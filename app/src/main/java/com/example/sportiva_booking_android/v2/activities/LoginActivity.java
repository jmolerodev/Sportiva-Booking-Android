package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Activity encargada de manejar el inicio de sesión de los usuarios
 */
public class LoginActivity extends AppCompatActivity {

    /*Clave para SharedPreferences*/
    private static final String PREFS_NAME   = "SportivaPrefs";
    private static final String KEY_REMEMBER = "rememberUser";

    /*Delay en ms para que el Snackbar sea visible antes de navegar*/
    private static final long NAVIGATE_DELAY_MS = 2000;

    /*Componentes de la vista*/
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputLayout   tilEmail;
    private TextInputLayout   tilPassword;
    private Button            btnLogin;
    private CheckBox          cbRemember;
    private TextView          tvForgotPassword;
    private TextView          tvGoToSignUp;

    /*Variables donde guardaremos los datos introducidos por el usuario*/
    private String userEmail;
    private String userPassword;

    /*Instancia de Firebase Authentication*/
    private FirebaseAuth firebaseAuth;

    /*SharedPreferences para persistir la preferencia de "Recordar usuario"*/
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        /*Inicializamos Firebase Auth*/
        firebaseAuth = FirebaseAuth.getInstance();

        /*Inicializamos SharedPreferences*/
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        /*Si el usuario marcó "Recordar usuario" y sigue con sesión activa, navegamos directamente*/
        checkRememberedSession();

        /*Inicializamos los componentes de la vista*/
        etEmail          = findViewById(R.id.etEmailLogin);
        etPassword       = findViewById(R.id.etPasswordLogin);
        tilEmail         = findViewById(R.id.tilEmailLogin);
        tilPassword      = findViewById(R.id.tilPasswordLogin);
        btnLogin         = findViewById(R.id.btnLogin);
        cbRemember       = findViewById(R.id.cbRememberLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPasswordLogin);
        tvGoToSignUp     = findViewById(R.id.tvRegisterLogin);

        /*Listeners*/
        btnLogin.setOnClickListener(v -> login());
        tvGoToSignUp.setOnClickListener(v -> navigateToSignUp());
        tvForgotPassword.setOnClickListener(v -> navigateToResetPassword());
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
     * Método utilitario para navegar con un pequeño delay,
     * de forma que el Snackbar sea visible antes de cambiar de pantalla
     */
    private void navigateToMainDelayed() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
        }, NAVIGATE_DELAY_MS);
    }

    /**
     * Comprueba si el usuario había marcado "Recordar usuario" y tiene sesión activa en Firebase.
     */
    private void checkRememberedSession() {

        boolean rememberUser = sharedPreferences.getBoolean(KEY_REMEMBER, false);

        if (rememberUser) {

            FirebaseUser currentUser = firebaseAuth.getCurrentUser();

            if (currentUser != null) {

                /*Obtenemos el UID del usuario*/
                String userId = currentUser.getUid();

                /*Referencia al nodo Persons*/
                DatabaseReference userRef = FirebaseDatabase.getInstance()
                        .getReference("Persons")
                        .child(userId);

                /*Obtenemos los datos del usuario*/
                userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        if (snapshot.exists()) {

                            /*Mostramos mensaje de sesión recordada y navegamos con delay*/
                            showCenteredSnackbar("¡Ya tenías la sesión iniciada! Bienvenido de nuevo");
                            navigateToMainDelayed();

                        } else {
                            showCenteredSnackbar("No se encontraron datos del usuario.");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showCenteredSnackbar("Error al recuperar los datos del usuario.");
                    }
                });
            }
        }
    }

    /**
     * Método mediante el cual gestionamos el inicio de sesión del usuario
     */
    private void login() {

        /*Obtenemos los valores introducidos por el usuario*/
        userEmail    = etEmail.getText().toString().trim();
        userPassword = etPassword.getText().toString().trim();

        /*Validamos los campos del formulario*/
        if (!validateFields()) return;

        /*Intentamos iniciar sesión mediante Firebase Authentication*/
        firebaseAuth.signInWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        /*Persistimos la preferencia de "Recordar usuario"*/
                        sharedPreferences.edit()
                                .putBoolean(KEY_REMEMBER, cbRemember.isChecked())
                                .apply();

                        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                        if (currentUser == null) {
                            showCenteredSnackbar("Error inesperado. Inténtalo de nuevo.");
                            return;
                        }

                        String userId = currentUser.getUid();

                        /*Recuperamos datos del usuario desde Firebase Database*/
                        DatabaseReference userRef = FirebaseDatabase.getInstance()
                                .getReference("Persons")
                                .child(userId);

                        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot snapshot) {

                                if (snapshot.exists()) {

                                    /*Mostramos mensaje de bienvenida y navegamos con delay*/
                                    showCenteredSnackbar("¡Bienvenido a Sportiva Booking!");
                                    navigateToMainDelayed();

                                } else {
                                    showCenteredSnackbar("No se encontraron datos del usuario.");
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                showCenteredSnackbar("Error al recuperar los datos del usuario.");
                            }
                        });

                    } else {
                        showCenteredSnackbar("Lo sentimos, pero las credenciales son incorrectas");
                    }
                });
    }

    /**
     * Valida el email siguiendo exactamente la lógica del customEmailValidator() de Angular
     */
    private boolean validateEmail() {

        /*Si el campo está vacío, no mostramos error todavía*/
        if (userEmail == null || userEmail.isEmpty()) {
            tilEmail.setError(null);
            return true;
        }

        /*Regex donde se valida el formato del correo electrónico*/
        String simpleEmailRegex = "^[^@]+@[^@]+\\.[a-zA-Z]{2,}$";

        boolean valid = userEmail.matches(simpleEmailRegex);

        tilEmail.setError(valid ? null : "Introduce un correo electrónico válido");

        return valid;
    }

    /**
     * Valida la contraseña siguiendo exactamente la lógica del customPasswordValidator() de Angular
     */
    private boolean validatePassword() {

        /*Si el campo está vacío, no mostramos error todavía*/
        if (userPassword == null || userPassword.isEmpty()) {
            tilPassword.setError(null);
            return true;
        }

        /*Regex donde se valida el formato de la contraseña*/
        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d])[A-Za-z\\d\\W]{9,}$";

        boolean valid = userPassword.matches(passwordRegex);

        if (!valid) {

            /*Mensaje completo en rojo debajo del campo*/
            StringBuilder sb = new StringBuilder();
            sb.append("La contraseña no cumple con el formato correcto:\n");
            sb.append("• 9 caracteres mínimo\n");
            sb.append("• 1 mayúscula\n");
            sb.append("• 1 minúscula\n");
            sb.append("• 1 número\n");
            sb.append("• 1 carácter especial");

            tilPassword.setError(sb.toString());

        } else {
            tilPassword.setError(null);
        }

        return valid;
    }

    /**
     * Método mediante el cual validamos los campos del formulario de inicio de sesión
     */
    private boolean validateFields() {

        /*Comprobamos que los campos no estén vacíos*/
        if (userEmail.isEmpty() || userPassword.isEmpty()) {
            showCenteredSnackbar("Por favor, rellena los campos correctamente para iniciar sesión");
            return false;
        }

        /*Validamos el formato del email y la contraseña*/
        boolean emailValid    = validateEmail();
        boolean passwordValid = validatePassword();

        if (!emailValid || !passwordValid) {
            showCenteredSnackbar("Por favor, rellena los campos correctamente para iniciar sesión");
            return false;
        }

        return true;
    }

    /**
     * Método mediante el cual navegaremos a la pantalla de Registro
     */
    private void navigateToSignUp() {
        Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
        startActivity(intent);
    }

    /**
     * Método mediante el cual navegaremos a la pantalla de Recuperar Contraseña
     */
    private void navigateToResetPassword() {
        Intent intent = new Intent(LoginActivity.this, ResetPassword.class);
        startActivity(intent);
    }
}