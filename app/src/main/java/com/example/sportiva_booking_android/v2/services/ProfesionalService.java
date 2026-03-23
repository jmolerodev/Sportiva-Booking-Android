package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import com.example.sportiva_booking_android.v2.models.Profesional;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfesionalService {

    /*Atributos del Servicio*/
    DatabaseReference databaseReference;

    /*Constructor del Servicio*/
    public ProfesionalService (Context context){
        /*Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente*/
        databaseReference = FirebaseDatabase.getInstance().getReference("Persons");
    }

    /**
     * Metodo mediante el cual insertaremos a un nuevo profesional dentro de nuestra Base de Datos
     * @param profesional - Profesional que se desea insertar dentro de nuestra Base de Datos
     * @return - ID del Profesional insertado en la Base de Datos
     */
    public String insertProfesional (Profesional profesional){
        /*Usaremos como ID el UID que se nos genera mediante Firebase Auth*/
        String profesionalID = profesional.getId();
        /*Obtenemos la referencia para insertar al profesional en el nodo de 'Persons'*/
        DatabaseReference profesionalReference = databaseReference.child(profesionalID);
        /*Finalmente, vamos a guardar al profesional dentro de nuestra Base de Datos y retornamos su ID*/
        profesionalReference.setValue(profesional);
        return profesionalID;
    }

    /**
     * Método mediante el cual actualizaremos a un profesional ya existente dentro de nuestra Base de Datos
     * @param profesional - Profesional que deseamos actualizar
     */
    public void updateProfesional (Profesional profesional){
        /*Accedemos al nodo específico del profesional mediante su identificador y le asignaremos nuevos valores*/
        databaseReference.child(profesional.getId()).setValue(profesional);
    }

    /**
     * Método mediante el que eliminaremos a un Profesional de nuestra Base de Datos
     * @param profesional - Profesional que deseamos eliminar
     */
    public void deleteProfesional (Profesional profesional){
        /*Volvemos a acceder al nodo del profesional mediante su identificador y lo eliminamos*/
        databaseReference.child(profesional.getId()).removeValue();
    }

    /**
     * Método mediante el cual obtendremos un Profesional de nuestra Base de Datos dado su identificador
     * @param profesionalId - ID del Profesional que deseamos obtener
     * @param listener - Listener que gestionará la respuesta asíncrona de Firebase
     */
    public void getProfesionalById(String profesionalId, ValueEventListener listener) {
        databaseReference.child(profesionalId).addListenerForSingleValueEvent(listener);
    }
}