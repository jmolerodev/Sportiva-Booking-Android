package com.example.sportiva_booking_android.v2.models;

import com.example.sportiva_booking_android.v2.enums.EstadoReserva;
import com.google.firebase.database.IgnoreExtraProperties;


@IgnoreExtraProperties
public class Booking extends DomainEntity {

    /*Atributos de la Clase*/
    private String sesionId;
    private String clienteId;
    private String centroId;
    private long fecha;
    private EstadoReserva estado;

    /*Constructor de la Clase*/
    public Booking() {
    }

    /*Getters y Setters de la Clase*/
    public String getSesionId() {
        return sesionId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getCentroId() {
        return centroId;
    }

    public long getFecha() {
        return fecha;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setSesionId(String sesionId) {
        this.sesionId = sesionId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public void setCentroId(String centroId) {
        this.centroId = centroId;
    }

    public void setFecha(long fecha) {
        this.fecha = fecha;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }
}