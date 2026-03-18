package com.example.sportiva_booking_android.v2.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

import java.io.Serializable;

@IgnoreExtraProperties
public class DomainEntity implements Serializable {

    /*Atributos de la Clase*/
    private String id;

    /*Constructor de la Clase*/
    public DomainEntity() {

    }

    /*Getters y Setters de la Clase*/
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
