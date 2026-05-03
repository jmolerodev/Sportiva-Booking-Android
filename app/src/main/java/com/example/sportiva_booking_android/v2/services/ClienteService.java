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

import java.util.ArrayList;
import java.util.List;

public class ClienteService {

    private static final String COLLECTION_NAME = "Persons";

    /*Atributos del Servicio*/
    DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public ClienteService(Context context) {
        databaseReference = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
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
     * Método mediante el que eliminaremos a un Cliente de nuestra Base de Datos
     *
     * @param cliente - Cliente que deseamos eliminar
     */
    public void deleteCliente(Cliente cliente) {
        databaseReference.child(cliente.getId()).removeValue();
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