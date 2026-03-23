package com.example.sportiva_booking_android.v2.models;

import com.example.sportiva_booking_android.v2.enums.Especialidad;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Profesional extends Person {

    /*Atributos de la Clase*/
    private String descripcion;
    private Integer annos_experiencia;
    private Especialidad especialidad;

    /*Constructor de la Clase*/
    public Profesional (){

    }

    /*Getters y Setters de la Clase*/
    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getAnnos_experiencia() {
        return annos_experiencia;
    }

    public void setAnnos_experiencia(Integer annos_experiencia) {
        this.annos_experiencia = annos_experiencia;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(Especialidad especialidad) {
        this.especialidad = especialidad;
    }
}
