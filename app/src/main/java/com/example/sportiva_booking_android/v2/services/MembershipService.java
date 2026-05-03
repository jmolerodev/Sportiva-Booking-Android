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
    private final DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public MembershipService() {
        databaseReference = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
    }

    /**
     * Obtiene todas las membresías de un cliente concreto.
     * Replica el getMembresiasByCliente() de Angular filtrando por clienteId.
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
     * Interfaz con la que manejaremos una lista de membersias.
     */
    public interface MembershipListCallback {
        void onResult(List<Membership> membresias);
    }

    /**
     * Contrato de retorno para operaciones que devuelven una única membresía o null.
     */
    public interface MembershipCallback {
        void onResult(Membership membresia);
    }

    /**
     * Verifica si un cliente tiene una membresía activa y vigente en un centro concreto.
     * Comprueba tanto el estado ACTIVA como que la fecha de fin no haya expirado.
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
                /* No se encontró membresía activa y vigente para este cliente y centro */
                callback.onResult(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(null);
            }
        });
    }
}