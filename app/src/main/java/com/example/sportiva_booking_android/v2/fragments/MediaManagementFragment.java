package com.example.sportiva_booking_android.v2.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.MediaAdapter;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Media;
import com.example.sportiva_booking_android.v2.services.MediaService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment de gestión multimedia para profesionales.
 * Al arrancar consulta /Persons/{uid} para obtener el centroId del profesional.
 * Si no tiene centro asignado muestra un aviso. Si lo tiene, muestra el formulario
 * de subida y la lista de vídeos propios en tiempo real.
 */
public class MediaManagementFragment extends Fragment {

    private static final String ARG_ROL = "ROL";

    // Vistas
    private LinearLayout       layoutCargando;
    private ScrollView         layoutContent;
    private CardView           cardSinCentro;
    private CardView           cardFormulario;
    private CardView           cardListaVacia;
    private CardView           cardPanelVideos;
    private TextInputLayout    tilNombre;
    private TextInputEditText  etNombre;
    private TextInputLayout    tilDescripcion;
    private TextInputEditText  etDescripcion;
    private Button             btnSeleccionarVideo;
    private TextView           tvArchivoSeleccionado;
    private LinearLayout       layoutProgreso;
    private Button             btnPublicar;
    private Button             btnCancelarSubida;
    private RecyclerView       rvVideos;

    private Button             btnVolverHomeSinCentro;
    private Button             btnVolverHomeMedia;

    // Estado
    private String             profesionalUid = null;
    private String             centroId       = null;
    private Uri                videoUri       = null;
    private String             videoFileName  = null;
    private Rol                rolUsuarioLogueado;
    private final List<Media>  misVideos      = new ArrayList<>();
    private MediaAdapter       mediaAdapter;

    // Servicios
    private MediaService mediaService;

    /* Lanzador del selector de archivos del sistema.
     * Cuando el usuario elige un vídeo guardamos su URI y su nombre. */
    private final ActivityResultLauncher<Intent> videoPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            videoUri      = result.getData().getData();
                            videoFileName = obtenerNombreArchivo(videoUri);
                            tvArchivoSeleccionado.setText("📎 " + videoFileName);
                        }
                    }
            );

    /**
     * Método de factoría. Pasamos el rol por Bundle para poder regenerar el HomeFragment al volver.
     */
    public static MediaManagementFragment newInstance(Rol rol) {
        MediaManagementFragment fragment = new MediaManagementFragment();
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
        return inflater.inflate(R.layout.fragment_media_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recuperarRol();
        inicializarServicios();
        inicializarVistas(view);
        configurarListeners();
        inicializarCarga();
    }

    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuarioLogueado = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.PROFESIONAL.name())
                );
            } catch (IllegalArgumentException e) {
                rolUsuarioLogueado = Rol.PROFESIONAL;
            }
        } else {
            rolUsuarioLogueado = Rol.PROFESIONAL;
        }
    }

    private void inicializarServicios() {
        mediaService = new MediaService();
    }

    private void inicializarVistas(View view) {
        layoutCargando        = view.findViewById(R.id.layoutCargandoMedia);
        layoutContent         = view.findViewById(R.id.layoutContentMedia);
        cardSinCentro         = view.findViewById(R.id.cardSinCentro);
        cardFormulario        = view.findViewById(R.id.cardFormulario);
        cardListaVacia        = view.findViewById(R.id.cardListaVacia);
        cardPanelVideos       = view.findViewById(R.id.cardPanelVideos);
        tilNombre             = view.findViewById(R.id.tilNombre);
        etNombre              = view.findViewById(R.id.etNombre);
        tilDescripcion        = view.findViewById(R.id.tilDescripcion);
        etDescripcion         = view.findViewById(R.id.etDescripcion);
        btnSeleccionarVideo   = view.findViewById(R.id.btnSeleccionarVideo);
        tvArchivoSeleccionado = view.findViewById(R.id.tvArchivoSeleccionado);
        layoutProgreso        = view.findViewById(R.id.layoutProgreso);
        btnPublicar           = view.findViewById(R.id.btnPublicar);
        btnCancelarSubida     = view.findViewById(R.id.btnCancelarSubida);
        rvVideos              = view.findViewById(R.id.rvVideos);

        btnVolverHomeSinCentro = view.findViewById(R.id.btnVolverHomeSinCentro);
        btnVolverHomeMedia     = view.findViewById(R.id.btnVolverHomeMedia);

        rvVideos.setLayoutManager(new LinearLayoutManager(requireContext()));
        mediaAdapter = new MediaAdapter(misVideos, media -> abrirReproductor(media.getUrl()), this::confirmarBorrarVideo);
        rvVideos.setAdapter(mediaAdapter);

        // Arrancamos con la carga visible hasta que tengamos datos del perfil
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
    }

    private void configurarListeners() {
        btnSeleccionarVideo.setOnClickListener(v    -> abrirSelectorVideo());
        btnPublicar.setOnClickListener(v            -> confirmarPublicar());
        btnCancelarSubida.setOnClickListener(v      -> confirmarCancelar());
        btnVolverHomeSinCentro.setOnClickListener(v -> navegarAlHome());
        btnVolverHomeMedia.setOnClickListener(v     -> navegarAlHome());
    }

    /**
     * Comprueba que hay sesión activa y consulta /Persons/{uid} para obtener el centroId.
     * Dependiendo del resultado muestra el formulario o el aviso de centro no asignado.
     */
    private void inicializarCarga() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            mostrarContenido();
            return;
        }

        profesionalUid = user.getUid();

        FirebaseDatabase.getInstance()
                .getReference("Persons")
                .child(profesionalUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Object raw = snapshot.child("centroId").getValue();
                            centroId = raw != null ? raw.toString() : null;
                        }

                        mostrarContenido();

                        if (centroId != null) {
                            cardSinCentro.setVisibility(View.GONE);
                            cardFormulario.setVisibility(View.VISIBLE);
                            cargarMultimedia();
                        } else {
                            cardFormulario.setVisibility(View.GONE);
                            cardSinCentro.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        mostrarContenido();
                        showSnackbar("Error al cargar el perfil: " + error.getMessage());
                    }
                });
    }

    /**
     * Escucha en tiempo real los vídeos del profesional autenticado.
     * Cada vez que hay un cambio en la base de datos la lista se refresca automáticamente.
     */
    private void cargarMultimedia() {
        if (profesionalUid == null) return;

        mediaService.getMediaByProfesional(profesionalUid, new MediaService.MediaListCallback() {
            @Override
            public void onSuccess(List<Media> mediaList) {
                misVideos.clear();
                misVideos.addAll(mediaList);
                actualizarEstadoLista();
            }

            @Override
            public void onError(String errorMessage) {
                showSnackbar("Error al cargar los vídeos: " + errorMessage);
            }
        });
    }

    /**
     * Valida el formulario y, si todo es correcto, muestra un Snackbar de confirmación
     * antes de lanzar la subida. Los errores de campo se muestran con los TextInputLayout
     * sin lanzar Snackbar — solo si falta el vídeo se avisa con Snackbar.
     * El Snackbar de confirmación solo aparece cuando el formulario está 100 % válido.
     */
    private void confirmarPublicar() {
        String nombre      = Objects.requireNonNull(etNombre.getText()).toString().trim();
        String descripcion = Objects.requireNonNull(etDescripcion.getText()).toString().trim();
        boolean valido     = true;

        if (nombre.isEmpty()) {
            tilNombre.setError("El título es obligatorio");
            valido = false;
        } else {
            tilNombre.setError(null);
        }

        if (descripcion.isEmpty()) {
            tilDescripcion.setError("La descripción es obligatoria");
            valido = false;
        } else {
            tilDescripcion.setError(null);
        }

        if (videoUri == null) {
            showSnackbar("Debes seleccionar un archivo de vídeo");
            valido = false;
        }

        if (!valido) return;

        if (profesionalUid == null || centroId == null) {
            showSnackbar("Acción denegada: Centro deportivo no vinculado");
            return;
        }

        /* formulario válido — pedimos confirmación antes de lanzar la subida */
        Snackbar snackbar = Snackbar.make(
                requireView(),
                "¿Confirmas la publicación del vídeo?",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("PUBLICAR", v -> publicarContenido());

        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }

    /**
     * Ejecuta la subida real del vídeo tras la confirmación del usuario.
     * Bloquea los botones y muestra la barra de progreso hasta que termina.
     */
    private void publicarContenido() {
        layoutProgreso.setVisibility(View.VISIBLE);
        btnPublicar.setEnabled(false);
        btnCancelarSubida.setEnabled(false);

        String nombre      = Objects.requireNonNull(etNombre.getText()).toString().trim();
        String descripcion = Objects.requireNonNull(etDescripcion.getText()).toString().trim();

        Media media = new Media();
        media.setNombre(nombre);
        media.setDescripcion(descripcion);
        media.setProfesionalId(profesionalUid);
        media.setCentroId(centroId);
        media.setFecha_subida(System.currentTimeMillis());
        media.setUrl("");

        mediaService.uploadAndSaveMedia(videoUri, videoFileName, media, new MediaService.MediaUploadCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                showSnackbar("✅ Contenido publicado exitosamente");
                limpiarFormulario();
                layoutProgreso.setVisibility(View.GONE);
                btnPublicar.setEnabled(true);
                btnCancelarSubida.setEnabled(true);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                showSnackbar("Error durante la subida: " + errorMessage);
                layoutProgreso.setVisibility(View.GONE);
                btnPublicar.setEnabled(true);
                btnCancelarSubida.setEnabled(true);
            }
        });
    }

    /**
     * Muestra un Snackbar de confirmación antes de cancelar y limpiar el formulario.
     * Si el usuario pulsa DESCARTAR se limpia todo sin subir nada y se informa mediante Snackbar.
     */
    private void confirmarCancelar() {
        Snackbar snackbar = Snackbar.make(
                requireView(),
                "¿Cancelar? Se descartarán todos los datos del formulario.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("DESCARTAR", v -> {
            limpiarFormulario();
            showSnackbar("Formulario cancelado. No se ha subido nada.");
        });

        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }

    /**
     * Muestra un Snackbar de confirmación antes de borrar el vídeo.
     * La acción ELIMINAR aparece en rojo igual que en ProfileFragment.
     * El borrado real en Storage y RTDB solo ocurre si el usuario confirma.
     *
     * @param url URL del vídeo a eliminar
     */
    private void confirmarBorrarVideo(String url) {
        if (url == null || url.isEmpty()) return;

        Snackbar snackbar = Snackbar.make(
                requireView(),
                "¿Eliminar este vídeo? Esta acción no se puede deshacer.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("ELIMINAR", v ->
                mediaService.deleteMediaByUrl(url, new MediaService.MediaDeleteCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        showSnackbar("✅ Vídeo eliminado correctamente");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded()) return;
                        showSnackbar("Error al eliminar: " + errorMessage);
                    }
                })
        );

        snackbar.setActionTextColor(
                getResources().getColor(android.R.color.holo_red_light, null)
        );

        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }

    /**
     * Actualiza la visibilidad del panel de vídeos y el estado vacío según haya contenido o no.
     */
    private void actualizarEstadoLista() {
        if (!isAdded()) return;

        if (misVideos.isEmpty()) {
            cardPanelVideos.setVisibility(View.GONE);
            cardListaVacia.setVisibility(View.VISIBLE);
        } else {
            cardListaVacia.setVisibility(View.GONE);
            cardPanelVideos.setVisibility(View.VISIBLE);
            mediaAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Oculta la pantalla de carga y muestra el contenido una vez tenemos los datos del perfil.
     */
    private void mostrarContenido() {
        if (!isAdded()) return;
        layoutCargando.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
    }

    /**
     * Abre el reproductor de vídeo nativo del dispositivo con la URL recibida.
     */
    private void abrirReproductor(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), "video/mp4");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Abre el selector de archivos del sistema filtrando por vídeos MP4.
     */
    private void abrirSelectorVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/mp4");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        videoPickerLauncher.launch(intent);
    }

    /**
     * Obtiene el nombre real del archivo a partir de su URI.
     * Usa ContentResolver para leer los metadatos del archivo seleccionado.
     */
    private String obtenerNombreArchivo(Uri uri) {
        String nombre = "video.mp4";
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) nombre = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return nombre;
    }

    /**
     * Limpia el formulario y reinicia el estado del selector de vídeo.
     */
    private void limpiarFormulario() {
        etNombre.setText("");
        etDescripcion.setText("");
        tilNombre.setError(null);
        tilDescripcion.setError(null);
        videoUri      = null;
        videoFileName = null;
        tvArchivoSeleccionado.setText("Máximo recomendado: 50 MB");
    }

    /**
     * Limpia el backstack y vuelve al HomeFragment.
     */
    private void navegarAlHome() {
        requireActivity().getSupportFragmentManager()
                .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, HomeFragment.newInstance(rolUsuarioLogueado))
                .commit();
    }

    /**
     * Muestra un Snackbar centrado con el mensaje recibido.
     * Comprueba isAdded() antes de actuar para evitar crashes si el fragment ya no está adjunto.
     *
     * @param message Texto a mostrar en el Snackbar
     */
    private void showSnackbar(String message) {
        if (!isAdded()) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        TextView textView = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );
        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        snackbar.show();
    }
}