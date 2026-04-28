package com.example.sportiva_booking_android.v2.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Map;

@IgnoreExtraProperties
public class SportCentre extends DomainEntity {

    /*Atributos de la Clase*/
    private String nombre;
    private String direccion;
    private String telefono;
    private String foto;
    private String adminUid;
    private Map<String, HorarioDia> horario;

    /*Constructor de la Clase*/
    public SportCentre() {
    }

    /*Getters y Setters*/
    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getFoto() {
        return foto;
    }

    public String getAdminUid() {
        return adminUid;
    }

    public Map<String, HorarioDia> getHorario() {
        return horario;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public void setAdminUid(String adminUid) {
        this.adminUid = adminUid;
    }

    public void setHorario(Map<String, HorarioDia> horario) {
        this.horario = horario;
    }

    /*Clase axuliar con la que controlaremos el horario dirario de cada centro deportivo*/
    @IgnoreExtraProperties
    public static class HorarioDia {

        private boolean abierto;
        private String apertura;
        private String cierre;

        public HorarioDia() {
        }

        public boolean isAbierto() {
            return abierto;
        }

        public String getApertura() {
            return apertura;
        }

        public String getCierre() {
            return cierre;
        }

        public void setAbierto(boolean abierto) {
            this.abierto = abierto;
        }

        public void setApertura(String apertura) {
            this.apertura = apertura;
        }

        public void setCierre(String cierre) {
            this.cierre = cierre;
        }
    }
}