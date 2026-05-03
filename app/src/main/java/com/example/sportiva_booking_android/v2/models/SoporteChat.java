package com.example.sportiva_booking_android.v2.models;

import com.example.sportiva_booking_android.v2.enums.EstadoChat;
import com.google.firebase.database.IgnoreExtraProperties;


@IgnoreExtraProperties
public class SoporteChat extends DomainEntity {

    /*Atributos de la Clase*/
    private String centroId;
    private String clienteId;
    private String adminId;
    private EstadoChat estado;
    private long fechaCreacion;
    private long fechaUltimoMensaje;

    /* Constructor de la Clase*/
    public SoporteChat() {
    }

    /*Getters y Setters de la Clase*/
    public String getCentroId() {
        return centroId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getAdminId() {
        return adminId;
    }

    public EstadoChat getEstado() {
        return estado;
    }

    public long getFechaCreacion() {
        return fechaCreacion;
    }

    public long getFechaUltimoMensaje() {
        return fechaUltimoMensaje;
    }

    public void setCentroId(String centroId) {
        this.centroId = centroId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public void setEstado(EstadoChat estado) {
        this.estado = estado;
    }

    public void setFechaCreacion(long fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public void setFechaUltimoMensaje(long fechaUltimoMensaje) {
        this.fechaUltimoMensaje = fechaUltimoMensaje;
    }
}