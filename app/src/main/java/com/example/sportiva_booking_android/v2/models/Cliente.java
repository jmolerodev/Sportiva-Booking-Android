package com.example.sportiva_booking_android.v2.models;


import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Cliente extends Person {

    /*Atributos de la Clase*/
    private String dni;
    private String direccion;
    private long fecha_alta;
    private boolean is_active;

    /*Constructor de la Clase*/
    public Cliente (){

    }

    /*Getters y Setters de la Clase*/
    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public long getFecha_alta() {
        return fecha_alta;
    }

    public void setFecha_alta(long fecha_alta) {
        this.fecha_alta = fecha_alta;
    }

    public boolean isIs_active() {
        return is_active;
    }

    public void setIs_active(boolean is_active) {
        this.is_active = is_active;
    }
}
