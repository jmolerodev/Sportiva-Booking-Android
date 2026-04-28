package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Cliente;
import com.example.sportiva_booking_android.v2.services.ClienteService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignUpActivity extends AppCompatActivity {

    /*Componentes de la vista*/
    private TextInputLayout tilNombre, tilApellidos, tilEmail, tilPassword, tilConfirmPassword, tilDni, tilDireccion;
    private TextInputEditText etNombre, etApellidos, etEmail, etPassword, etConfirmPassword, etDni, etDireccion;
    private Button btnRegister;
    private TextView tvLogin;

    /*Servicios y autenticación*/
    private FirebaseAuth firebaseAuth;
    private ClienteService clienteService;

    /*Variable booleana que nos indica si el formulario se está procesando (para evitar doble envío)*/
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        /*Inicializamos Firebase Auth y los servicios*/
        firebaseAuth = FirebaseAuth.getInstance();
        clienteService = new ClienteService(this);

        /*Inicializamos los componentes de la vista*/
        tilNombre            = findViewById(R.id.tilNombreRegister);
        tilApellidos         = findViewById(R.id.tilApellidosRegister);
        tilEmail             = findViewById(R.id.tilEmailRegister);
        tilPassword          = findViewById(R.id.tilPasswordRegister);
        tilConfirmPassword   = findViewById(R.id.tilConfirmPasswordRegister);
        tilDni               = findViewById(R.id.tilDniRegister);
        tilDireccion         = findViewById(R.id.tilDireccionRegister);

        etNombre             = findViewById(R.id.etNombreRegister);
        etApellidos          = findViewById(R.id.etApellidosRegister);
        etEmail              = findViewById(R.id.etEmailRegister);
        etPassword           = findViewById(R.id.etPasswordRegister);
        etConfirmPassword    = findViewById(R.id.etConfirmPasswordRegister);
        etDni                = findViewById(R.id.etDniRegister);
        etDireccion          = findViewById(R.id.etDireccionRegister);

        btnRegister = findViewById(R.id.btnRegister);
        tvLogin     = findViewById(R.id.tvLoginRegister);

        /*Listener del botón de registro*/
        btnRegister.setOnClickListener(v -> signUp());

        /*Listener para navegar al Login*/
        tvLogin.setOnClickListener(v -> navigateToLogin());
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
     * Método principal que gestiona el registro de un nuevo cliente en el sistema.
     * Replica la lógica del componente Angular SignUp: primero valida el formulario,
     * luego verifica que el DNI no esté registrado, y finalmente crea el usuario
     * en Firebase Auth y guarda sus datos en la base de datos.
     */
    private void signUp() {

        /*Evitamos doble envío mientras se procesa la petición*/
        if (isLoading) return;

        /*Obtenemos los valores introducidos por el usuario*/
        String nombre          = etNombre.getText().toString().trim();
        String apellidos       = etApellidos.getText().toString().trim();
        String email           = etEmail.getText().toString().trim();
        String password        = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String dni             = etDni.getText().toString().trim().toUpperCase();
        String direccion       = etDireccion.getText().toString().trim();

        /*Limpiamos los errores previos antes de revalidar*/
        limpiarErrores();

        /*Validamos todos los campos del formulario antes de proceder*/
        if (!validarFormulario(nombre, apellidos, email, password, confirmPassword, dni, direccion)) {
            /*Snackbar general cuando hay errores de validación*/
            showCenteredSnackbar("Por favor, rellena los campos correctamente para crear tu cuenta");
            return;
        }

        isLoading = true;
        btnRegister.setEnabled(false);

        /*Comprobamos si el DNI ya está registrado antes de crear el usuario en Firebase Auth*/
        clienteService.isDniAlreadyRegistered(dni, dniExiste -> {

            if (dniExiste) {
                tilDni.setError("El DNI introducido ya está registrado en el sistema");
                showCenteredSnackbar("El DNI introducido ya está registrado en el sistema");
                resetLoading();
                return;
            }

            /*Si el DNI es único, procedemos con el registro en Firebase Auth*/
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            String uid = firebaseUser.getUid();

                            /*Construimos el objeto Cliente con los datos del formulario*/
                            Cliente nuevoCliente = new Cliente();
                            nuevoCliente.setId(uid);
                            nuevoCliente.setNombre(nombre);
                            nuevoCliente.setApellidos(apellidos);
                            nuevoCliente.setEmail(email);
                            nuevoCliente.setPassword(password);
                            nuevoCliente.setRol(Rol.CLIENTE);
                            nuevoCliente.setFoto("");
                            nuevoCliente.setDni(dni);
                            nuevoCliente.setDireccion(direccion);
                            nuevoCliente.setFecha_alta(System.currentTimeMillis());
                            nuevoCliente.setIs_active(true);

                            clienteService.insertCliente(nuevoCliente);
                            onRegistroExitoso();

                        } else {

                            /*Gestionamos los errores específicos de Firebase Auth*/
                            gestionarErrorFirebase(task.getException());
                            resetLoading();

                        }
                    });
        });
    }

    /**
     * Valida todos los campos del formulario replicando la lógica de los validators de Angular.
     * Marca con error los campos inválidos directamente en el TextInputLayout correspondiente.
     *
     * @return true si el formulario es válido, false en caso contrario
     */
    private boolean validarFormulario(String nombre, String apellidos, String email,
                                      String password, String confirmPassword,
                                      String dni, String direccion) {
        boolean valido = true;

        /*Validación de nombre: campo obligatorio*/
        if (nombre.isEmpty()) {
            tilNombre.setError("El nombre es obligatorio");
            valido = false;
        }

        /*Validación de apellidos: campo obligatorio*/
        if (apellidos.isEmpty()) {
            tilApellidos.setError("Los apellidos son obligatorios");
            valido = false;
        }

        /*Validación de email: obligatorio y formato correcto (replica customEmailValidator)*/
        if (email.isEmpty()) {
            tilEmail.setError("El correo electrónico es obligatorio");
            valido = false;
        } else if (!validarEmail(email)) {
            tilEmail.setError("El formato del correo electrónico no es válido");
            valido = false;
        }

        /*Validación de contraseña: obligatoria y con requisitos (replica customPasswordValidator)*/
        if (password.isEmpty()) {
            tilPassword.setError("La contraseña es obligatoria");
            valido = false;
        } else {
            String errorPassword = validarPassword(password);
            if (errorPassword != null) {
                tilPassword.setError(errorPassword);
                valido = false;
            }
        }

        /*Validación de confirmación de contraseña: obligatoria y debe coincidir (replica passwordsMatchValidator)*/
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError("Debes confirmar la contraseña");
            valido = false;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Las contraseñas no coinciden");
            valido = false;
        }

        /*Validación de DNI: obligatorio y formato español válido (replica customDniValidator)*/
        if (dni.isEmpty()) {
            tilDni.setError("El DNI es obligatorio");
            valido = false;
        } else if (!validarDni(dni)) {
            tilDni.setError("El formato del DNI no es válido (ej: 12345678A)");
            valido = false;
        }

        /*Validación de dirección: campo obligatorio*/
        if (direccion.isEmpty()) {
            tilDireccion.setError("La dirección es obligatoria");
            valido = false;
        }

        return valido;
    }

    /**
     * Valida el formato del email mediante expresión regular.
     * Replica el comportamiento del customEmailValidator de Angular.
     *
     * @param email Email a validar
     * @return true si el formato es válido
     */
    private boolean validarEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /**
     * Valida que la contraseña cumpla todos los requisitos de seguridad.
     * Replica el comportamiento del customPasswordValidator de Angular:
     * mínimo 9 caracteres, 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial.
     *
     * @param password Contraseña a validar
     * @return null si es válida, o un String con el mensaje de error
     */
    private String validarPassword(String password) {

        StringBuilder errores = new StringBuilder();

        if (password.length() < 9) {
            errores.append("mínimo 9 caracteres, ");
        }
        if (!password.matches(".*[A-Z].*")) {
            errores.append("1 mayúscula, ");
        }
        if (!password.matches(".*[a-z].*")) {
            errores.append("1 minúscula, ");
        }
        if (!password.matches(".*\\d.*")) {
            errores.append("1 número, ");
        }
        if (!password.matches(".*[^A-Za-z\\d].*")) {
            errores.append("1 carácter especial, ");
        }

        if (errores.length() > 0) {
            String mensaje = errores.toString();
            return "La contraseña necesita: " + mensaje.substring(0, mensaje.length() - 2);
        }

        return null;
    }

    /**
     * Valida el formato del DNI español: 8 dígitos seguidos de una letra mayúscula.
     * Replica el comportamiento del customDniValidator de Angular.
     *
     * @param dni DNI a validar
     * @return true si el formato es válido
     */
    private boolean validarDni(String dni) {
        return dni.matches("^\\d{8}[A-Z]$");
    }

    /**
     * Gestiona los errores específicos devueltos por Firebase Auth
     * replicando el bloque error del subscribe de Angular.
     */
    private void gestionarErrorFirebase(Exception exception) {

        String mensaje = "Error al registrar el usuario. Inténtalo de nuevo";

        if (exception != null) {
            String errorCode = exception.getMessage();
            if (errorCode != null && errorCode.contains("email-already-in-use")) {
                tilEmail.setError("El correo electrónico ya está registrado");
                mensaje = "El correo electrónico ya está registrado";
            } else if (errorCode != null && errorCode.contains("invalid-email")) {
                tilEmail.setError("El correo electrónico no es válido");
                mensaje = "El correo electrónico no es válido";
            }
        }

        showCenteredSnackbar(mensaje);
    }

    /**
     * Limpia los errores de todos los TextInputLayout del formulario
     */
    private void limpiarErrores() {
        tilNombre.setError(null);
        tilApellidos.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tilDni.setError(null);
        tilDireccion.setError(null);
    }

    /**
     * Restaura el estado del botón y la variable isLoading tras un error
     */
    private void resetLoading() {
        isLoading = false;
        btnRegister.setEnabled(true);
    }

    /**
     * Navega a la pantalla de Login y cierra esta Activity
     */
    private void navigateToLogin() {
        startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
        finish();
    }

    /**
     * Callback ejecutado tras un registro exitoso: muestra confirmación y navega al Home
     */
    private void onRegistroExitoso() {
        showCenteredSnackbar("¡Cuenta de Cliente creada con éxito! Bienvenido");
        startActivity(new Intent(SignUpActivity.this, MainActivity.class));
        finish();
    }
}