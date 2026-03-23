package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Especialidad;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Administrador;
import com.example.sportiva_booking_android.v2.models.Cliente;
import com.example.sportiva_booking_android.v2.models.Profesional;
import com.example.sportiva_booking_android.v2.services.AdministradorService;
import com.example.sportiva_booking_android.v2.services.ClienteService;
import com.example.sportiva_booking_android.v2.services.ProfesionalService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignUpActivity extends AppCompatActivity {

    /*Componentes de la vista*/
    private RadioGroup rgRol;
    private EditText etNombre, etApellidos, etEmail, etPassword, etConfirmPassword;
    private LinearLayout layoutCamposCliente, layoutCamposProfesional;
    private Button btnRegister;
    private TextView tvLogin;
    private EditText etDni, etDireccion;
    private EditText etDescripcion, etAnnosExperiencia;
    private Spinner spinnerEspecialidad;
    private Rol rolSeleccionado = null;
    private FirebaseAuth firebaseAuth;
    private ClienteService clienteService;
    private ProfesionalService profesionalService;
    private AdministradorService administradorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        /*Inicializamos Firebase Auth y los servicios*/
        firebaseAuth = FirebaseAuth.getInstance();
        clienteService = new ClienteService(this);
        profesionalService = new ProfesionalService(this);
        administradorService = new AdministradorService(this);

        /*Inicializamos los componentes de la vista*/
        rgRol = findViewById(R.id.rgRolRegister);
        etNombre = findViewById(R.id.etNombreRegister);
        etApellidos = findViewById(R.id.etApellidosRegister);
        etEmail = findViewById(R.id.etEmailRegister);
        etPassword = findViewById(R.id.etPasswordRegister);
        etConfirmPassword = findViewById(R.id.etConfirmPasswordRegister);
        layoutCamposCliente = findViewById(R.id.layoutCamposCliente);
        layoutCamposProfesional = findViewById(R.id.layoutCamposProfesional);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLoginRegister);
        etDni = findViewById(R.id.etDniRegister);
        etDireccion = findViewById(R.id.etDireccionRegister);
        etDescripcion = findViewById(R.id.etDescripcionRegister);
        etAnnosExperiencia = findViewById(R.id.etAnnosExperienciaRegister);
        spinnerEspecialidad = findViewById(R.id.spinnerEspecialidadRegister);

        /*Cargamos los valores del enum Especialidad en el Spinner*/
        ArrayAdapter<Especialidad> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Especialidad.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEspecialidad.setAdapter(adapter);

        /*Listener del RadioGroup para mostrar/ocultar campos según el rol seleccionado*/
        rgRol.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCliente) {
                rolSeleccionado = Rol.CLIENTE;
                layoutCamposCliente.setVisibility(View.VISIBLE);
                layoutCamposProfesional.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbProfesional) {
                rolSeleccionado = Rol.PROFESIONAL;
                layoutCamposCliente.setVisibility(View.GONE);
                layoutCamposProfesional.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rbAdministrador) {
                rolSeleccionado = Rol.ADMINISTRADOR;
                layoutCamposCliente.setVisibility(View.GONE);
                layoutCamposProfesional.setVisibility(View.GONE);
            }
        });

        btnRegister.setOnClickListener(v -> register());

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });
    }

    /**
     * Método mediante el cual gestionamos el registro del usuario según el rol seleccionado
     */
    private void register() {

        /*Obtenemos los valores comunes introducidos por el usuario*/
        String nombre = etNombre.getText().toString().trim();
        String apellidos = etApellidos.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        /*Validación de campos comunes*/
        if (rolSeleccionado == null || nombre.isEmpty() || apellidos.isEmpty()
                || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content),
                    "Por favor, rellena todos los campos para continuar",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        /*Validación de que las contraseñas coinciden*/
        if (!password.equals(confirmPassword)) {
            Snackbar.make(findViewById(android.R.id.content),
                    "Las contraseñas no coinciden",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        /*Validación detallada de contraseña*/
        if (!validarPasswordConMensaje(password)) {
            return;
        }

        /*Creamos el usuario en Firebase Auth y luego lo guardamos en la Base de Datos*/
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        String uid = firebaseUser.getUid();

                        switch (rolSeleccionado) {
                            case CLIENTE:
                                registrarCliente(uid, nombre, apellidos, email, password);
                                break;
                            case PROFESIONAL:
                                registrarProfesional(uid, nombre, apellidos, email, password);
                                break;
                            case ADMINISTRADOR:
                                registrarAdministrador(uid, nombre, apellidos, email, password);
                                break;
                        }

                    } else {
                        Snackbar.make(findViewById(android.R.id.content),
                                "Error al crear la cuenta. Inténtalo de nuevo.",
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Validación de contraseña con mensajes específicos
     */
    private boolean validarPasswordConMensaje(String password) {

        StringBuilder errores = new StringBuilder();

        if (password.length() < 9) {
            errores.append(" 9 caracteres, ");
        }

        if (!password.matches(".*[A-Z].*")) {
            errores.append(" 1 mayúscula, ");
        }

        if (!password.matches(".*[a-z].*")) {
            errores.append(" 1 minúscula, ");
        }

        if (!password.matches(".*\\d.*")) {
            errores.append(" 1 número, ");
        }

        if (!password.matches(".*[^A-Za-z\\d].*")) {
            errores.append(" 1 carácter especial, ");
        }

        if (errores.length() > 0) {

            String mensaje = errores.toString();


            mensaje = mensaje.substring(0, mensaje.length() - 2);

            Snackbar.make(findViewById(android.R.id.content),
                    "La contraseña no cumple los requisitos: " + mensaje,
                    Snackbar.LENGTH_LONG).show();

            return false;
        }

        return true;
    }

    private void registrarCliente(String uid, String nombre, String apellidos, String email, String password) {

        String dni = etDni.getText().toString().trim();
        String direccion = etDireccion.getText().toString().trim();

        if (dni.isEmpty() || direccion.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content),
                    "Por favor, rellena todos los campos del Cliente para continuar",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        Cliente cliente = new Cliente();
        cliente.setId(uid);
        cliente.setNombre(nombre);
        cliente.setApellidos(apellidos);
        cliente.setEmail(email);
        cliente.setPassword(password);
        cliente.setRol(Rol.CLIENTE);
        cliente.setFoto("");
        cliente.setDni(dni);
        cliente.setDireccion(direccion);
        cliente.setFecha_alta(System.currentTimeMillis());
        cliente.setIs_active(true);

        clienteService.insertCliente(cliente);
        onRegistroExitoso();
    }

    private void registrarProfesional(String uid, String nombre, String apellidos, String email, String password) {

        String descripcion = etDescripcion.getText().toString().trim();
        String annosStr = etAnnosExperiencia.getText().toString().trim();
        Especialidad especialidad = (Especialidad) spinnerEspecialidad.getSelectedItem();

        if (descripcion.isEmpty() || annosStr.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content),
                    "Por favor, rellena todos los campos del Profesional para continuar",
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        Profesional profesional = new Profesional();
        profesional.setId(uid);
        profesional.setNombre(nombre);
        profesional.setApellidos(apellidos);
        profesional.setEmail(email);
        profesional.setPassword(password);
        profesional.setRol(Rol.PROFESIONAL);
        profesional.setFoto("");
        profesional.setDescripcion(descripcion);
        profesional.setAnnos_experiencia(Integer.parseInt(annosStr));
        profesional.setEspecialidad(especialidad);

        profesionalService.insertProfesional(profesional);
        onRegistroExitoso();
    }

    private void registrarAdministrador(String uid, String nombre, String apellidos, String email, String password) {

        Administrador administrador = new Administrador();
        administrador.setId(uid);
        administrador.setNombre(nombre);
        administrador.setApellidos(apellidos);
        administrador.setEmail(email);
        administrador.setPassword(password);
        administrador.setRol(Rol.ADMINISTRADOR);
        administrador.setFoto("");

        administradorService.insertAdministrador(administrador);
        onRegistroExitoso();
    }

    private void onRegistroExitoso() {
        Snackbar.make(findViewById(android.R.id.content),
                "¡Cuenta creada con éxito! Bienvenido a Sportiva Booking",
                Snackbar.LENGTH_LONG).show();
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}