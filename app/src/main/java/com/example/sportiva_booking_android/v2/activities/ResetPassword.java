package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Activity encargada de gestionar el restablecimiento de contraseña del usuario.
 * El usuario introduce su correo y Firebase Auth le manda un enlace para resetearla.
 */
public class ResetPassword extends AppCompatActivity {

    /*Componentes de la vista*/
    private TextInputEditText etEmailReset;
    private TextInputLayout   tilEmailReset;
    private Button            btnSendReset;
    private Button            btnBackToLoginReset;
    private LinearLayout      layoutEmailSent;

    /*Variable donde guardaremos el correo introducido por el usuario*/
    private String userEmail;

    /*Instancia de Firebase Authentication*/
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);

        /*Inicializamos Firebase Auth*/
        firebaseAuth = FirebaseAuth.getInstance();



        /*Inicializamos los componentes de la vista*/
        etEmailReset        = findViewById(R.id.etEmailReset);
        tilEmailReset       = findViewById(R.id.tilEmailReset);
        btnSendReset        = findViewById(R.id.btnSendReset);
        btnBackToLoginReset = findViewById(R.id.btnBackToLoginReset);
        layoutEmailSent     = findViewById(R.id.layoutEmailSent);

        /*Listeners*/
        btnSendReset.setOnClickListener(v -> sendResetEmail());
        btnBackToLoginReset.setOnClickListener(v -> navigateToLogin());
    }

    /**
     * Método utilitario para mostrar Snackbars centrados
     */
    private void showCenteredSnackbar(String message) {

        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                message,
                Snackbar.LENGTH_LONG);

        /*Centramos el texto del Snackbar*/
        TextView textView = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );

        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);

        snackbar.show();
    }

    /**
     * Valida el email siguiendo exactamente la lógica del customEmailValidator() de Angular
     */
    private boolean validateEmail() {

        /*Si el campo está vacío, no mostramos error todavía*/
        if (userEmail == null || userEmail.isEmpty()) {
            tilEmailReset.setError(null);
            return true;
        }

        /*Regex donde se valida el formato del correo electrónico*/
        String simpleEmailRegex = "^[^@]+@[^@]+\\.[a-zA-Z]{2,}$";

        boolean valid = userEmail.matches(simpleEmailRegex);

        tilEmailReset.setError(valid ? null : "Introduce un correo electrónico válido");

        return valid;
    }

    /**
     * Valida los campos del formulario antes de intentar enviar el correo
     */
    private boolean validateFields() {

        /*Comprobamos que el campo no esté vacío*/
        if (userEmail.isEmpty()) {
            showCenteredSnackbar("Por favor, rellena todos los campos para continuar");
            return false;
        }

        /*Validamos el formato del email*/
        if (!validateEmail()) {
            showCenteredSnackbar("El formato del correo electrónico no es válido");
            return false;
        }

        return true;
    }

    /**
     * Método mediante el cual enviaremos el correo de restablecimiento de contraseña via Firebase Auth
     */
    private void sendResetEmail() {

        /*Obtenemos el texto del campo quitando espacios*/
        userEmail = etEmailReset.getText() != null
                ? etEmailReset.getText().toString().trim()
                : "";

        /*Validamos los campos antes de continuar*/
        if (!validateFields()) return;

        /*Limpiamos cualquier error anterior si la validación fue bien*/
        tilEmailReset.setError(null);

        /*Enviamos el correo de restablecimiento via Firebase Auth*/
        firebaseAuth.sendPasswordResetEmail(userEmail)
                .addOnSuccessListener(unused -> onEmailSentSuccess())
                .addOnFailureListener(e -> onEmailSentFailure());
    }

    /**
     * Se ejecuta cuando Firebase confirma el envío del correo
     */
    private void onEmailSentSuccess() {

        /*Hacemos visible el bloque de confirmación que estaba en GONE*/
        layoutEmailSent.setVisibility(View.VISIBLE);

        /*Deshabilitamos el botón para evitar reenvíos accidentales*/
        btnSendReset.setEnabled(false);

        showCenteredSnackbar("Correo de restablecimiento enviado correctamente");
    }

    /**
     * Se ejecuta cuando Firebase devuelve un error inesperado
     */
    private void onEmailSentFailure() {
        showCenteredSnackbar("No se encontró ninguna cuenta con ese correo electrónico");
    }

    /**
     * Método mediante el cual navegaremos de vuelta a la pantalla de Login
     */
    private void navigateToLogin() {
        Intent intent = new Intent(ResetPassword.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}