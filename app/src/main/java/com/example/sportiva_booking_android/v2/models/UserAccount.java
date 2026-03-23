package com.example.sportiva_booking_android.v2.models;


import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class UserAccount extends DomainEntity{


    /*Atributos de la Clase*/
    private String email;
    private String password;

    /*Constructor de la Clase*/
    public UserAccount (){

    }

    /*Getters y Setters de la Clase*/
    @Exclude
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Exclude
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
