package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import com.example.sportiva_booking_android.v2.models.Administrador;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdministradorService {

    /*Atributos del Servicio*/
    DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public AdministradorService (Context context){
        /*Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente*/
        databaseReference = FirebaseDatabase.getInstance().getReference("Persons");
    }

    /**
     * Metodo mediante el cual insertaremos a un nuevo administrador dentro de nuestra Base de Datos
     * @param administrador - Administrador que se desea insertar dentro de nuestra Base de Datos
     * @return - ID del Administrador insertado en la Base de Datos
     */
    public String insertAdministrador (Administrador administrador){
        /*Usaremos como ID el UID que se nos genera mediante Firebase Auth*/
        String adminID = administrador.getId();
        /*Obtenemos la referencia para insertar al cliente en el nodo de 'Persons'*/
        DatabaseReference administradorReference = databaseReference.child(adminID);
        /*Finalmente, vamos a guardar al cliente dentro de nuestra Base de Datos y retornamos su ID*/
        administradorReference.setValue(administrador);
        return adminID;
    }

    /**
     * Método mediante el cual actualizaremos a un administrador ya existente dentro de nuestra Base de Datos
     * @param administrador - Administrador que deseamos actualizar
     */
    public void updateAdministrador (Administrador administrador){
        /*Accedemos al nodo especifico del administrador mediante su identificador y le asignaremos nuevos valores*/
        databaseReference.child(administrador.getId()).setValue(administrador);
    }

    /**
     * Método mediante el que eliminaremos a un Administrador de nuestra Base de Datos
     * @param administrador - Administrador que deseamos eliminar
     */
    public void deleteAdministrador (Administrador administrador){
        /*Volvemos a acceder al nodo del administrador mediante su identificador y lo eliminamos*/
        databaseReference.child(administrador.getId()).removeValue();
    }

    /**
     * Método mediante el cual obtendremos un Administrador de nuestra Base de Datos dado su identificador
     * @param administradorId - ID del Administrador que deseamos obtener
     * @param listener - Listener que gestionará la respuesta asíncrona de Firebase
     */
    public void getAdministradorById(String administradorId, ValueEventListener listener) {
        databaseReference.child(administradorId).addListenerForSingleValueEvent(listener);
    }
}