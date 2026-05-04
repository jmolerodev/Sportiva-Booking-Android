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
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

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

    /*Instancia de Firebase Functions para verificar si el correo existe en Auth*/
    private FirebaseFunctions firebaseFunctions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);

        /*Inicializamos Firebase Auth y Functions*/
        firebaseAuth      = FirebaseAuth.getInstance();
        firebaseFunctions = FirebaseFunctions.getInstance();

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
     * Valida el email siguiendo exactamente la lógica del customEmailValidator() de Angular.
     * Marca el error visual en rojo directamente en el TextInputLayout.
     */
    private boolean validateEmail() {

        if (userEmail.isEmpty()) {
            tilEmailReset.setError("El correo electrónico es obligatorio");
            return false;
        }

        /*Regex donde se valida el formato del correo electrónico*/
        String simpleEmailRegex = "^[^@]+@[^@]+\\.[a-zA-Z]{2,}$";

        if (!userEmail.matches(simpleEmailRegex)) {
            tilEmailReset.setError("Introduce un correo electrónico válido");
            return false;
        }

        tilEmailReset.setError(null);
        return true;
    }

    /**
     * Valida los campos del formulario antes de intentar enviar el correo.
     * Combina errores visuales en rojo bajo el campo + Snackbar general si hay algún error.
     */
    private boolean validateFields() {

        if (!validateEmail()) {
            showCenteredSnackbar("Por favor, rellena los campos correctamente para continuar");
            return false;
        }

        return true;
    }

    /**
     * Método mediante el cual enviaremos el correo de restablecimiento de contraseña via Firebase Auth.
     * Antes de enviarlo, verificamos mediante la Cloud Function checkEmailExists que el correo
     * esté registrado en Auth para evitar envíos a cuentas inexistentes.
     */
    private void sendResetEmail() {

        /*Obtenemos el texto del campo quitando espacios*/
        userEmail = etEmailReset.getText() != null
                ? etEmailReset.getText().toString().trim()
                : "";

        /*Limpiamos cualquier error visual anterior antes de revalidar*/
        tilEmailReset.setError(null);

        /*Validamos los campos antes de continuar*/
        if (!validateFields()) return;

        /*Construimos el payload para la Cloud Function*/
        Map<String, Object> data = new HashMap<>();
        data.put("email", userEmail);

        /*Verificamos primero si el correo existe en Auth antes de enviar el email*/
        firebaseFunctions
                .getHttpsCallable("checkEmailExists")
                .call(data)
                .addOnSuccessListener(result -> {

                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                    boolean exists = Boolean.TRUE.equals(resultData.get("exists"));

                    if (!exists) {
                        onEmailNotFound();
                        return;
                    }

                    /*Si el correo existe procedemos a enviar el correo de restablecimiento*/
                    firebaseAuth.sendPasswordResetEmail(userEmail)
                            .addOnSuccessListener(unused -> onEmailSentSuccess())
                            .addOnFailureListener(e -> onEmailSentFailure());
                })
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
     * Se ejecuta cuando el correo introducido no está registrado en Auth
     */
    private void onEmailNotFound() {
        showCenteredSnackbar("No se encontró ninguna cuenta con ese correo electrónico");
    }

    /**
     * Se ejecuta cuando Firebase devuelve un error inesperado
     */
    private void onEmailSentFailure() {
        showCenteredSnackbar("Ha ocurrido un error. Inténtalo de nuevo más tarde");
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