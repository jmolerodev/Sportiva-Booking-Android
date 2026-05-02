package com.example.sportiva_booking_android.v2.models;

import com.example.sportiva_booking_android.v2.enums.EstadoSesion;
import com.example.sportiva_booking_android.v2.enums.ModalidadSesion;
import com.example.sportiva_booking_android.v2.enums.TipoSesion;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Session extends DomainEntity {

    /*Atributos de la Clase*/
    private String centroId;
    private String profesionalId;
    private TipoSesion tipo;
    private long fecha;
    private String horaInicio;
    private String horaFin;
    private ModalidadSesion modalidad;
    private int aforoMax;
    private int aforoActual;
    private String titulo;
    private String descripcion;
    private EstadoSesion estado;

    /*Constructor de la Clase*/
    public Session() {
    }

    /*Getters y Setters de la Clase*/
    public String getCentroId() {
        return centroId;
    }

    public String getProfesionalId() {
        return profesionalId;
    }

    public TipoSesion getTipo() {
        return tipo;
    }

    public long getFecha() {
        return fecha;
    }

    public String getHoraInicio() {
        return horaInicio;
    }

    public String getHoraFin() {
        return horaFin;
    }

    public ModalidadSesion getModalidad() {
        return modalidad;
    }

    public int getAforoMax() {
        return aforoMax;
    }

    public int getAforoActual() {
        return aforoActual;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public EstadoSesion getEstado() {
        return estado;
    }

    public void setCentroId(String centroId) {
        this.centroId = centroId;
    }

    public void setProfesionalId(String profesionalId) {
        this.profesionalId = profesionalId;
    }

    public void setTipo(TipoSesion tipo) {
        this.tipo = tipo;
    }

    public void setFecha(long fecha) {
        this.fecha = fecha;
    }

    public void setHoraInicio(String horaInicio) {
        this.horaInicio = horaInicio;
    }

    public void setHoraFin(String horaFin) {
        this.horaFin = horaFin;
    }

    public void setModalidad(ModalidadSesion modalidad) {
        this.modalidad = modalidad;
    }

    public void setAforoMax(int aforoMax) {
        this.aforoMax = aforoMax;
    }

    public void setAforoActual(int aforoActual) {
        this.aforoActual = aforoActual;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setEstado(EstadoSesion estado) {
        this.estado = estado;
    }
}