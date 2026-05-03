package com.example.sportiva_booking_android.v2.services;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.models.Membership;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MembershipService {

    private static final String COLLECTION_NAME = "Memberships";

    /*Referencia al nodo Memberships de Firebase*/
    public DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public MembershipService() {
        databaseReference = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
    }

    /**
     * Obtiene todas las membresías de un cliente concreto filtrando por su UID
     *
     * @param clienteId UID del cliente autenticado
     * @param callback  Callback que devuelve la lista de membresías del cliente
     */
    public void getMembresiasByCliente(String clienteId, MembershipListCallback callback) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Membership> result = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Membership m = child.getValue(Membership.class);
                    /*Filtramos únicamente las membresías del cliente autenticado*/
                    if (m != null && clienteId.equals(m.getClienteId())) {
                        m.setId(child.getKey());
                        result.add(m);
                    }
                }
                callback.onResult(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(new ArrayList<>());
            }
        });
    }

    /**
     * Obtiene todas las membresías activas y vigentes de un centro deportivo concreto.
     * Filtramos por centroId, estado ACTIVA y que la fecha de fin no haya expirado
     *
     * @param centroId UID del centro deportivo
     * @param callback Callback que devuelve la lista de membresías activas del centro
     */
    public void getMembresiasByCentro(String centroId, MembershipListCallback callback) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Membership> result = new ArrayList<>();
                long ahora = System.currentTimeMillis();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Membership m = child.getValue(Membership.class);
                    /*Nos quedamos solo con las que pertenecen al centro, están activas y no han expirado*/
                    if (m != null
                            && centroId.equals(m.getCentroId())
                            && "ACTIVA".equals(m.getEstado())
                            && m.getFechaFin() > ahora) {
                        m.setId(child.getKey());
                        result.add(m);
                    }
                }
                callback.onResult(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(new ArrayList<>());
            }
        });
    }

    /**
     * Verifica si un cliente tiene una membresía activa y vigente en un centro concreto.
     * Comprueba tanto el estado ACTIVA como que la fecha de fin no haya expirado
     *
     * @param clienteId UID del cliente autenticado
     * @param centroId  UID del centro deportivo a verificar
     * @param callback  Retorno con la membresía activa o null si no la tiene
     */
    public void getMembresiaActivaByClienteYCentro(String clienteId,
                                                   String centroId,
                                                   MembershipCallback callback) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long ahora = System.currentTimeMillis();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Membership m = child.getValue(Membership.class);
                    if (m != null
                            && clienteId.equals(m.getClienteId())
                            && centroId.equals(m.getCentroId())
                            && "ACTIVA".equals(m.getEstado())
                            && m.getFechaFin() > ahora) {
                        m.setId(child.getKey());
                        callback.onResult(m);
                        return;
                    }
                }
                /*No se encontró membresía activa y vigente para este cliente y centro*/
                callback.onResult(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(null);
            }
        });
    }

    /**
     * Persiste una nueva membresía en Firebase generando un ID automático con push().
     * Se invoca desde MembershipPaymentFragment tras la confirmación del pago por PayPal
     *
     * @param membresia Objeto Membership completo listo para persistir
     * @param callback  Retorno de éxito o error
     */
    public void saveMembresia(Membership membresia, WriteCallback callback) {
        databaseReference.push().setValue(membresia)
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Interfaz con la que manejaremos una lista de membresías
     */
    public interface MembershipListCallback {
        void onResult(List<Membership> membresias);
    }

    /**
     * Contrato de retorno para operaciones que devuelven una única membresía o null
     */
    public interface MembershipCallback {
        void onResult(Membership membresia);
    }

    /**
     * Contrato de retorno para operaciones de escritura simples
     */
    public interface WriteCallback {
        void onExito();
        void onError(String mensaje);
    }
}