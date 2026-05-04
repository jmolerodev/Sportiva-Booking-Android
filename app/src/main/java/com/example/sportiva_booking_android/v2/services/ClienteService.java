package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.models.Cliente;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClienteService {

    private static final String COLLECTION_NAME = "Persons";

    /*Atributos del Servicio*/
    DatabaseReference databaseReference;
    DatabaseReference rootReference;

    /*Instancia de Firebase Functions para invocar Cloud Functions*/
    FirebaseFunctions firebaseFunctions;

    /*Constructor del Servicio*/
    public ClienteService(Context context) {
        /*Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente*/
        databaseReference = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
        /*Referencia a la raíz necesaria para escrituras atómicas multi-ruta*/
        rootReference     = FirebaseDatabase.getInstance().getReference();
        /*Inicializamos Firebase Functions*/
        firebaseFunctions = FirebaseFunctions.getInstance();
    }

    /**
     * Metodo mediante el cual insertaremos a un nuevo cliente dentro de nuestra Base de Datos
     *
     * @param cliente - Cliente que se desea insertar dentro de nuestra Base de Datos
     * @return - ID del Cliente insertado en la Base de Datos
     */
    public String insertCliente(Cliente cliente) {
        String clienteID = cliente.getId();
        DatabaseReference clienteReference = databaseReference.child(clienteID);
        clienteReference.setValue(cliente);
        return clienteID;
    }

    /**
     * Método mediante el cual actualizaremos a un cliente ya existente dentro de nuestra Base de Datos
     *
     * @param cliente - Cliente que deseamos actualizar
     */
    public void updateCliente(Cliente cliente) {
        databaseReference.child(cliente.getId()).setValue(cliente);
    }

    /**
     * Método mediante el que eliminaremos de forma completamente recursiva a un Cliente:
     *   1. Elimina de forma atómica el nodo del cliente en 'Persons'.
     *   2. Invoca la Cloud Function 'deleteUserFromAuth' para eliminar al usuario de Firebase Authentication.
     * @param uid      - UID del Cliente a eliminar
     * @param callback - Callback que notifica el resultado de la operación
     */
    public void deleteCliente(String uid, OperationCallback callback) {

        Map<String, Object> updates = new HashMap<>();
        updates.put("Persons/" + uid, null);

        /*Paso 1: eliminamos de forma atómica el nodo en Realtime Database*/
        rootReference.updateChildren(updates)
                .addOnSuccessListener(unused -> {

                    /*Paso 2: eliminamos al cliente de Firebase Authentication mediante Cloud Function*/
                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", uid);

                    firebaseFunctions
                            .getHttpsCallable("deleteUserFromAuth")
                            .call(data)
                            .addOnSuccessListener(result -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));

                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Método mediante el cual obtendremos un Cliente de nuestra Base de Datos dado su identificador
     *
     * @param clienteId - ID del Cliente que deseamos obtener
     * @param listener  - Listener que gestionará la respuesta asíncrona de Firebase
     */
    public void getClienteById(String clienteId, ValueEventListener listener) {
        databaseReference.child(clienteId).addListenerForSingleValueEvent(listener);
    }

    /**
     * Obtiene todos los usuarios con rol CLIENTE registrados en la plataforma.
     * Se usa en ClientListFragment para cruzar con las membresías activas del centro
     * y obtener los datos completos de cada cliente vinculado.
     *
     * @param callback Callback que devuelve la lista de clientes con rol CLIENTE
     */
    public void getAllClientesConRol(ClienteListCallback callback) {
        Query query = databaseReference.orderByChild("rol").equalTo("CLIENTE");

        query.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Cliente> result = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Cliente c = child.getValue(Cliente.class);
                    if (c != null) {
                        c.setId(child.getKey());
                        result.add(c);
                    }
                }
                callback.onSuccess(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Interfaz de retorno para operaciones que devuelven una lista de clientes.
     */
    public interface ClienteListCallback {
        void onSuccess(List<Cliente> clientes);
        void onError(String errorMessage);
    }

    /**
     * Interfaz de callback para operaciones simples de éxito o error
     */
    public interface OperationCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    /**
     * Método para comprobar si un DNI ya está registrado en la base de datos.
     * Consulta el nodo de clientes filtrando por el campo 'dni' y devuelve el resultado
     * mediante un callback booleano (true si ya existe, false si está disponible).
     *
     * @param dni      DNI a verificar
     * @param callback Interfaz funcional que recibe el resultado de la consulta
     */
    public void isDniAlreadyRegistered(String dni, DniCheckCallback callback) {
        databaseReference.orderByChild("dni")
                .equalTo(dni)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        callback.onResult(snapshot.exists() && snapshot.hasChildren());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onResult(false);
                    }
                });
    }

    public interface DniCheckCallback {
        void onResult(boolean dniExiste);
    }
}