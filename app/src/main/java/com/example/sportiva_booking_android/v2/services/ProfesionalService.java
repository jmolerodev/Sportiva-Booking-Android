package com.example.sportiva_booking_android.v2.services;

import android.content.Context;

import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Profesional;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfesionalService {

    /* Atributos del Servicio */
    DatabaseReference databaseReference;
    DatabaseReference rootReference;

    /* Constructor del Servicio */
    public ProfesionalService(Context context) {
        /* Nos conectamos a la Base de Datos, accediendo al nodo 'Persons' respectivamente */
        databaseReference = FirebaseDatabase.getInstance().getReference("Persons");
        /* Referencia a la raíz necesaria para escrituras atómicas multi-ruta */
        rootReference     = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Interfaz de callback para operaciones que devuelven una lista de Profesionales.
     */
    public interface ProfesionalListCallback {
        void onSuccess(List<Profesional> profesionales);
        void onError(String errorMessage);
    }

    /**
     * Interfaz de callback para operaciones simples de éxito o error.
     */
    public interface OperationCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    /**
     * Metodo mediante el cual insertaremos a un nuevo profesional dentro de nuestra Base de Datos
     * @param profesional - Profesional que se desea insertar dentro de nuestra Base de Datos
     * @return - ID del Profesional insertado en la Base de Datos
     */
    public String insertProfesional(Profesional profesional) {
        /* Usaremos como ID el UID que se nos genera mediante Firebase Auth */
        String profesionalID = profesional.getId();
        /* Obtenemos la referencia para insertar al profesional en el nodo de 'Persons' */
        DatabaseReference profesionalReference = databaseReference.child(profesionalID);
        /* Finalmente, vamos a guardar al profesional dentro de nuestra Base de Datos y retornamos su ID */
        profesionalReference.setValue(profesional);
        return profesionalID;
    }

    /**
     * Método mediante el cual actualizaremos a un profesional ya existente dentro de nuestra Base de Datos
     * @param profesional - Profesional que deseamos actualizar
     */
    public void updateProfesional(Profesional profesional) {
        /* Accedemos al nodo específico del profesional mediante su identificador y le asignaremos nuevos valores */
        databaseReference.child(profesional.getId()).setValue(profesional);
    }

    /**
     * Método mediante el que eliminaremos a un Profesional de nuestra Base de Datos
     * @param profesional - Profesional que deseamos eliminar
     */
    public void deleteProfesional(Profesional profesional) {
        /* Volvemos a acceder al nodo del profesional mediante su identificador y lo eliminamos */
        databaseReference.child(profesional.getId()).removeValue();
    }

    /**
     * Método mediante el cual obtendremos un Profesional de nuestra Base de Datos dado su identificador
     * @param profesionalId - ID del Profesional que deseamos obtener
     * @param listener      - Listener que gestionará la respuesta asíncrona de Firebase
     */
    public void getProfesionalById(String profesionalId, ValueEventListener listener) {
        databaseReference.child(profesionalId).addListenerForSingleValueEvent(listener);
    }

    /**
     * Obtiene la lista de profesionales vinculados a un administrador concreto.
     * Filtra en el nodo 'Persons' aquellos nodos cuyo rol sea PROFESIONAL
     * y cuyo adminId coincida con el UID del administrador propietario.
     *
     * @param adminUid - UID del administrador propietario de los profesionales
     * @param callback - Callback que recibirá la lista de profesionales o el error
     */
    public void getProfesionalesByAdmin(String adminUid, ProfesionalListCallback callback) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Profesional> profesionales = new ArrayList<>();

                for (DataSnapshot personSnapshot : snapshot.getChildren()) {
                    String rolString   = personSnapshot.child("rol").getValue(String.class);
                    String adminIdNode = personSnapshot.child("adminId").getValue(String.class);

                    /* filtramos solo los PROFESIONAL que pertenecen a este administrador */
                    if (rolString != null && rolString.equals(Rol.PROFESIONAL.name())
                            && adminUid.equals(adminIdNode)) {

                        Profesional profesional = personSnapshot.getValue(Profesional.class);
                        if (profesional != null) {
                            /* asignamos el UID manualmente ya que Firebase no lo incluye en el objeto */
                            profesional.setId(personSnapshot.getKey());
                            profesionales.add(profesional);
                        }
                    }
                }
                callback.onSuccess(profesionales);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Elimina un profesional junto con todas sus sesiones y reservas asociadas
     * mediante una escritura atómica multi-ruta sobre la raíz de la base de datos.
     * El borrado se orquesta en cascada: primero se leen las sesiones del profesional,
     * se identifican sus reservas, y se construye el mapa de nulos que Firebase
     * aplica de forma atómica para evitar datos huérfanos.
     *
     * @param uid      - UID del profesional a eliminar
     * @param callback - Callback que notifica el resultado de la operación
     */
    public void deleteProfesionalCompleto(String uid, OperationCallback callback) {

        /* leemos las sesiones del profesional para recopilar los ids */
        rootReference.child("Sessions")
                .orderByChild("profesionalId").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot sesionesSnapshot) {

                        /* mapa de escrituras atómicas: null = borrar el nodo */
                        Map<String, Object> updates = new HashMap<>();

                        /* marcamos todas las sesiones del profesional para borrar */
                        for (DataSnapshot sesion : sesionesSnapshot.getChildren()) {
                            updates.put("Sessions/" + sesion.getKey(), null);
                        }

                        /* leemos todos los bookings para filtrar los que pertenecen a esas sesiones */
                        rootReference.child("Bookings")
                                .addListenerForSingleValueEvent(new ValueEventListener() {

                                    @Override
                                    public void onDataChange(DataSnapshot bookingsSnapshot) {

                                        /* recogemos los ids de sesiones del profesional para filtrar */
                                        List<String> sesionIds = new ArrayList<>();
                                        for (DataSnapshot sesion : sesionesSnapshot.getChildren()) {
                                            sesionIds.add(sesion.getKey());
                                        }

                                        /* marcamos para borrar las reservas cuya sesión pertenece al profesional */
                                        for (DataSnapshot booking : bookingsSnapshot.getChildren()) {
                                            String bookingSesionId = booking.child("sesionId")
                                                    .getValue(String.class);
                                            if (bookingSesionId != null
                                                    && sesionIds.contains(bookingSesionId)) {
                                                updates.put("Bookings/" + booking.getKey(), null);
                                            }
                                        }

                                        /* finalmente marcamos el nodo del profesional para borrar */
                                        updates.put("Persons/" + uid, null);

                                        /* aplicamos todas las escrituras de forma atómica */
                                        rootReference.updateChildren(updates)
                                                .addOnSuccessListener(unused -> callback.onSuccess())
                                                .addOnFailureListener(e -> callback.onError(e.getMessage()));
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        callback.onError(error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }
}