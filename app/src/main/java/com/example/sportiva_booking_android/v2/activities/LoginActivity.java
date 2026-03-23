package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Activity encargada de manejar el inicio de sesión de los usuarios
 */
public class LoginActivity extends AppCompatActivity {

    /*Componentes de la vista*/
    private EditText etEmail;
    private EditText etPassword;
    private Button btnLogin;
    private TextView tvGoToSignUp;

    /*Variables donde guardaremos los datos introducidos por el usuario*/
    private String userEmail;
    private String userPassword;

    /*Instancia de Firebase Authentication*/
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        /*Inicializamos Firebase Auth*/
        firebaseAuth = FirebaseAuth.getInstance();

        /*Inicializamos los componentes de la vista*/
        etEmail = findViewById(R.id.etEmailLogin);
        etPassword = findViewById(R.id.etPasswordLogin);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToSignUp = findViewById(R.id.tvRegisterLogin);


        btnLogin.setOnClickListener(v -> login());

        tvGoToSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    /**
     * Método mediante el cual gestionamos el inicio de sesión del usuario
     */
    private void login(){

        /*Obtenemos los valores introducidos por el usuario*/
        userEmail = etEmail.getText().toString().trim();
        userPassword = etPassword.getText().toString().trim();

        /*Validación de que ambos campos han sido rellenados*/
        if(userEmail.isEmpty() || userPassword.isEmpty()){
            Snackbar.make(findViewById(android.R.id.content),
                    "Por favor, rellena todos los campos para continuar",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        /*Intentamos iniciar sesión mediante Firebase Authentication*/
        firebaseAuth.signInWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(task -> {

                    /*Si el login ha sido correcto*/
                    if(task.isSuccessful()){

                        Snackbar.make(findViewById(android.R.id.content),
                                "¡Bienvenido de nuevo a Sportiva Booking!",
                                Snackbar.LENGTH_LONG).show();

                        /*Redirigimos al usuario a la pantalla principal*/
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();

                    }else{

                        Snackbar.make(findViewById(android.R.id.content),
                                "Error al iniciar sesión. Comprueba tus credenciales",
                                Snackbar.LENGTH_LONG).show();

                    }
                });

    }

}