package com.example.sportiva_booking_android.v2.models;

import com.example.sportiva_booking_android.v2.enums.Rol;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public abstract class Person extends UserAccount {

    /*Atributos de la Clase*/
    private String nombre;
    private String apellidos;
    private String foto;
    private Rol rol;

    /*Constructor de la Clase*/
    public Person (){

    }

    /*Getters y Setters de la Clase*/
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }
}
