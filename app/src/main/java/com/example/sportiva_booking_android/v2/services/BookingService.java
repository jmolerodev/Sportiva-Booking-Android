package com.example.sportiva_booking_android.v2.services;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.enums.EstadoReserva;
import com.example.sportiva_booking_android.v2.models.Booking;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BookingService {

    /* Nombre del nodo principal de reservas en Firebase RTDB */
    private static final String COLLECTION = "Bookings";

    private final DatabaseReference dbRef;

    public BookingService() {
        this.dbRef = FirebaseDatabase.getInstance().getReference();
    }


    /**
     * Contrato de retorno para operaciones que devuelven una lista de reservas.
     */
    public interface BookingsCallback {
        void onBookingsObtenidas(List<Booking> bookings);

        void onError(String mensaje);
    }

    /**
     * Contrato de retorno para operaciones de escritura simples (sin datos de vuelta).
     */
    public interface WriteCallback {
        void onExito();

        void onError(String mensaje);
    }


    /**
     * Escucha en tiempo real todas las reservas de un cliente concreto,
     * independientemente de su estado o centro.
     * Devuelve el ValueEventListener activo para que el fragment pueda cancelarlo
     * en onDestroyView y evitar fugas de memoria.
     *
     * @param clienteId UID del cliente autenticado
     * @param callback  Retorno con la lista de reservas o el error producido
     * @return ValueEventListener activo — el llamador debe cancelarlo con removeEventListener
     */
    public ValueEventListener escucharReservasByCliente(String clienteId,
                                                        BookingsCallback callback) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Booking> lista = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Booking b = child.getValue(Booking.class);
                    if (b != null && clienteId.equals(b.getClienteId())) {
                        b.setId(child.getKey());
                        lista.add(b);
                    }
                }
                callback.onBookingsObtenidas(lista);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };

        dbRef.child(COLLECTION).addValueEventListener(listener);
        return listener;
    }

    /**
     * Cancela el listener de reservas registrado sobre el nodo raíz Bookings.
     * Se llama desde el fragment en onDestroyView para liberar el listener de Firebase.
     *
     * @param listener ValueEventListener activo devuelto por escucharReservasByCliente
     */
    public void cancelarListenerReservas(ValueEventListener listener) {
        if (listener == null) return;
        dbRef.child(COLLECTION).removeEventListener(listener);
    }


    /**
     * Persiste una nueva reserva en Firebase e incrementa de forma atómica
     * el aforoActual de la sesión vinculada.
     * Antes de invocar este método deben haberse verificado externamente:
     * — Membresía activa del cliente con el centro
     * — Ausencia de reserva previa CONFIRMADA del cliente en la misma sesión
     * — Sesión sin aforo completo (aforoActual menor que aforoMax)
     *
     * @param reserva    Objeto Booking completo listo para persistir
     * @param aforoNuevo Valor incrementado del aforoActual de la sesión
     * @param callback   Retorno de éxito o error
     */
    public void crearReserva(Booking reserva, int aforoNuevo, WriteCallback callback) {
        DatabaseReference newRef = dbRef.child(COLLECTION).push();
        String bookingId = newRef.getKey();

        if (bookingId == null) {
            callback.onError("No se pudo generar el ID de la reserva");
            return;
        }

        /* Escritura atómica: nueva reserva + aforoActual de la sesión incrementado */
        Map<String, Object> updates = new HashMap<>();
        updates.put("/" + COLLECTION + "/" + bookingId, reservaToMap(reserva));
        updates.put("/Sessions/" + reserva.getSesionId() + "/aforoActual", aforoNuevo);

        dbRef.updateChildren(updates)
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cancela una reserva confirmada marcándola como CANCELADA y decrementa
     * de forma atómica el aforoActual de la sesión para liberar la plaza.
     *
     * @param bookingId  UID de la reserva a cancelar
     * @param sesionId   UID de la sesión vinculada para actualizar el aforo
     * @param aforoNuevo Valor decrementado del aforoActual de la sesión
     * @param callback   Retorno de éxito o error
     */
    public void cancelarReserva(String bookingId, String sesionId,
                                int aforoNuevo, WriteCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("/" + COLLECTION + "/" + bookingId + "/estado",
                EstadoReserva.CANCELADA.name());
        updates.put("/Sessions/" + sesionId + "/aforoActual", aforoNuevo);

        dbRef.updateChildren(updates)
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cancela en cascada todas las reservas CONFIRMADAS de una sesión concreta
     * marcándolas como CANCELADA de forma atómica en una única escritura batch.
     * Se invoca antes de cancelar o eliminar una sesión para que el estado
     * de las reservas quede consistente en Firebase.
     * No toca aforoActual porque la sesión entera queda cancelada.
     *
     * @param sesionId UID de la sesión cuyas reservas confirmadas se van a cancelar
     * @param callback Retorno de éxito o error
     */
    public void cancelarReservasBySesion(String sesionId, WriteCallback callback) {
        dbRef.child(COLLECTION).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Object> updates = new HashMap<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Booking b = child.getValue(Booking.class);
                    if (b != null
                            && sesionId.equals(b.getSesionId())
                            && EstadoReserva.CONFIRMADA.equals(b.getEstado())) {
                        updates.put("/" + COLLECTION + "/" + child.getKey() + "/estado",
                                EstadoReserva.CANCELADA.name());
                    }
                }

                if (updates.isEmpty()) {
                    callback.onExito();
                    return;
                }

                dbRef.updateChildren(updates)
                        .addOnSuccessListener(v -> callback.onExito())
                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Elimina permanentemente el nodo de una reserva cancelada de Firebase.
     * Solo debe invocarse sobre reservas en estado CANCELADA para evitar
     * pérdidas accidentales de reservas activas o pendientes.
     *
     * @param bookingId UID de la reserva a eliminar
     * @param callback  Retorno de éxito o error
     */
    public void eliminarReserva(String bookingId, WriteCallback callback) {
        dbRef.child(COLLECTION).child(bookingId).removeValue()
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }


    /**
     * Convierte un objeto Booking a Map para usarlo en escrituras atómicas
     * con updateChildren(), evitando serializar el campo id que no vive en Firebase.
     *
     * @param b Objeto Booking a convertir
     * @return Mapa con los campos persistibles de la reserva
     */
    private Map<String, Object> reservaToMap(Booking b) {
        Map<String, Object> map = new HashMap<>();
        map.put("sesionId", b.getSesionId());
        map.put("clienteId", b.getClienteId());
        map.put("centroId", b.getCentroId());
        map.put("fecha", b.getFecha());
        map.put("estado", b.getEstado() != null ? b.getEstado().name() : EstadoReserva.CONFIRMADA.name());
        return map;
    }
}