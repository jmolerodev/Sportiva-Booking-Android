package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
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


        btnLogin.setOnClickListener(v -> login());
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
            Toast.makeText(this, "Por favor, rellena todos los campos para continuar", Toast.LENGTH_LONG).show();
            return;
        }

        /*Intentamos iniciar sesión mediante Firebase Authentication*/
        firebaseAuth.signInWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(task -> {

                    /*Si el login ha sido correcto*/
                    if(task.isSuccessful()){

                        Toast.makeText(getApplicationContext(),
                                "¡Bienvenido de nuevo a Sportiva Booking!",
                                Toast.LENGTH_LONG).show();

                        /*Redirigimos al usuario a la pantalla principal*/
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();

                    }else{

                        Toast.makeText(getApplicationContext(),
                                "Error al iniciar sesión. Comprueba tus credenciales",
                                Toast.LENGTH_LONG).show();

                    }
                });

    }

}