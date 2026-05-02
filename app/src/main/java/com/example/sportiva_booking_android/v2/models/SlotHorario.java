package com.example.sportiva_booking_android.v2.models;

import com.example.sportiva_booking_android.v2.enums.EstadoSlot;

public class SlotHorario {

    /*Atributos de la Clase*/
    private String horaInicio;
    private String horaFin;
    private EstadoSlot estado;
    private Session sesion;

    /*Constructor de la Clase*/
    public SlotHorario() {
    }

    /**
     * Constructor completo para crear el slot directamente con todos sus datos.
     *
     * @param horaInicio Hora de inicio en formato HH:mm
     * @param horaFin    Hora de fin en formato HH:mm
     * @param estado     Estado calculado: LIBRE, PROPIO u OCUPADO
     * @param sesion     Sesión vinculada o null si el slot está libre
     */
    public SlotHorario(String horaInicio, String horaFin, EstadoSlot estado, Session sesion) {
        this.horaInicio = horaInicio;
        this.horaFin    = horaFin;
        this.estado     = estado;
        this.sesion     = sesion;
    }

    /*Getters y Setters de la Clase*/
    public String getHoraInicio() {
        return horaInicio;
    }

    public String getHoraFin() {
        return horaFin;
    }

    public EstadoSlot getEstado() {
        return estado;
    }

    public Session getSesion() {
        return sesion;
    }

    public void setHoraInicio(String horaInicio) {
        this.horaInicio = horaInicio;
    }

    public void setHoraFin(String horaFin) {
        this.horaFin = horaFin;
    }

    public void setEstado(EstadoSlot estado) {
        this.estado = estado;
    }

    public void setSesion(Session sesion) {
        this.sesion = sesion;
    }
}