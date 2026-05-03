package com.example.sportiva_booking_android.v2.models;

import com.google.firebase.database.IgnoreExtraProperties;


@IgnoreExtraProperties
public class Mensaje extends DomainEntity {

    /*Atributos de la Clase*/
    private String emisorId;
    private String texto;
    private long fecha;

    /*Constructor de la Clase*/
    public Mensaje() {
    }

    /*Getters y Setters de la Clase*/
    public String getEmisorId() {
        return emisorId;
    }

    public String getTexto() {
        return texto;
    }

    public long getFecha() {
        return fecha;
    }

    public void setEmisorId(String emisorId) {
        this.emisorId = emisorId;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public void setFecha(long fecha) {
        this.fecha = fecha;
    }
}