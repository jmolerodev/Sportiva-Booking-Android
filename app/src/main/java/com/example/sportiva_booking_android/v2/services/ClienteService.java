package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.models.Cliente;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ClienteService {

    private static final String COLLECTION_NAME = "Persons";
    /*Atributos del Servicio*/
    DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public ClienteService (Context context){
        /*Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente*/
        databaseReference = FirebaseDatabase.getInstance().getReference(COLLECTION_NAME);
    }

    /**
     * Metodo mediante el cual insertaremos a un nuevo cliente dentro de nuestra Base de Datos
     * @param cliente - Cliente que se desea insertar dentro de nuestra Base de Datos
     * @return - ID del Cliente insertado en la Base de Datos
     */
    public String insertCliente (Cliente cliente){
        /*Usaremos como ID el UID que se nos genera mediante Firebase Auth*/
        String clienteID = cliente.getId();
        /*Obtenemos la referencia para insertar al cliente en el nodo de 'Persons'*/
        DatabaseReference clienteReference = databaseReference.child(clienteID);
        /*Finalmente, vamos a guardar al Cliente dentro de nuestra Base de Datos y retornamos su ID*/
        clienteReference.setValue(cliente);
        return clienteID;
    }

    /**
     * Método mediante el cual actualizaremos a un cliente ya existente dentro de nuestra Base de Datos
     * @param cliente - Cliente que deseamos actualizar
     */
    public void updateCliente (Cliente cliente){
        /*Accedemos al nodo especifico del cliente mediante su identificador y le asignaremos nuevos valores*/
        databaseReference.child(cliente.getId()).setValue(cliente);
    }

    /**
     * Método mediante el que eliminaremos a un Cliente de nuestra Base de Datos
     * @param cliente - Cliente que deseamos eliminar
     */
    public void deleteCliente (Cliente cliente){
        /*Volvemos a acceder al nodo del cliente mediante su identificador y lo eliminamos*/
        databaseReference.child(cliente.getId()).removeValue();
    }

    /**
     * Método mediante el cual obtendremos un Cliente de nuestra Base de Datos dado su identificador
     * @param clienteId - ID del Cliente que deseamos obtener
     * @param listener - Listener que gestionará la respuesta asíncrona de Firebase
     */
    public void getClienteById(String clienteId, ValueEventListener listener) {
        databaseReference.child(clienteId).addListenerForSingleValueEvent(listener);
    }

    /**
     * Método para comprobar si un DNI ya está registrado en la base de datos.
     * Replica el comportamiento del isDniAlreadyRegistered de Angular: consulta
     * el nodo de clientes filtrando por el campo 'dni' y devuelve el resultado
     * mediante un callback booleano (true si ya existe, false si está disponible).
     *
     * @param dni      DNI a verificar
     * @param callback Interfaz funcional que recibe el resultado de la consulta
     */
    public void isDniAlreadyRegistered(String dni, DniCheckCallback callback) {

        DatabaseReference clientesRef = FirebaseDatabase.getInstance()
                .getReference(COLLECTION_NAME);

    /*Consultamos ordenando por el campo 'dni' e igualando al valor buscado,
     replicando el orderByChild('dni') + equalTo(dni) de Angular*/
        clientesRef.orderByChild("dni")
                .equalTo(dni)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        /*Si el snapshot tiene hijos, el DNI ya está registrado*/
                        callback.onResult(snapshot.exists() && snapshot.hasChildren());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    /*En caso de error devolvemos false para no bloquear el flujo,
                     el error se gestionará en la Activity mediante el Snackbar*/
                        callback.onResult(false);
                    }
                });
    }

    /**
     * Interfaz funcional que actúa como equivalente al Observable<boolean> de Angular,
     * adaptado al modelo de callbacks de Android
     */
    public interface DniCheckCallback {
        void onResult(boolean dniExiste);
    }
}