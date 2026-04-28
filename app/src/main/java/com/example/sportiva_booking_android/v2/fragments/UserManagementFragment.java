package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Especialidad;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Administrador;
import com.example.sportiva_booking_android.v2.models.Profesional;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class UserManagementFragment extends Fragment {

    /*Clave del Bundle para recibir el rol desde MainActivity*/
    private static final String ARG_ROL = "ROL";

    /*Componentes de la vista*/
    private TextView             tvTitulo;
    private TextInputLayout      tilNombre, tilApellidos, tilEmail;
    private TextInputLayout      tilPassword, tilConfirmPassword;
    private TextInputLayout      tilDescripcion, tilAnnosExp, tilEspecialidad;
    private TextInputEditText    etNombre, etApellidos, etEmail;
    private TextInputEditText    etPassword, etConfirmPassword;
    private TextInputEditText    etDescripcion, etAnnosExp;
    private AutoCompleteTextView actvEspecialidad;
    private View                 seccionProfesional;
    private Button               btnConfirmar, btnCancelar;

    /*Firebase*/
    private FirebaseAuth      firebaseAuth;
    private DatabaseReference personsRef;

    /*Servicios*/
    private SportCentreService sportCentreService;

    /*Estado interno del Fragment*/
    private Rol     rolUsuarioLogueado;
    private String  centroIdActual = null;
    private boolean isLoading      = false;

    /**
     * Crea una nueva instancia del Fragment pasando el rol por Bundle.
     * Mismo patrón que HomeFragment.newInstance() ya existente en el proyecto.
     *
     * @param rol Rol del usuario autenticado (ROOT o ADMINISTRADOR)
     * @return Instancia configurada del Fragment
     */
    public static UserManagementFragment newInstance(Rol rol) {
        UserManagementFragment fragment = new UserManagementFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL, rol.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recuperarRol();

        /*Inicializamos Firebase*/
        firebaseAuth       = FirebaseAuth.getInstance();
        personsRef         = FirebaseDatabase.getInstance().getReference("Persons");
        sportCentreService = new SportCentreService();

        inicializarVistas(view);
        configurarSegunRol();

        /*Solo el ADMINISTRADOR necesita verificar que tiene centro antes de operar*/
        if (rolUsuarioLogueado == Rol.ADMINISTRADOR) {
            verificarCentroDeportivo();
        }

        btnConfirmar.setOnClickListener(v -> crearUsuario());
        btnCancelar.setOnClickListener(v -> volverAtras());
    }

    /**
     * Recupera el rol del Bundle de argumentos.
     * Si no viene o falla el parse, asignamos ROOT como fallback ya que este
     * Fragment solo lo usan roles de gestión.
     */
    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuarioLogueado = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.ROOT.name())
                );
            } catch (IllegalArgumentException e) {
                rolUsuarioLogueado = Rol.ROOT;
            }
        } else {
            rolUsuarioLogueado = Rol.ROOT;
        }
    }

    /**
     * Enlaza todas las vistas del layout con sus variables
     * y carga el dropdown de especialidades.
     *
     * @param view Vista raíz inflada del Fragment
     */
    private void inicializarVistas(View view) {
        tvTitulo           = view.findViewById(R.id.tvTituloUM);
        tilNombre          = view.findViewById(R.id.tilNombreUM);
        tilApellidos       = view.findViewById(R.id.tilApellidosUM);
        tilEmail           = view.findViewById(R.id.tilEmailUM);
        tilPassword        = view.findViewById(R.id.tilPasswordUM);
        tilConfirmPassword = view.findViewById(R.id.tilConfirmPasswordUM);
        tilDescripcion     = view.findViewById(R.id.tilDescripcionUM);
        tilAnnosExp        = view.findViewById(R.id.tilAnnosExpUM);
        tilEspecialidad    = view.findViewById(R.id.tilEspecialidadUM);
        etNombre           = view.findViewById(R.id.etNombreUM);
        etApellidos        = view.findViewById(R.id.etApellidosUM);
        etEmail            = view.findViewById(R.id.etEmailUM);
        etPassword         = view.findViewById(R.id.etPasswordUM);
        etConfirmPassword  = view.findViewById(R.id.etConfirmPasswordUM);
        etDescripcion      = view.findViewById(R.id.etDescripcionUM);
        etAnnosExp         = view.findViewById(R.id.etAnnosExpUM);
        actvEspecialidad   = view.findViewById(R.id.actvEspecialidadUM);
        seccionProfesional = view.findViewById(R.id.seccionProfesional);
        btnConfirmar       = view.findViewById(R.id.btnConfirmarUM);
        btnCancelar        = view.findViewById(R.id.btnCancelarUM);

        cargarEspecialidades();
    }

    /**
     * Ajusta el título y muestra u oculta la sección profesional según el rol.
     * ROOT crea Administradores, ADMINISTRADOR crea Profesionales.
     */
    private void configurarSegunRol() {
        if (rolUsuarioLogueado == Rol.ROOT) {
            tvTitulo.setText("Alta de Administradores");
            seccionProfesional.setVisibility(View.GONE);
        } else {
            tvTitulo.setText("Alta de Profesionales");
            seccionProfesional.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Carga los valores del enum Especialidad en el AutoCompleteTextView.
     * Equivalente al @for (esp of especialidades) del template Angular.
     */
    private void cargarEspecialidades() {
        Especialidad[] valores = Especialidad.values();
        String[]       nombres = new String[valores.length];
        for (int i = 0; i < valores.length; i++) {
            nombres[i] = valores[i].name();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                nombres
        );
        actvEspecialidad.setAdapter(adapter);
    }

    /**
     * Verifica si el administrador autenticado tiene centro deportivo registrado.
     * Bloquea el botón de confirmar hasta que se resuelva, igual que en Angular.
     */
    private void verificarCentroDeportivo() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;

        btnConfirmar.setEnabled(false);

        sportCentreService.getSportCentreByAdminUid(user.getUid(), centro -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (centro != null) {
                    centroIdActual = centro.getId();
                    btnConfirmar.setEnabled(true);
                } else {
                    showCenteredSnackbar("Debes registrar tu Centro Deportivo antes de dar de alta profesionales");
                }
            });
        });
    }

    /**
     * Método principal que gestiona el alta de un Administrador (ROOT) o un Profesional (ADMINISTRADOR).
     * Valida el formulario, crea la cuenta en Firebase Auth y guarda los datos en /Persons.
     * Replica la lógica del createUser() del componente Angular.
     */
    private void crearUsuario() {
        if (isLoading) return;

        String nombre          = etNombre.getText().toString().trim();
        String apellidos       = etApellidos.getText().toString().trim();
        String email           = etEmail.getText().toString().trim();
        String password        = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        limpiarErrores();

        if (!validarFormularioBase(nombre, apellidos, email, password, confirmPassword)) {
            showCenteredSnackbar("Revisa los errores en el formulario antes de continuar");
            return;
        }

        if (rolUsuarioLogueado == Rol.ADMINISTRADOR) {
            if (!validarCamposProfesional()) {
                showCenteredSnackbar("Revisa los errores en el formulario antes de continuar");
                return;
            }
            if (centroIdActual == null) {
                showCenteredSnackbar("Debes registrar tu Centro Deportivo antes de dar de alta profesionales");
                return;
            }
        }

        isLoading = true;
        btnConfirmar.setEnabled(false);

        /*Guardamos el UID del admin antes de crear la cuenta nueva porque Firebase
        cambia el currentUser en cuanto crea otro usuario con createUserWithEmailAndPassword*/
        String adminIdActual = firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid()
                : null;

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = firebaseAuth.getCurrentUser().getUid();

                        if (rolUsuarioLogueado == Rol.ROOT) {
                            guardarAdministrador(uid, nombre, apellidos);
                        } else {
                            String descripcion  = etDescripcion.getText().toString().trim();
                            String annosExpStr  = etAnnosExp.getText().toString().trim();
                            String especialidad = actvEspecialidad.getText().toString().trim();
                            guardarProfesional(uid, nombre, apellidos, descripcion,
                                    Integer.parseInt(annosExpStr), especialidad, adminIdActual);
                        }
                    } else {
                        gestionarErrorFirebase(task.getException());
                        resetLoading();
                    }
                });
    }

    /**
     * Guarda los datos del nuevo Administrador en /Persons bajo su UID.
     * Usa setters en lugar de constructor con parámetros, siguiendo tu patrón de modelos.
     *
     * @param uid       UID generado por Firebase Auth
     * @param nombre    Nombre introducido en el formulario
     * @param apellidos Apellidos introducidos en el formulario
     */
    private void guardarAdministrador(String uid, String nombre, String apellidos) {
        Administrador nuevoAdmin = new Administrador();
        nuevoAdmin.setNombre(nombre);
        nuevoAdmin.setApellidos(apellidos);
        nuevoAdmin.setFoto("");
        nuevoAdmin.setRol(Rol.ADMINISTRADOR);

        personsRef.child(uid).setValue(nuevoAdmin)
                .addOnSuccessListener(unused -> {
                    showCenteredSnackbar("Administrador registrado con éxito");
                    finalizarYVolver();
                })
                .addOnFailureListener(e -> {
                    showCenteredSnackbar("Error al guardar los datos del administrador");
                    resetLoading();
                });
    }

    /**
     * Guarda los datos del nuevo Profesional en /Persons bajo su UID,
     * vinculándolo directamente al centro del administrador que lo da de alta.
     * Usa setters siguiendo tu patrón de modelos con herencia.
     *
     * @param uid         UID generado por Firebase Auth
     * @param nombre      Nombre introducido en el formulario
     * @param apellidos   Apellidos introducidos en el formulario
     * @param descripcion Biografía breve del profesional
     * @param annosExp    Años de experiencia
     * @param especialidad Especialidad seleccionada del enum
     * @param adminId     UID del administrador que realiza el alta
     */
    private void guardarProfesional(String uid, String nombre, String apellidos,
                                    String descripcion, int annosExp,
                                    String especialidad, String adminId) {
        Profesional nuevoPro = new Profesional();
        nuevoPro.setNombre(nombre);
        nuevoPro.setApellidos(apellidos);
        nuevoPro.setFoto("");
        nuevoPro.setRol(Rol.PROFESIONAL);
        nuevoPro.setDescripcion(descripcion);
        nuevoPro.setAnnos_experiencia(annosExp);
        nuevoPro.setEspecialidad(Especialidad.valueOf(especialidad));

        personsRef.child(uid).setValue(nuevoPro)
                .addOnSuccessListener(unused -> {
                    showCenteredSnackbar("Profesional registrado y vinculado al centro con éxito");
                    finalizarYVolver();
                })
                .addOnFailureListener(e -> {
                    showCenteredSnackbar("Error al guardar los datos del profesional");
                    resetLoading();
                });
    }

    /**
     * Valida los campos comunes a ambos roles: nombre, apellidos, email,
     * contraseña y confirmación. Mismo patrón que validarFormulario() en SignUpActivity.
     *
     * @return true si todos los campos base son válidos
     */
    private boolean validarFormularioBase(String nombre, String apellidos, String email,
                                          String password, String confirmPassword) {
        boolean valido = true;

        if (nombre.isEmpty()) {
            tilNombre.setError("El nombre es obligatorio");
            valido = false;
        }

        if (apellidos.isEmpty()) {
            tilApellidos.setError("Los apellidos son obligatorios");
            valido = false;
        }

        if (email.isEmpty()) {
            tilEmail.setError("El correo electrónico es obligatorio");
            valido = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("El formato del correo electrónico no es válido");
            valido = false;
        }

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

        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError("Debes confirmar la contraseña");
            valido = false;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Las contraseñas no coinciden");
            valido = false;
        }

        return valido;
    }

    /**
     * Valida los campos exclusivos del perfil profesional.
     * Solo se llama cuando el rol es ADMINISTRADOR.
     *
     * @return true si descripción, años de experiencia y especialidad son válidos
     */
    private boolean validarCamposProfesional() {
        boolean valido      = true;
        String descripcion  = etDescripcion.getText().toString().trim();
        String annosExp     = etAnnosExp.getText().toString().trim();
        String especialidad = actvEspecialidad.getText().toString().trim();

        if (descripcion.isEmpty()) {
            tilDescripcion.setError("La descripción es obligatoria");
            valido = false;
        }

        if (annosExp.isEmpty()) {
            tilAnnosExp.setError("Los años de experiencia son obligatorios");
            valido = false;
        }

        if (especialidad.isEmpty()) {
            tilEspecialidad.setError("La especialidad es obligatoria");
            valido = false;
        }

        return valido;
    }

    /**
     * Valida que la contraseña cumpla los requisitos de seguridad.
     * Mínimo 9 caracteres, 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial.
     * Mismo patrón que validarPassword() en SignUpActivity.
     *
     * @param password Contraseña a validar
     * @return null si es válida, o el mensaje de error en caso contrario
     */
    private String validarPassword(String password) {
        StringBuilder errores = new StringBuilder();

        if (password.length() < 9)                 errores.append("mínimo 9 caracteres, ");
        if (!password.matches(".*[A-Z].*"))         errores.append("1 mayúscula, ");
        if (!password.matches(".*[a-z].*"))         errores.append("1 minúscula, ");
        if (!password.matches(".*\\d.*"))           errores.append("1 número, ");
        if (!password.matches(".*[^A-Za-z\\d].*")) errores.append("1 carácter especial, ");

        if (errores.length() > 0) {
            String msg = errores.toString();
            return "La contraseña necesita: " + msg.substring(0, msg.length() - 2);
        }

        return null;
    }

    /**
     * Gestiona los errores específicos de Firebase Auth.
     * Mismo patrón que gestionarErrorFirebase() en SignUpActivity.
     */
    private void gestionarErrorFirebase(Exception exception) {
        String mensaje = "Error en el registro. Inténtalo de nuevo";

        if (exception != null && exception.getMessage() != null) {
            if (exception.getMessage().contains("email-already-in-use")) {
                tilEmail.setError("El correo electrónico ya está registrado");
                mensaje = "El correo electrónico ya está registrado. Prueba con una dirección diferente";
            } else if (exception.getMessage().contains("invalid-email")) {
                tilEmail.setError("El correo electrónico no es válido");
                mensaje = "El correo electrónico no es válido";
            }
        }

        showCenteredSnackbar(mensaje);
    }

    /*Limpia los errores de todos los campos del formulario antes de revalidar*/
    private void limpiarErrores() {
        tilNombre.setError(null);
        tilApellidos.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tilDescripcion.setError(null);
        tilAnnosExp.setError(null);
        tilEspecialidad.setError(null);
    }

    /*Restaura el estado del botón y la variable isLoading tras un error*/
    private void resetLoading() {
        isLoading = false;
        btnConfirmar.setEnabled(true);
    }

    /*Resetea el estado de carga y vuelve al Fragment anterior*/
    private void finalizarYVolver() {
        isLoading = false;
        volverAtras();
    }

    /*Vuelve al HomeFragment usando la back stack, equivalente a router.navigate(['/home']) en Angular*/
    private void volverAtras() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    /**
     * Método utilitario para mostrar Snackbars centrados.
     * Mismo patrón que en MainActivity y SignUpActivity.
     */
    private void showCenteredSnackbar(String message) {
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        TextView textView = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        snackbar.show();
    }
}