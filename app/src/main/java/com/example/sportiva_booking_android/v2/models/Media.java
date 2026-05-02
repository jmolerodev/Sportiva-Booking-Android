package com.example.sportiva_booking_android.v2.models;

import com.google.firebase.database.IgnoreExtraProperties;


@IgnoreExtraProperties
public class Media extends DomainEntity {


    private String url;
    private String nombre;
    private String descripcion;
    private long fecha_subida;
    private String profesionalId;
    private String centroId;

    /*Constructor de la Clase*/
    public Media() {
    }


    /*Getters y Setters de la Clase*/
    public String getUrl() {
        return url;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public long getFecha_subida() {
        return fecha_subida;
    }

    public String getProfesionalId() {
        return profesionalId;
    }

    public String getCentroId() {
        return centroId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setFecha_subida(long fecha_subida) {
        this.fecha_subida = fecha_subida;
    }

    public void setProfesionalId(String profesionalId) {
        this.profesionalId = profesionalId;
    }

    public void setCentroId(String centroId) {
        this.centroId = centroId;
    }
}