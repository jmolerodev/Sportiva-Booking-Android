package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;

/**
 * Activity que actúa como Splash Screen de la aplicación.
 * Muestra el logo de Sportiva Booking antes de redirigir al Login.
 */
public class WelcomeActivity extends AppCompatActivity {

    /*Duración de la pantalla de bienvenida en ms*/
    private static final long WELCOME_DELAY_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome);

        /*Iniciamos la navegación retardada hacia el Login*/
        navigateToLoginDelayed();
    }

    /**
     * Método utilitario para navegar con un delay al LoginActivity,
     * permitiendo que el usuario vea el branding de la app.
     */
    private void navigateToLoginDelayed() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);

            /*Finalizamos esta activity para que no se pueda volver atrás con el botón de retroceso*/
            finish();
        }, WELCOME_DELAY_MS);
    }
}