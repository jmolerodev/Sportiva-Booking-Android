package com.example.sportiva_booking_android.v2.services;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.enums.EstadoSesion;
import com.example.sportiva_booking_android.v2.models.Session;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SessionService {

    private static final String COLLECTION_NAME = "Sessions";

    /* Referencia raíz al nodo Sessions de Firebase Realtime Database */
    private final DatabaseReference sessionsRef;

    public SessionService() {
        sessionsRef = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
    }

    public interface SessionsCallback {
        void onSuccess(List<Session> sesiones);

        void onError(String error);
    }

    public interface SessionCallback {
        void onSuccess(Session sesion);

        void onError(String error);
    }

    public interface VoidCallback {
        void onSuccess();

        void onError(String error);
    }


    /**
     * Obtiene todas las sesiones ACTIVAS de un centro en un rango de timestamps
     * correspondiente a un día completo (00:00:00 – 23:59:59).
     * El filtrado se hace en cliente porque RTDB no soporta consultas compuestas.
     *
     * @param centroId    UID del centro deportivo
     * @param fechaInicio Timestamp epoch del inicio del día en ms
     * @param fechaFin    Timestamp epoch del fin del día en ms
     * @param callback    Resultado con la lista filtrada o el error
     */
    public void getSessionsByCentroAndFecha(String centroId, long fechaInicio, long fechaFin,
                                            SessionsCallback callback) {
        sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Session> resultado = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Session s = child.getValue(Session.class);
                    if (s == null) continue;
                    s.setId(child.getKey());
                    if (centroId.equals(s.getCentroId())
                            && s.getFecha() >= fechaInicio
                            && s.getFecha() <= fechaFin
                            && EstadoSesion.ACTIVA.equals(s.getEstado())) {
                        resultado.add(s);
                    }
                }
                callback.onSuccess(resultado);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Obtiene todas las sesiones creadas por un profesional concreto sin filtro de estado.
     * Se usa como caché reactiva en el fragmento para construir el historial y
     * las reservas pendientes.
     *
     * @param profesionalUid UID del profesional propietario de las sesiones
     * @param callback       Resultado con el listado completo o el error
     */
    public void getSessionsByProfesional(String profesionalUid, SessionsCallback callback) {
        sessionsRef.orderByChild("profesionalId")
                .equalTo(profesionalUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Session> resultado = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Session s = child.getValue(Session.class);
                            if (s == null) continue;
                            s.setId(child.getKey());
                            resultado.add(s);
                        }
                        callback.onSuccess(resultado);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }


    /**
     * Persiste una nueva sesión en Firebase generando un ID automático con push().
     *
     * @param sesion   Objeto sesión completo listo para persistir
     * @param callback Resultado de la operación de escritura
     */
    public void createSession(Session sesion, VoidCallback callback) {
        String uid = sessionsRef.push().getKey();
        if (uid == null) {
            callback.onError("No se pudo generar el ID de la sesión");
            return;
        }
        sesion.setId(uid);
        sessionsRef.child(uid).setValue(sesion)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Actualización parcial de una sesión existente usando updateChildren()
     * para no sobreescribir campos que no se modifican.
     *
     * @param uid      Clave del nodo de la sesión en Firebase
     * @param data     Mapa de campos a actualizar
     * @param callback Resultado de la operación de escritura
     */
    public void updateSession(String uid, java.util.Map<String, Object> data, VoidCallback callback) {
        sessionsRef.child(uid).updateChildren(data)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cancelación lógica de una sesión: actualiza únicamente el campo estado a CANCELADA
     * sin borrar el nodo, de modo que caiga al historial del profesional.
     * Las reservas asociadas se cancelan en cascada desde el fragmento invocando
     * BookingService antes de llamar a este método.
     *
     * @param uid      Clave del nodo de la sesión en Firebase
     * @param callback Resultado de la operación de escritura
     */
    public void cancelSession(String uid, VoidCallback callback) {
        sessionsRef.child(uid).child("estado")
                .setValue(EstadoSesion.CANCELADA)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Eliminación física del nodo de sesión en Firebase.
     * El borrado en cascada de las reservas asociadas debe ejecutarse antes
     * desde el fragmento usando BookingService para no dejar nodos huérfanos.
     *
     * @param uid      Clave del nodo de la sesión en Firebase
     * @param callback Resultado de la operación de borrado
     */
    public void deleteSession(String uid, VoidCallback callback) {
        sessionsRef.child(uid).removeValue()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}