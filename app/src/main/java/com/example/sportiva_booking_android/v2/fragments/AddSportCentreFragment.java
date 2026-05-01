package com.example.sportiva_booking_android.v2.fragments;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.SportCentre;
import com.example.sportiva_booking_android.v2.models.SportCentre.HorarioDia;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AddSportCentreFragment extends Fragment {

    /*Clave para indicar si venimos en modo edición*/
    private static final String ARG_MODO_EDICION = "modoEdicion";

    /*Días de la semana — mismo orden que en Angular*/
    private static final String[] DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    /*Modo edición o creación*/
    private boolean modoEdicion = false;

    /*UID del administrador autenticado*/
    private String adminUid;

    /*Centro cargado en modo edición (null en creación)*/
    private SportCentre centroOriginal;

    /*Control de carga*/
    private boolean isLoading        = false;
    private boolean isInitialLoading = false;

    /*Imagen seleccionada pendiente de subir*/
    private Uri    imagenSeleccionada = null;
    private String urlImagenOriginal  = null;

    /*Servicios y Firebase*/
    private SportCentreService sportCentreService;
    private FirebaseStorage    firebaseStorage;

    /*--- Vistas globales ---*/
    private View        layoutCargando;
    private View        layoutContenido;
    private ProgressBar pbGuardando;

    /*--- Campos del formulario ---*/
    private TextInputLayout   tilNombre, tilDireccion, tilTelefono;
    private TextInputEditText etNombre, etDireccion, etTelefono;

    /*--- Imagen ---*/
    private ImageView ivFotoCentro;
    private Button    btnCambiarFoto, btnEliminarFoto;

    /*--- Botones de acción ---*/
    private Button btnGuardar, btnCancelar;

    /*--- Horario: mapa de vistas por día ---*/
    private final Map<String, Switch>   switchesDia    = new HashMap<>();
    private final Map<String, TextView> tvAperturasDia = new HashMap<>();
    private final Map<String, TextView> tvCierresDia   = new HashMap<>();
    private final Map<String, View>     rowsHorarioDia = new HashMap<>();

    /*Launcher para el selector de imagen*/
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    /*----------------------------------------------------------------------
     * Factory method
     *----------------------------------------------------------------------*/

    /**
     * Crea el fragment pasando si viene en modo edición.
     * @param modoEdicion true si el admin ya tiene centro y quiere editarlo
     */
    public static AddSportCentreFragment newInstance(boolean modoEdicion) {
        AddSportCentreFragment fragment = new AddSportCentreFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_MODO_EDICION, modoEdicion);
        fragment.setArguments(args);
        return fragment;
    }

    /*----------------------------------------------------------------------
     * Ciclo de vida
     *----------------------------------------------------------------------*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            modoEdicion = getArguments().getBoolean(ARG_MODO_EDICION, false);
        }

        adminUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        sportCentreService = new SportCentreService();
        firebaseStorage    = FirebaseStorage.getInstance();

        /*Registramos el launcher antes de adjuntarnos a la Activity*/
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) onImagenSeleccionada(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_sport_centre, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        configurarListeners();

        if (modoEdicion) {
            mostrarCargando(true);
            cargarDatosParaEdicion();
        } else {
            inicializarHorarioPorDefecto();
            mostrarCargando(false);
        }
    }

    /*----------------------------------------------------------------------
     * Binding de vistas
     *----------------------------------------------------------------------*/

    /**
     * Enlaza todas las vistas del layout con sus referencias Java.
     * @param view Vista raíz del fragment
     */
    private void bindViews(View view) {
        layoutCargando  = view.findViewById(R.id.layoutCargandoAddCentre);
        layoutContenido = view.findViewById(R.id.layoutContenidoAddCentre);
        pbGuardando     = view.findViewById(R.id.pbGuardandoAddCentre);

        tilNombre    = view.findViewById(R.id.tilNombreAddCentre);
        tilDireccion = view.findViewById(R.id.tilDireccionAddCentre);
        tilTelefono  = view.findViewById(R.id.tilTelefonoAddCentre);

        etNombre    = view.findViewById(R.id.etNombreAddCentre);
        etDireccion = view.findViewById(R.id.etDireccionAddCentre);
        etTelefono  = view.findViewById(R.id.etTelefonoAddCentre);

        ivFotoCentro    = view.findViewById(R.id.ivFotoAddCentre);
        btnCambiarFoto  = view.findViewById(R.id.btnCambiarFotoAddCentre);
        btnEliminarFoto = view.findViewById(R.id.btnEliminarFotoAddCentre);

        btnGuardar  = view.findViewById(R.id.btnGuardarAddCentre);
        btnCancelar = view.findViewById(R.id.btnCancelarAddCentre);

        /*Enlazamos los controles de horario por cada día*/
        for (String dia : DIAS) {
            String tag = diaTag(dia);
            switchesDia.put(dia,    view.findViewWithTag("switch_"     + tag));
            tvAperturasDia.put(dia, view.findViewWithTag("apertura_"   + tag));
            tvCierresDia.put(dia,   view.findViewWithTag("cierre_"     + tag));
            rowsHorarioDia.put(dia, view.findViewWithTag("row_horario_"+ tag));
        }
    }

    /**
     * Convierte el nombre del día a un tag seguro para usarlo como identificador de vistas.
     * Elimina tildes y espacios para evitar problemas con los tags XML.
     * @param dia Nombre del día (ej: "Miércoles")
     * @return Tag limpio (ej: "miercoles")
     */
    private String diaTag(String dia) {
        return dia.toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e")
                .replace("í", "i").replace("ó", "o")
                .replace("ú", "u").replace("ü", "u")
                .replace(" ", "_");
    }

    /*----------------------------------------------------------------------
     * Listeners
     *----------------------------------------------------------------------*/

    /**
     * Asigna todos los listeners del formulario: botones, switches de horario
     * y pickers de hora para apertura y cierre.
     */
    private void configurarListeners() {

        btnGuardar.setOnClickListener(v  -> guardarCentro());
        btnCancelar.setOnClickListener(v -> navegarAtras());
        btnCambiarFoto.setOnClickListener(v -> abrirGaleria());
        btnEliminarFoto.setOnClickListener(v -> eliminarFoto());

        /*Por cada día configuramos el switch y los pickers de hora*/
        for (String dia : DIAS) {
            Switch   sw     = switchesDia.get(dia);
            TextView tvAp   = tvAperturasDia.get(dia);
            TextView tvCi   = tvCierresDia.get(dia);
            View     rowHor = rowsHorarioDia.get(dia);

            if (sw == null || tvAp == null || tvCi == null || rowHor == null) continue;

            /*Switch: muestra u oculta la fila de horas*/
            sw.setOnCheckedChangeListener((btn, checked) ->
                    rowHor.setVisibility(checked ? View.VISIBLE : View.GONE));

            /*Picker de apertura*/
            tvAp.setOnClickListener(v -> mostrarTimePicker(tvAp));

            /*Picker de cierre*/
            tvCi.setOnClickListener(v -> mostrarTimePicker(tvCi));
        }
    }

    /*----------------------------------------------------------------------
     * Carga inicial en modo edición
     *----------------------------------------------------------------------*/

    /**
     * Carga el centro existente del administrador desde Firebase y rellena el formulario.
     * Replica el bloque ngOnInit en modo edición del componente Angular.
     */
    private void cargarDatosParaEdicion() {
        if (adminUid == null) return;

        sportCentreService.getSportCentreByAdminUid(adminUid, centro -> {
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                if (centro != null) {
                    centroOriginal = centro;
                    rellenarFormulario(centro);
                } else {
                    showSnackbar("No se encontró el centro deportivo");
                }
                mostrarCargando(false);
            });
        });
    }

    /**
     * Rellena todos los campos del formulario con los datos del centro cargado.
     * @param centro Centro deportivo obtenido de Firebase
     */
    private void rellenarFormulario(SportCentre centro) {
        etNombre.setText(centro.getNombre());
        etDireccion.setText(centro.getDireccion());
        etTelefono.setText(centro.getTelefono());

        /*Imagen existente*/
        if (centro.getFoto() != null && !centro.getFoto().isEmpty()) {
            urlImagenOriginal = centro.getFoto();
            Glide.with(this)
                    .load(centro.getFoto())
                    .placeholder(R.drawable.ic_centre_placeholder)
                    .error(R.drawable.ic_centre_placeholder)
                    .centerCrop()
                    .into(ivFotoCentro);
        }

        /*Horario*/
        Map<String, HorarioDia> horario = centro.getHorario();
        if (horario != null) {
            for (String dia : DIAS) {
                HorarioDia hd = horario.get(dia);
                if (hd == null) continue;

                Switch   sw  = switchesDia.get(dia);
                TextView tvA = tvAperturasDia.get(dia);
                TextView tvC = tvCierresDia.get(dia);
                View     row = rowsHorarioDia.get(dia);

                if (sw == null || tvA == null || tvC == null || row == null) continue;

                sw.setChecked(hd.isAbierto());
                row.setVisibility(hd.isAbierto() ? View.VISIBLE : View.GONE);
                tvA.setText(hd.getApertura() != null ? hd.getApertura() : "08:00");
                tvC.setText(hd.getCierre()   != null ? hd.getCierre()   : "22:00");
            }
        } else {
            inicializarHorarioPorDefecto();
        }
    }

    /**
     * Inicializa el horario con los valores por defecto: todos los días abiertos,
     * apertura a las 08:00 y cierre a las 22:00. Se usa en modo creación.
     */
    private void inicializarHorarioPorDefecto() {
        for (String dia : DIAS) {
            Switch   sw  = switchesDia.get(dia);
            TextView tvA = tvAperturasDia.get(dia);
            TextView tvC = tvCierresDia.get(dia);
            View     row = rowsHorarioDia.get(dia);

            if (sw  != null) sw.setChecked(true);
            if (tvA != null) tvA.setText("08:00");
            if (tvC != null) tvC.setText("22:00");
            if (row != null) row.setVisibility(View.VISIBLE);
        }
    }

    /*----------------------------------------------------------------------
     * Gestión de imagen
     *----------------------------------------------------------------------*/

    /**
     * Abre la galería del dispositivo para seleccionar una imagen del centro.
     * Usa ACTION_PICK para evitar que el sistema ofrezca la cámara como opción.
     */
    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    /**
     * Recibe la URI de la imagen seleccionada, la guarda como pendiente de subir
     * y muestra la preview de forma inmediata con Glide.
     * @param uri URI local de la imagen seleccionada por el usuario
     */
    private void onImagenSeleccionada(Uri uri) {
        imagenSeleccionada = uri;
        Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(ivFotoCentro);
    }

    /**
     * Elimina la imagen seleccionada y resetea el ImageView al placeholder.
     * El borrado real en Storage ocurre al guardar, no en este momento.
     * Replica el método eliminarImagen() del componente Angular.
     */
    private void eliminarFoto() {
        imagenSeleccionada = null;
        urlImagenOriginal  = null;
        ivFotoCentro.setImageResource(R.drawable.ic_centre_placeholder);
    }

    /**
     * Sube la nueva imagen a Firebase Storage bajo la ruta Sport-Centre/uuid,
     * elimina la anterior si existía, y delega la persistencia final en guardarDatosFinales().
     * Replica el bloque uploadBytes de Angular.
     * @param nombre    Nombre del centro
     * @param direccion Dirección del centro
     * @param telefono  Teléfono del centro
     */
    private void subirFotoYGuardar(String nombre, String direccion, String telefono) {
        if (imagenSeleccionada == null) return;

        /*Si había foto anterior la eliminamos de Storage antes de subir la nueva*/
        if (urlImagenOriginal != null && !urlImagenOriginal.isEmpty()) {
            try {
                firebaseStorage.getReferenceFromUrl(urlImagenOriginal).delete();
            } catch (Exception ignored) {}
        }

        StorageReference fileRef = firebaseStorage.getReference()
                .child("Sport-Centre/" + UUID.randomUUID());

        fileRef.putFile(imagenSeleccionada)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return fileRef.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    imagenSeleccionada = null;
                    guardarDatosFinales(nombre, direccion, telefono, uri.toString());
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Error al subir la imagen. Inténtalo de nuevo.");
                    setLoading(false);
                });
    }

    /*----------------------------------------------------------------------
     * Guardado
     *----------------------------------------------------------------------*/

    /**
     * Punto de entrada del formulario al pulsar "Guardar".
     * Valida, construye el objeto, gestiona la imagen y persiste en RTDB.
     * Replica el método saveSportCentre() del componente Angular.
     */
    private void guardarCentro() {
        if (isLoading || adminUid == null) return;

        limpiarErrores();

        String nombre    = etNombre.getText()    != null ? etNombre.getText().toString().trim()    : "";
        String direccion = etDireccion.getText() != null ? etDireccion.getText().toString().trim() : "";
        String telefono  = etTelefono.getText()  != null ? etTelefono.getText().toString().trim()  : "";

        if (!validarFormulario(nombre, direccion, telefono)) {
            showSnackbar("Por favor, rellena todos los campos obligatorios para continuar");
            return;
        }

        setLoading(true);

        if (imagenSeleccionada != null) {
            /*Hay imagen nueva: la subimos y luego guardamos*/
            subirFotoYGuardar(nombre, direccion, telefono);

        } else if (urlImagenOriginal == null) {
            /*El usuario eliminó la imagen o no hay foto — guardamos sin foto*/
            if (centroOriginal != null
                    && centroOriginal.getFoto() != null
                    && !centroOriginal.getFoto().isEmpty()) {
                /*Borramos la imagen anterior de Storage*/
                try {
                    firebaseStorage.getReferenceFromUrl(centroOriginal.getFoto()).delete();
                } catch (Exception ignored) {}
            }
            guardarDatosFinales(nombre, direccion, telefono, "");

        } else {
            /*Sin imagen nueva — mantenemos la URL original*/
            guardarDatosFinales(nombre, direccion, telefono, urlImagenOriginal);
        }
    }

    /**
     * Construye el objeto SportCentre, lo persiste en RTDB y navega atrás.
     * Replica el método guardarDatosFinales() del componente Angular.
     * @param nombre    Nombre del centro
     * @param direccion Dirección del centro
     * @param telefono  Teléfono del centro
     * @param foto      URL de la foto (vacío si no tiene)
     */
    private void guardarDatosFinales(String nombre, String direccion,
                                     String telefono, String foto) {
        SportCentre centro = new SportCentre();
        centro.setNombre(nombre);
        centro.setDireccion(direccion);
        centro.setTelefono(telefono);
        centro.setFoto(foto);
        centro.setAdminUid(adminUid);
        centro.setHorario(construirHorario());

        sportCentreService.saveSportCentre(adminUid, centro,
                () -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        showSnackbar(modoEdicion
                                ? "Centro actualizado correctamente"
                                : "Centro creado correctamente");
                        setLoading(false);
                        navegarAtras();
                    });
                },
                e -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        showSnackbar("Error al guardar el centro. Inténtalo de nuevo.");
                        setLoading(false);
                    });
                }
        );
    }

    /**
     * Recorre las vistas de horario y construye el mapa de HorarioDia
     * que se guardará en Firebase. Replica la estructura horario de Angular.
     * @return Mapa con la configuración de horario de cada día de la semana
     */
    private Map<String, HorarioDia> construirHorario() {
        Map<String, HorarioDia> horario = new HashMap<>();

        for (String dia : DIAS) {
            Switch   sw  = switchesDia.get(dia);
            TextView tvA = tvAperturasDia.get(dia);
            TextView tvC = tvCierresDia.get(dia);

            boolean abierto  = sw  != null && sw.isChecked();
            String  apertura = tvA != null ? tvA.getText().toString() : "08:00";
            String  cierre   = tvC != null ? tvC.getText().toString() : "22:00";

            HorarioDia hd = new HorarioDia();
            hd.setAbierto(abierto);
            hd.setApertura(apertura);
            hd.setCierre(cierre);
            horario.put(dia, hd);
        }

        return horario;
    }

    /*----------------------------------------------------------------------
     * Validación
     *----------------------------------------------------------------------*/

    /**
     * Valida que los tres campos obligatorios del formulario no estén vacíos.
     * Marca con error los TextInputLayout correspondientes igual que en SignUpActivity.
     * @return true si el formulario es válido
     */
    private boolean validarFormulario(String nombre, String direccion, String telefono) {
        boolean valido = true;

        if (nombre.isEmpty()) {
            tilNombre.setError("El nombre del centro es obligatorio");
            valido = false;
        }

        if (direccion.isEmpty()) {
            tilDireccion.setError("La dirección es obligatoria");
            valido = false;
        }

        if (telefono.isEmpty()) {
            tilTelefono.setError("El teléfono es obligatorio");
            valido = false;
        }

        return valido;
    }

    /**
     * Limpia los mensajes de error de todos los TextInputLayout del formulario.
     */
    private void limpiarErrores() {
        tilNombre.setError(null);
        tilDireccion.setError(null);
        tilTelefono.setError(null);
    }

    /*----------------------------------------------------------------------
     * Time picker
     *----------------------------------------------------------------------*/

    /**
     * Abre el diálogo nativo de Android para seleccionar una hora.
     * Al confirmar actualiza el TextView con el formato HH:mm.
     * @param target TextView de apertura o cierre que recibirá la hora seleccionada
     */
    private void mostrarTimePicker(TextView target) {
        if (getContext() == null) return;

        /*Parseamos la hora actual del TextView para pre-seleccionarla en el picker*/
        String actual = target.getText().toString();
        int hora   = 8;
        int minuto = 0;
        try {
            String[] partes = actual.split(":");
            hora   = Integer.parseInt(partes[0]);
            minuto = Integer.parseInt(partes[1]);
        } catch (Exception ignored) {}

        new TimePickerDialog(getContext(), (view, h, m) ->
                target.setText(String.format(Locale.ROOT, "%02d:%02d", h, m)),
                hora, minuto, true
        ).show();
    }

    /*----------------------------------------------------------------------
     * UI helpers
     *----------------------------------------------------------------------*/

    /**
     * Controla la visibilidad del spinner de carga inicial y del contenido del formulario.
     * @param cargando true para mostrar el spinner, false para mostrar el formulario
     */
    private void mostrarCargando(boolean cargando) {
        if (layoutCargando == null || layoutContenido == null) return;
        layoutCargando.setVisibility(cargando  ? View.VISIBLE : View.GONE);
        layoutContenido.setVisibility(cargando ? View.GONE    : View.VISIBLE);
    }

    /**
     * Activa o desactiva el ProgressBar de guardado y los botones de acción.
     * Evita envíos duplicados mientras la operación está en curso.
     * @param loading true para bloquear la interacción, false para restaurarla
     */
    private void setLoading(boolean loading) {
        isLoading = loading;
        if (pbGuardando != null)
            pbGuardando.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnGuardar  != null) btnGuardar.setEnabled(!loading);
        if (btnCancelar != null) btnCancelar.setEnabled(!loading);
    }

    /**
     * Navega atrás en el back stack del fragment.
     * Equivale a cancelarEdicion() / navigateToHome() en Angular.
     */
    private void navegarAtras() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    /**
     * Muestra un Snackbar centrado con el mensaje recibido.
     * @param message Texto a mostrar
     */
    private void showSnackbar(String message) {
        if (!isAdded() || getView() == null) return;

        Snackbar snackbar = Snackbar.make(getView(), message, Snackbar.LENGTH_LONG);
        TextView textView = snackbar.getView()
                .findViewById(com.google.android.material.R.id.snackbar_text);

        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }
}