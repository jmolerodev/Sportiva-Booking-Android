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
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
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

    private static final String ARG_MODO_EDICION = "modoEdicion";

    /* días de la semana — mismo orden en RTDB y en el formulario */
    private static final String[] DIAS = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    /* modo edición o creación */
    private boolean modoEdicion = false;

    /* UID del administrador autenticado */
    private String adminUid;

    /* centro cargado en modo edición (null en creación) */
    private SportCentre centroOriginal;

    /* control de carga */
    private boolean isLoading        = false;
    private boolean isInitialLoading = false;

    /* imagen seleccionada pendiente de subir */
    private Uri    imagenSeleccionada = null;
    private String urlImagenOriginal  = null;

    /* servicios y Firebase */
    private SportCentreService sportCentreService;
    private FirebaseStorage    firebaseStorage;

    /* vistas — pantalla de carga global */
    private View        layoutCargando;
    private View        layoutContenido;

    /* vistas — spinner sobre la foto */
    private ProgressBar pbGuardando;

    /* vistas — campos del formulario */
    private TextInputLayout   tilNombre, tilDireccion, tilTelefono;
    private TextInputEditText etNombre, etDireccion, etTelefono;

    /* vistas — imagen */
    private ImageView ivFotoCentro;
    private Button    btnCambiarFoto, btnEliminarFoto;

    /* vistas — botones de acción */
    private Button btnGuardar, btnCancelar;

    /* horario: mapa de vistas por día */
    private final Map<String, Switch>   switchesDia    = new HashMap<>();
    private final Map<String, TextView> tvAperturasDia = new HashMap<>();
    private final Map<String, TextView> tvCierresDia   = new HashMap<>();
    private final Map<String, View>     rowsHorarioDia = new HashMap<>();

    /* launcher para el selector de imagen del sistema */
    private ActivityResultLauncher<Intent> imagePickerLauncher;


    /**
     * Crea una nueva instancia del fragment pasando el modo por Bundle.
     *
     * @param modoEdicion true si el admin ya tiene centro y quiere editarlo
     * @return Instancia configurada del fragment
     */
    public static AddSportCentreFragment newInstance(boolean modoEdicion) {
        AddSportCentreFragment fragment = new AddSportCentreFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_MODO_EDICION, modoEdicion);
        fragment.setArguments(args);
        return fragment;
    }

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

        /* registramos el launcher antes de adjuntarnos a la Activity */
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


    /**
     * Enlaza todas las vistas del layout con sus referencias Java.
     * Arranca siempre con la pantalla de carga visible si venimos en modo edición.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
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

        /* enlazamos los controles de horario por cada día */
        for (String dia : DIAS) {
            String tag = diaTag(dia);
            switchesDia.put(dia,    view.findViewWithTag("switch_"      + tag));
            tvAperturasDia.put(dia, view.findViewWithTag("apertura_"    + tag));
            tvCierresDia.put(dia,   view.findViewWithTag("cierre_"      + tag));
            rowsHorarioDia.put(dia, view.findViewWithTag("row_horario_" + tag));
        }
    }

    /**
     * Convierte el nombre del día a un tag seguro para usarlo como identificador de vistas.
     * Elimina tildes y espacios para evitar problemas con los tags XML.
     *
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


    /**
     * Asigna todos los listeners del formulario: botones, switches de horario
     * y pickers de hora para apertura y cierre.
     */
    private void configurarListeners() {

        btnGuardar.setOnClickListener(v  -> guardarCentro());

        /* Modificado: Muestra snackbar de cancelación y restaura el estado anterior */
        btnCancelar.setOnClickListener(v -> {
            if (modoEdicion && centroOriginal != null) {
                showSnackbar("Se han cancelado los cambios en la edición");
                rellenarFormulario(centroOriginal);
            } else {
                limpiarFormularioNuevo();
                showSnackbar("Se ha limpiado el formulario");
            }
        });

        btnCambiarFoto.setOnClickListener(v -> abrirGaleria());
        btnEliminarFoto.setOnClickListener(v -> eliminarFoto());

        /* por cada día configuramos el switch y los pickers de hora */
        for (String dia : DIAS) {
            Switch   sw     = switchesDia.get(dia);
            TextView tvAp   = tvAperturasDia.get(dia);
            TextView tvCi   = tvCierresDia.get(dia);
            View     rowHor = rowsHorarioDia.get(dia);

            if (sw == null || tvAp == null || tvCi == null || rowHor == null) continue;

            /* switch: muestra u oculta la fila de horas */
            sw.setOnCheckedChangeListener((btn, checked) ->
                    rowHor.setVisibility(checked ? View.VISIBLE : View.GONE));

            /* picker de apertura */
            tvAp.setOnClickListener(v -> mostrarTimePicker(tvAp));

            /* picker de cierre */
            tvCi.setOnClickListener(v -> mostrarTimePicker(tvCi));
        }
    }


    /**
     * Carga el centro existente del administrador desde Firebase y rellena el formulario.
     */
    private void cargarDatosParaEdicion() {
        if (adminUid == null) return;

        sportCentreService.getSportCentreByAdminUid(adminUid, centro -> {
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                if (centro != null) {
                    centroOriginal = centro;
                    rellenarFormulario(centro);
                    /* mostrarCargando(false) se llama desde cargarFotoYMostrar()
                       una vez que la foto esté lista o confirmado que no hay foto */
                } else {
                    showSnackbar("No se encontró el centro deportivo");
                    mostrarCargando(false);
                }
            });
        });
    }

    /**
     * Rellena todos los campos del formulario con los datos del centro cargado.
     *
     * @param centro Centro deportivo obtenido de Firebase
     */
    private void rellenarFormulario(SportCentre centro) {
        etNombre.setText(centro.getNombre());
        etDireccion.setText(centro.getDireccion());
        etTelefono.setText(centro.getTelefono());

        /* horario */
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

        /* cargamos la foto y solo revelamos el contenido cuando esté lista */
        cargarFotoYMostrar(centro.getFoto());
    }

    /**
     * Carga la foto del centro con Glide y espera a que esté lista antes de revelar
     * el contenido — el usuario nunca ve el placeholder en su lugar.
     * Si no hay URL muestra el placeholder y revela el contenido directamente.
     *
     * @param foto URL de la foto almacenada en Storage, o vacío si no tiene
     */
    private void cargarFotoYMostrar(String foto) {
        if (foto != null && !foto.isEmpty()) {
            urlImagenOriginal = foto;
            imagenSeleccionada = null; // Reseteamos selección local al cargar desde URL
            Glide.with(this)
                    .load(foto)
                    .circleCrop()
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e,
                                                    Object model,
                                                    Target<android.graphics.drawable.Drawable> target,
                                                    boolean isFirstResource) {
                            if (!isAdded()) return false;
                            /* si falla mostramos el placeholder y revelamos el contenido igualmente */
                            ivFotoCentro.setImageResource(R.drawable.ic_centre_placeholder);
                            mostrarCargando(false);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                       Object model,
                                                       Target<android.graphics.drawable.Drawable> target,
                                                       DataSource dataSource,
                                                       boolean isFirstResource) {
                            if (!isAdded()) return false;
                            /* foto lista — ya podemos revelar el contenido sin placeholder */
                            mostrarCargando(false);
                            return false;
                        }
                    })
                    .into(ivFotoCentro);
        } else {
            /* sin foto mostramos el placeholder y revelamos el contenido directamente */
            ivFotoCentro.setImageResource(R.drawable.ic_centre_placeholder);
            urlImagenOriginal = null;
            imagenSeleccionada = null;
            mostrarCargando(false);
        }
    }

    /**
     * Inicializa el horario con valores por defecto: todos los días abiertos,
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


    /**
     * Abre directamente la galería del dispositivo usando ACTION_PICK sobre
     * el content URI de imágenes — evita que el sistema ofrezca la cámara como opción.
     */
    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    /**
     * Recibe la URI seleccionada desde la galería, guarda la imagen pendiente de subir
     * y muestra la preview de forma inmediata cargando la URI local con Glide.
     *
     * @param uri URI de la imagen seleccionada por el usuario
     */
    private void onImagenSeleccionada(Uri uri) {
        imagenSeleccionada = uri;
        Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(ivFotoCentro);
    }

    /**
     * Elimina la imagen seleccionada y resetea el ImageView al placeholder.
     * El borrado real en Storage ocurre al guardar, no en este momento.
     */
    private void eliminarFoto() {
        imagenSeleccionada = null;
        urlImagenOriginal  = null;
        ivFotoCentro.setImageResource(R.drawable.ic_centre_placeholder);
    }

    /**
     * Sube la nueva imagen a Storage bajo la ruta Sport-Centre/uuid,
     * borra la anterior si existía y delega la persistencia final en guardarDatosFinales().
     *
     * @param nombre    Nombre del centro
     * @param direccion Dirección del centro
     * @param telefono  Teléfono del centro
     */
    private void subirFotoYGuardar(String nombre, String direccion, String telefono) {
        if (imagenSeleccionada == null) return;

        /* si había foto anterior la eliminamos de Storage antes de subir la nueva */
        if (urlImagenOriginal != null && !urlImagenOriginal.isEmpty()) {
            try {
                firebaseStorage.getReferenceFromUrl(urlImagenOriginal).delete();
            } catch (Exception ignored) {}
        }

        /* uuid para garantizar unicidad y evitar conflictos de caché */
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


    /**
     * Punto de entrada del formulario al pulsar "Guardar".
     * Valida, gestiona la imagen si la hay y delega la escritura en guardarDatosFinales().
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
            /* hay imagen nueva: la subimos y luego guardamos */
            subirFotoYGuardar(nombre, direccion, telefono);

        } else if (urlImagenOriginal == null) {
            /* el usuario eliminó la imagen o no hay foto — guardamos sin foto */
            if (centroOriginal != null
                    && centroOriginal.getFoto() != null
                    && !centroOriginal.getFoto().isEmpty()) {
                /* borramos la imagen anterior de Storage */
                try {
                    firebaseStorage.getReferenceFromUrl(centroOriginal.getFoto()).delete();
                } catch (Exception ignored) {}
            }
            guardarDatosFinales(nombre, direccion, telefono, "");

        } else {
            /* sin imagen nueva — mantenemos la URL original */
            guardarDatosFinales(nombre, direccion, telefono, urlImagenOriginal);
        }
    }

    /**
     * Construye el objeto SportCentre, lo persiste en RTDB.
     * Modificado: No navega atrás tras guardar para permitir seguir editando.
     *
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

                        /* Actualizamos el objeto original para futuras cancelaciones y cambiamos a modo edición */
                        centroOriginal = centro;
                        urlImagenOriginal = foto;
                        modoEdicion = true;
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
     * que se guardará en Firebase.
     *
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


    /**
     * Valida que los tres campos obligatorios del formulario no estén vacíos.
     * Marca con error los TextInputLayout correspondientes si alguno falla.
     *
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

    /**
     * Limpia todos los campos del formulario cuando el usuario está en modo creación.
     */
    private void limpiarFormularioNuevo() {
        etNombre.setText("");
        etDireccion.setText("");
        etTelefono.setText("");
        eliminarFoto();
        inicializarHorarioPorDefecto();
    }


    /**
     * Abre el diálogo nativo de Android para seleccionar una hora.
     * Al confirmar actualiza el TextView con el formato HH:mm.
     *
     * @param target TextView de apertura o cierre que recibirá la hora seleccionada
     */
    private void mostrarTimePicker(TextView target) {
        if (getContext() == null) return;

        /* parseamos la hora actual del TextView para pre-seleccionarla en el picker */
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


    /**
     * Controla la visibilidad de la pantalla de carga global y del contenido del formulario.
     * Solo se activa en modo edición mientras se esperan los datos de Firebase.
     *
     * @param cargando true para mostrar el spinner, false para revelar el formulario
     */
    private void mostrarCargando(boolean cargando) {
        if (layoutCargando == null || layoutContenido == null) return;
        layoutCargando.setVisibility(cargando  ? View.VISIBLE : View.GONE);
        layoutContenido.setVisibility(cargando ? View.GONE    : View.VISIBLE);
    }

    /**
     * Muestra u oculta el spinner sobre la foto y bloquea los botones de acción
     * durante operaciones asíncronas para evitar envíos duplicados.
     *
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
     * Navega atrás en el back stack del fragment
     */
    private void navegarAtras() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
    }

    /**
     * Muestra un Snackbar centrado con el mensaje recibido.
     * Comprueba isAdded() antes de actuar para evitar crashes si el fragment ya no está adjunto.
     *
     * @param message Texto a mostrar en el Snackbar
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