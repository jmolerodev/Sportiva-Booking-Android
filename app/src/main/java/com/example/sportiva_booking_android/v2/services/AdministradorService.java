package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Administrador;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdministradorService {

    /*Atributos del Servicio*/
    DatabaseReference databaseReference;
    DatabaseReference rootReference;

    /*Constructor del Servicio*/
    public AdministradorService (Context context){
        /*Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente*/
        databaseReference = FirebaseDatabase.getInstance().getReference("Persons");
        /*Referencia a la raíz necesaria para escrituras atómicas multi-ruta*/
        rootReference     = FirebaseDatabase.getInstance().getReference();
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

    /**
     * Interfaz de callback para operaciones que devuelven una lista de Administradores
     */
    public interface AdminListCallback {
        void onSuccess(List<Administrador> administradores);
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
     * Método mediante el cual obtendremos todos los usuarios del nodo 'Persons'
     * filtrando únicamente aquellos con rol ADMINISTRADOR
     * @param callback - Callback que recibirá la lista de administradores o el error
     */
    public void getAllAdministradores(AdminListCallback callback) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Administrador> administradores = new ArrayList<>();

                for (DataSnapshot personSnapshot : snapshot.getChildren()) {
                    String rolString = personSnapshot.child("rol").getValue(String.class);

                    if (rolString != null && rolString.equals(Rol.ADMINISTRADOR.name())) {
                        Administrador admin = personSnapshot.getValue(Administrador.class);

                        if (admin != null) {
                            /*Asignamos el UID manualmente ya que Firebase no lo incluye en el objeto*/
                            admin.setId(personSnapshot.getKey());
                            administradores.add(admin);
                        }
                    }
                }
                callback.onSuccess(administradores);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Método mediante el cual eliminaremos de forma atómica el nodo del Administrador
     * en 'Persons' y su Centro Deportivo asociado en 'Sports-Centre'
     * @param uid      - UID del Administrador a eliminar
     * @param callback - Callback que notifica el resultado de la operación
     */
    public void deleteAdministradorCompleto(String uid, OperationCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("Persons/" + uid, null);
        updates.put("Sports-Centre/" + uid, null);

        rootReference.updateChildren(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}