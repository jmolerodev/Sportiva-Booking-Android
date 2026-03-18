package com.example.sportiva_booking_android.v2.models;

import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.auth.User;

@IgnoreExtraProperties
public class UserAccount extends DomainEntity{


    /*Atributos de la Clase*/
    private String email;
    private String password;

    /*Constructor de la Clase*/
    public UserAccount (){

    }

    /*Getters y Setters de la Clase*/
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
