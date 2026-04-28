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
     * Interfaz funcional equivalente al Observable<IMembership[]> de Angular.
     */
    public interface MembershipListCallback {
        void onResult(List<Membership> membresias);
    }
}