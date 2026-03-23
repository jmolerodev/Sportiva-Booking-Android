package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import com.example.sportiva_booking_android.v2.models.Cliente;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ClienteService {

    /*Atributos del Servicio*/
    DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public ClienteService (Context context){
        /*Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente*/
        databaseReference = FirebaseDatabase.getInstance().getReference("Persons");
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
}