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
import androidx.fragment.app.FragmentManager;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Especialidad;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Administrador;
import com.example.sportiva_booking_android.v2.models.Profesional;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class UserManagementFragment extends Fragment {

    /*Clave del Bundle para recibir el rol desde MainActivity*/
    private static final String ARG_ROL = "ROL";

    /*Nombre de la instancia secundaria de FirebaseApp usada exclusivamente para crear cuentas
      sin alterar la sesión activa del usuario logueado*/
    private static final String FIREBASE_APP_SECUNDARIA = "auth_secundario";

    /*Componentes de la vista — layouts de estado (loading, sin centro, formulario)*/
    private View layoutLoading;
    private View layoutSinCentro;
    private View layoutFormulario;

    /*Componentes de la vista — formulario*/
    private TextView tvTitulo;
    private TextInputLayout tilNombre, tilApellidos, tilEmail;
    private TextInputLayout tilPassword, tilConfirmPassword;
    private TextInputLayout tilDescripcion, tilAnnosExp, tilEspecialidad;
    private TextInputEditText etNombre, etApellidos, etEmail;
    private TextInputEditText etPassword, etConfirmPassword;
    private TextInputEditText etDescripcion, etAnnosExp;
    private AutoCompleteTextView actvEspecialidad;
    private View seccionProfesional;
    private Button btnConfirmar, btnCancelar, btnVolveraHomeUM;

    /*Firebase — instancia principal que mantiene la sesión del usuario logueado*/
    private FirebaseAuth firebaseAuth;

    /*Firebase — instancia secundaria usada únicamente para crear nuevas cuentas.
      Al operar sobre una FirebaseApp distinta, el SDK no toca la sesión principal
      y el usuario logueado permanece inalterado durante el proceso de alta*/
    private FirebaseAuth firebaseAuthSecundario;

    private DatabaseReference personsRef;

    /*Servicios*/
    private SportCentreService sportCentreService;

    /*Estado interno del Fragment*/
    private Rol rolUsuarioLogueado;
    private String centroIdActual = null;
    private boolean isLoading = false;

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
        firebaseAuth = FirebaseAuth.getInstance();
        personsRef = FirebaseDatabase.getInstance().getReference("Persons");
        sportCentreService = new SportCentreService();

        /*Inicializamos la instancia secundaria de Firebase para crear cuentas sin cambiar sesión*/
        inicializarFirebaseSecundario();

        inicializarVistas(view);
        configurarSegunRol();

        /*Solo el ADMINISTRADOR necesita verificar que tiene centro antes de operar.
          El ROOT ve el formulario directamente sin pasar por la verificación*/
        if (rolUsuarioLogueado == Rol.ADMINISTRADOR) {
            verificarCentroDeportivo();
        } else {
            mostrarFormulario();
        }

        btnConfirmar.setOnClickListener(v -> crearUsuario());
        btnCancelar.setOnClickListener(v -> volverAtras());
        btnVolveraHomeUM.setOnClickListener(v -> finalizarYNavegarAlHome());
    }

    /**
     * Inicializa la instancia secundaria de FirebaseApp usada para crear cuentas.
     * Si ya existe de una navegación anterior al fragment, la reutilizamos
     * en lugar de inicializarla de nuevo para evitar IllegalStateException.
     */
    private void inicializarFirebaseSecundario() {
        FirebaseApp appSecundaria;
        try {
            appSecundaria = FirebaseApp.initializeApp(
                    requireContext(),
                    FirebaseApp.getInstance().getOptions(),
                    FIREBASE_APP_SECUNDARIA
            );
        } catch (IllegalStateException e) {
            /*La app secundaria ya fue inicializada en una sesión anterior — la reutilizamos*/
            appSecundaria = FirebaseApp.getInstance(FIREBASE_APP_SECUNDARIA);
        }
        firebaseAuthSecundario = FirebaseAuth.getInstance(appSecundaria);
    }

    /**
     * Recupera el rol del Bundle de argumentos.
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
     * Enlaza todas las vistas del layout con sus variables.
     */
    private void inicializarVistas(View view) {

        /*Layouts de estado*/
        layoutLoading = view.findViewById(R.id.layoutLoadingUM);
        layoutSinCentro = view.findViewById(R.id.layoutSinCentroUM);
        layoutFormulario = view.findViewById(R.id.layoutFormularioUM);

        tvTitulo = view.findViewById(R.id.tvTituloUM);
        tilNombre = view.findViewById(R.id.tilNombreUM);
        tilApellidos = view.findViewById(R.id.tilApellidosUM);
        tilEmail = view.findViewById(R.id.tilEmailUM);
        tilPassword = view.findViewById(R.id.tilPasswordUM);
        tilConfirmPassword = view.findViewById(R.id.tilConfirmPasswordUM);
        tilDescripcion = view.findViewById(R.id.tilDescripcionUM);
        tilAnnosExp = view.findViewById(R.id.tilAnnosExpUM);
        tilEspecialidad = view.findViewById(R.id.tilEspecialidadUM);
        etNombre = view.findViewById(R.id.etNombreUM);
        etApellidos = view.findViewById(R.id.etApellidosUM);
        etEmail = view.findViewById(R.id.etEmailUM);
        etPassword = view.findViewById(R.id.etPasswordUM);
        etConfirmPassword = view.findViewById(R.id.etConfirmPasswordUM);
        etDescripcion = view.findViewById(R.id.etDescripcionUM);
        etAnnosExp = view.findViewById(R.id.etAnnosExpUM);
        actvEspecialidad = view.findViewById(R.id.actvEspecialidadUM);
        seccionProfesional = view.findViewById(R.id.seccionProfesional);
        btnConfirmar = view.findViewById(R.id.btnConfirmarUM);
        btnCancelar = view.findViewById(R.id.btnCancelarUM);
        btnVolveraHomeUM = view.findViewById(R.id.btnVolverHomeUM);

        cargarEspecialidades();
    }

    private void configurarSegunRol() {
        if (rolUsuarioLogueado == Rol.ROOT) {
            tvTitulo.setText("Alta de Administradores");
            seccionProfesional.setVisibility(View.GONE);
        } else {
            tvTitulo.setText("Alta de Profesionales");
            seccionProfesional.setVisibility(View.VISIBLE);
        }
    }

    private void cargarEspecialidades() {
        Especialidad[] valores = Especialidad.values();
        String[] nombres = new String[valores.length];
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
     * Verifica si el administrador actual tiene un centro deportivo registrado.
     * Replica el comportamiento de Angular: muestra loading mientras comprueba,
     * luego muestra el formulario si hay centro o el empty state si no lo hay.
     */
    private void verificarCentroDeportivo() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;

        /*Mostramos el spinner mientras se resuelve la consulta a Firebase*/
        mostrarLoading();

        sportCentreService.getSportCentreByAdminUid(user.getUid(), centro -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (centro != null) {
                    centroIdActual = centro.getId();
                    /*Centro encontrado — mostramos el formulario igual que en Angular*/
                    mostrarFormulario();
                } else {
                    /*Sin centro registrado — mostramos el empty state en lugar del formulario*/
                    mostrarSinCentro();
                }
            });
        });
    }

    /**
     * Oculta loading y sin-centro; muestra únicamente el formulario.
     */
    private void mostrarFormulario() {
        layoutLoading.setVisibility(View.GONE);
        layoutSinCentro.setVisibility(View.GONE);
        layoutFormulario.setVisibility(View.VISIBLE);
    }

    /**
     * Muestra el spinner de carga; oculta el resto de estados.
     */
    private void mostrarLoading() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutSinCentro.setVisibility(View.GONE);
        layoutFormulario.setVisibility(View.GONE);
    }

    /**
     * Muestra el empty state de centro no registrado; oculta el resto de estados.
     * Mismo comportamiento que el bloque @if (!cargandoCentro && !centroIdActual) en Angular.
     */
    private void mostrarSinCentro() {
        layoutLoading.setVisibility(View.GONE);
        layoutSinCentro.setVisibility(View.VISIBLE);
        layoutFormulario.setVisibility(View.GONE);
    }

    private void crearUsuario() {
        if (isLoading) return;

        String nombre = etNombre.getText().toString().trim();
        String apellidos = etApellidos.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        limpiarErrores();

        /* Ejecutamos ambas validaciones para que los errores se marquen simultáneamente */
        boolean baseValida = validarFormularioBase(nombre, apellidos, email, password, confirmPassword);
        boolean proValido = true;

        if (rolUsuarioLogueado == Rol.ADMINISTRADOR) {
            proValido = validarCamposProfesional();

            if (centroIdActual == null) {
                showCenteredSnackbar("Debes registrar tu Centro Deportivo antes de dar de alta profesionales");
                return;
            }
        }

        /* Si cualquiera de las validaciones falla, detenemos el proceso tras haber marcado los errores */
        if (!baseValida || !proValido) {
            showCenteredSnackbar("Revisa los errores en el formulario antes de continuar");
            return;
        }

        isLoading = true;
        btnConfirmar.setEnabled(false);

        String adminIdActual = firebaseAuth.getCurrentUser() != null
                ? firebaseAuth.getCurrentUser().getUid()
                : null;

        /*Usamos la instancia secundaria para que el SDK no cambie la sesión activa al crear
          la nueva cuenta — firebaseAuth (instancia principal) permanece inalterada*/
        firebaseAuthSecundario.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        /*Obtenemos el UID del recién creado desde la instancia secundaria*/
                        String uid = firebaseAuthSecundario.getCurrentUser().getUid();

                        /*Cerramos la sesión de la cuenta recién creada en la instancia secundaria
                          para mantenerla limpia de cara a futuros altas en la misma sesión*/
                        firebaseAuthSecundario.signOut();

                        if (rolUsuarioLogueado == Rol.ROOT) {
                            guardarAdministrador(uid, nombre, apellidos);
                        } else {
                            String descripcion = etDescripcion.getText().toString().trim();
                            String annosExpStr = etAnnosExp.getText().toString().trim();
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

    private void guardarAdministrador(String uid, String nombre, String apellidos) {
        Administrador nuevoAdmin = new Administrador();
        nuevoAdmin.setNombre(nombre);
        nuevoAdmin.setApellidos(apellidos);
        nuevoAdmin.setFoto("");
        nuevoAdmin.setRol(Rol.ADMINISTRADOR);

        personsRef.child(uid).setValue(nuevoAdmin)
                .addOnSuccessListener(unused -> {
                    showCenteredSnackbar("Administrador registrado con éxito");
                    finalizarYNavegarAlHome();
                })
                .addOnFailureListener(e -> {
                    showCenteredSnackbar("Error al guardar los datos del administrador");
                    resetLoading();
                });
    }

    /**
     * Construye el objeto Profesional y lo persiste en Firebase.
     * Vincula directamente adminId y centroId en el momento del alta,
     * replicando el comportamiento del controlador Angular donde ambos campos
     * se asignan antes de llamar a saveProfesional().
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

        /*Vinculamos al centro del administrador en el momento del alta —
          mismo comportamiento que centroId: this.centroIdActual en Angular*/
        nuevoPro.setAdminId(adminId);
        nuevoPro.setCentroId(centroIdActual);

        personsRef.child(uid).setValue(nuevoPro)
                .addOnSuccessListener(unused -> {
                    showCenteredSnackbar("Profesional registrado y vinculado al centro con éxito");
                    finalizarYNavegarAlHome();
                })
                .addOnFailureListener(e -> {
                    showCenteredSnackbar("Error al guardar los datos del profesional");
                    resetLoading();
                });
    }

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

    private boolean validarCamposProfesional() {
        boolean valido = true;
        String descripcion = etDescripcion.getText().toString().trim();
        String annosExp = etAnnosExp.getText().toString().trim();
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

    private String validarPassword(String password) {
        StringBuilder errores = new StringBuilder();

        if (password.length() < 9) errores.append("mínimo 9 caracteres, ");
        if (!password.matches(".*[A-Z].*")) errores.append("1 mayúscula, ");
        if (!password.matches(".*[a-z].*")) errores.append("1 minúscula, ");
        if (!password.matches(".*\\d.*")) errores.append("1 número, ");
        if (!password.matches(".*[^A-Za-z\\d].*")) errores.append("1 carácter especial, ");

        if (errores.length() > 0) {
            String msg = errores.toString();
            return "La contraseña necesita: " + msg.substring(0, msg.length() - 2);
        }

        return null;
    }

    private void gestionarErrorFirebase(Exception exception) {
        String mensaje = "Error en el registro. Inténtalo de nuevo";

        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException authEx = (FirebaseAuthException) exception;
            String codigo = authEx.getErrorCode();

            switch (codigo) {
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    tilEmail.setError("El correo electrónico ya está registrado");
                    mensaje = "El correo electrónico ya está registrado. Prueba con una dirección diferente";
                    break;
                case "ERROR_INVALID_EMAIL":
                    tilEmail.setError("El correo electrónico no es válido");
                    mensaje = "El correo electrónico no es válido";
                    break;

                default:
                /*Fallback con el código técnico por si aparece alguno no contemplado —
                  útil para detectar casos nuevos durante el desarrollo*/
                    mensaje = "Error en el registro (" + codigo + "). Inténtalo de nuevo";
                    break;
            }
        }

        showCenteredSnackbar(mensaje);
    }

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

    private void resetLoading() {
        isLoading = false;
        btnConfirmar.setEnabled(true);
    }

    /**
     * Resetea el estado de carga y navega al HomeFragment.
     * Se usa tras un alta exitosa para asegurar que la vista principal
     * refleje los nuevos cambios.
     */
    private void finalizarYNavegarAlHome() {
        isLoading = false;

        /*Limpiamos el backstack para que el usuario no pueda volver al formulario al darle atrás*/
        requireActivity().getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        /*Reemplazamos el fragment actual por una nueva instancia del HomeFragment*/
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, HomeFragment.newInstance(rolUsuarioLogueado))
                .commit();
    }

    /**
     * Vuelve al fragment anterior en la pila.
     * Se usa habitualmente para el botón cancelar.
     */
    private void volverAtras() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }

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