package com.example.sportiva_booking_android.v2.services;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.models.SportCentre;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SportCentreService {

    private static final String COLLECTION_NAME = "Sports-Centre";
    private static final String PERSONS_NODE = "Persons";

    /*Referencia al nodo Sports-Centre de Firebase*/
    private final DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public SportCentreService() {
        databaseReference = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
    }

    /**
     * Obtiene todos los centros deportivos disponibles en la plataforma.
     * Equivale al getAllSportCentres() de Angular.
     *
     * @param listener Listener que gestionará la respuesta asíncrona de Firebase
     */
    public void getAllSportCentres(ValueEventListener listener) {
        databaseReference.addListenerForSingleValueEvent(listener);
    }

    /**
     * Obtiene el centro deportivo cuyo adminUid coincide con el UID del administrador autenticado.
     * Itera los nodos del snapshot buscando la coincidencia, replicando el find() de Angular.
     *
     * @param adminUid UID del administrador autenticado
     * @param callback Callback que devuelve el centro encontrado o null si no existe
     */
    public void getSportCentreByAdminUid(String adminUid, SportCentreCallback callback) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    SportCentre centro = child.getValue(SportCentre.class);
                    /*Buscamos el centro cuyo adminUid coincida con el del usuario autenticado*/
                    if (centro != null && adminUid.equals(centro.getAdminUid())) {
                        centro.setId(child.getKey());
                        callback.onResult(centro);
                        return;
                    }
                }
                /*No se encontró ningún centro para este administrador*/
                callback.onResult(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(null);
            }
        });
    }

    /**
     * Obtiene el centro deportivo donde trabaja un profesional.
     * Primero lee su centroId desde Persons y luego busca el centro correspondiente.
     * Replica el getSportCentreByProfessionalUid() de Angular con su switchMap.
     *
     * @param proUid   UID del profesional autenticado
     * @param callback Callback que devuelve el centro encontrado o null si no tiene asignado
     */
    public void getSportCentreByProfessionalUid(String proUid, SportCentreCallback callback) {
        FirebaseDatabase.getInstance()
                .getReference(PERSONS_NODE)
                .child(proUid)
                .child("centroId")
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String centroId = snapshot.getValue(String.class);

                        /*Si el profesional no tiene centroId asignado, devolvemos null*/
                        if (centroId == null) {
                            callback.onResult(null);
                            return;
                        }

                        /*Si lo tiene, buscamos el centro por su ID*/
                        getSportCentreById(centroId, callback);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onResult(null);
                    }
                });
    }

    /**
     * Obtiene un centro deportivo directamente por su ID de nodo en Firebase.
     *
     * @param centroId ID del nodo en Sports-Centre
     * @param callback Callback que devuelve el centro o null si no existe
     */
    public void getSportCentreById(String centroId, SportCentreCallback callback) {
        databaseReference.child(centroId).addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SportCentre centro = snapshot.getValue(SportCentre.class);
                if (centro != null) centro.setId(snapshot.getKey());
                callback.onResult(centro);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(null);
            }
        });
    }

    /**
     * Interfaz funcional equivalente al Observable<SportCentre | null> de Angular.
     */
    public interface SportCentreCallback {
        void onResult(SportCentre centro);
    }

    /**
     * Interfaz funcional equivalente al Observable<SportCentre[]> de Angular.
     */
    public interface SportCentreListCallback {
        void onResult(java.util.List<SportCentre> centros);
    }
}