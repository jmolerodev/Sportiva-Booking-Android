package com.example.sportiva_booking_android.v2.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Membership extends DomainEntity {

    /*Atributos de la Clase*/
    private String clienteId;
    private String centroId;
    private String tipo;
    private long fechaInicio;
    private long fechaFin;
    private String estado;
    private String transactionId;
    private double importe;

    /*Constructor de la Clase*/
    public Membership() {
    }

    /*Getters y Setters*/
    public String getClienteId() {
        return clienteId;
    }

    public String getCentroId() {
        return centroId;
    }

    public String getTipo() {
        return tipo;
    }

    public long getFechaInicio() {
        return fechaInicio;
    }

    public long getFechaFin() {
        return fechaFin;
    }

    public String getEstado() {
        return estado;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public double getImporte() {
        return importe;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public void setCentroId(String centroId) {
        this.centroId = centroId;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public void setFechaInicio(long fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public void setFechaFin(long fechaFin) {
        this.fechaFin = fechaFin;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setImporte(double importe) {
        this.importe = importe;
    }
}