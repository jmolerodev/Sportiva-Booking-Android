package com.example.sportiva_booking_android.v2.fragments;

import android.app.Activity;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Administrador;
import com.example.sportiva_booking_android.v2.models.Cliente;
import com.example.sportiva_booking_android.v2.models.Profesional;
import com.example.sportiva_booking_android.v2.services.AdministradorService;
import com.example.sportiva_booking_android.v2.services.ClienteService;
import com.example.sportiva_booking_android.v2.services.ProfesionalService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

public class ProfileFragment extends Fragment {

    private static final String ARG_ROL = "ROL";

    /* vistas — pantalla de carga global */
    private View              layoutCargando;
    private TextView          tvCargandoPerfil;

    /* vistas — contenido real (oculto hasta que todo esté listo) */
    private View              layoutContenido;

    /* vistas — header */
    private ProgressBar       pbLoading;
    private ImageView         ivFotoPerfil;
    private TextView          tvNombreHeader, tvEmailHeader, tvRolBadge;

    /* vistas — secciones por rol */
    private LinearLayout      seccionCliente, seccionProfesional;

    /* vistas — campos comunes */
    private TextInputLayout   tilNombre, tilApellidos;
    private TextInputEditText etNombre, etApellidos;

    /* vistas — campos exclusivos de cliente */
    private TextInputLayout   tilDni, tilDireccion;
    private TextInputEditText etDni, etDireccion;

    /* vistas — campos exclusivos de profesional */
    private TextInputLayout   tilDescripcion, tilAnnosExp;
    private TextInputEditText etDescripcion, etAnnosExp;

    /* vistas — botones de acción */
    private Button btnEditar, btnGuardar, btnCancelar;
    private Button btnCambiarFoto, btnEliminarFoto;

    /* firebase */
    private FirebaseAuth     firebaseAuth;
    private FirebaseStorage  firebaseStorage;

    /* servicios */
    private ClienteService       clienteService;
    private ProfesionalService   profesionalService;
    private AdministradorService administradorService;

    /* perfil en memoria — casteamos al tipo correcto cuando hace falta */
    private Object perfil       = null;
    private String uid          = null;
    private String emailUsuario = null;
    private Rol    rolUsuario   = null;

    /* control de estado */
    private boolean modoEdicion = false;
    private boolean isLoading   = false;

    /* imagen seleccionada pendiente de subir */
    private Uri imagenSeleccionada = null;

    /* url de la foto actual en storage — necesaria para borrarla si cambia o se elimina */
    private String urlFotoOriginal = null;

    /* snapshot del estado al entrar en edición — permite restaurar si el usuario cancela */
    private String fotoSnapshotUrl    = null;
    private String nombreSnapshot     = null;
    private String apellidosSnapshot  = null;

    /* launcher para el picker de imagen del sistema */
    private ActivityResultLauncher<Intent> imagePickerLauncher;



    /**
     * Crea una nueva instancia del fragment pasando el rol por Bundle.
     *
     * @param rol Rol del usuario autenticado
     * @return Instancia configurada del fragment
     */
    public static ProfileFragment newInstance(Rol rol) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL, rol.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* registramos el launcher antes de adjuntarnos a la activity */
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
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recuperarRol();
        inicializarFirebase();
        inicializarServicios();
        inicializarVistas(view);
        configurarListeners();
        cargarPerfil();
    }


    /**
     * Recupera el rol del Bundle de argumentos.
     * Si no se encuentra o el valor no es válido usa CLIENTE como fallback.
     */
    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuario = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.CLIENTE.name())
                );
            } catch (IllegalArgumentException e) {
                rolUsuario = Rol.CLIENTE;
            }
        } else {
            rolUsuario = Rol.CLIENTE;
        }
    }

    /**
     * Inicializa las instancias de Firebase necesarias para auth y storage.
     */
    private void inicializarFirebase() {
        firebaseAuth    = FirebaseAuth.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
    }

    /**
     * Instancia los servicios que gestionan cada tipo de perfil en RTDB.
     */
    private void inicializarServicios() {
        clienteService       = new ClienteService(requireContext());
        profesionalService   = new ProfesionalService(requireContext());
        administradorService = new AdministradorService(requireContext());
    }

    /**
     * Enlaza todas las vistas del layout, muestra la sección correspondiente al rol
     * y arranca siempre en modo lectura con la pantalla de carga activa.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
     */
    private void inicializarVistas(View view) {
        layoutCargando     = view.findViewById(R.id.layoutCargandoProfile);
        tvCargandoPerfil   = view.findViewById(R.id.tvCargandoPerfilProfile);
        layoutContenido    = view.findViewById(R.id.layoutContenidoProfile);
        pbLoading          = view.findViewById(R.id.pbLoadingProfile);
        ivFotoPerfil       = view.findViewById(R.id.ivFotoProfile);
        tvNombreHeader     = view.findViewById(R.id.tvNombreHeaderProfile);
        tvEmailHeader      = view.findViewById(R.id.tvEmailHeaderProfile);
        tvRolBadge         = view.findViewById(R.id.tvRolBadgeProfile);
        seccionCliente     = view.findViewById(R.id.seccionClienteProfile);
        seccionProfesional = view.findViewById(R.id.seccionProfesionalProfile);
        tilNombre          = view.findViewById(R.id.tilNombreProfile);
        tilApellidos       = view.findViewById(R.id.tilApellidosProfile);
        etNombre           = view.findViewById(R.id.etNombreProfile);
        etApellidos        = view.findViewById(R.id.etApellidosProfile);
        tilDni             = view.findViewById(R.id.tilDniProfile);
        tilDireccion       = view.findViewById(R.id.tilDireccionProfile);
        etDni              = view.findViewById(R.id.etDniProfile);
        etDireccion        = view.findViewById(R.id.etDireccionProfile);
        tilDescripcion     = view.findViewById(R.id.tilDescripcionProfile);
        tilAnnosExp        = view.findViewById(R.id.tilAnnosExpProfile);
        etDescripcion      = view.findViewById(R.id.etDescripcionProfile);
        etAnnosExp         = view.findViewById(R.id.etAnnosExpProfile);
        btnEditar          = view.findViewById(R.id.btnEditarProfile);
        btnGuardar         = view.findViewById(R.id.btnGuardarProfile);
        btnCancelar        = view.findViewById(R.id.btnCancelarProfile);
        btnCambiarFoto     = view.findViewById(R.id.btnCambiarFotoProfile);
        btnEliminarFoto    = view.findViewById(R.id.btnEliminarFotoProfile);

        /* solo mostramos la sección que corresponde al rol del usuario autenticado */
        seccionCliente.setVisibility(rolUsuario == Rol.CLIENTE ? View.VISIBLE : View.GONE);
        seccionProfesional.setVisibility(rolUsuario == Rol.PROFESIONAL ? View.VISIBLE : View.GONE);

        /* el contenido permanece oculto hasta que datos y foto estén listos */
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);

        /* arrancamos siempre en modo lectura */
        setModoEdicion(false);
    }

    /**
     * Asigna los listeners a todos los botones del fragment.
     */
    private void configurarListeners() {
        btnEditar.setOnClickListener(v -> toggleEdicion());
        btnCancelar.setOnClickListener(v -> toggleEdicion());
        btnGuardar.setOnClickListener(v -> guardarCambios());
        btnCambiarFoto.setOnClickListener(v -> abrirGaleria());
        btnEliminarFoto.setOnClickListener(v -> confirmarEliminarFoto());
    }



    /**
     * Recupera el usuario de Auth y a continuación sus datos de RTDB usando el servicio
     * correspondiente al rol. Todos los perfiles viven en /Persons/:uid sin importar el rol.
     */
    private void cargarPerfil() {
        if (!isAdded()) return;

        if (firebaseAuth.getCurrentUser() == null) {
            showSnackbar("No hay sesión activa");
            mostrarContenido();
            return;
        }

        uid          = firebaseAuth.getCurrentUser().getUid();
        emailUsuario = firebaseAuth.getCurrentUser().getEmail();

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                if (snapshot.exists()) {
                    parsearYPintarPerfil(snapshot);
                    /* mostrarContenido() se llama desde pintarHeader() una vez
                       que la foto esté lista (o confirmado que no hay foto) */
                } else {
                    showSnackbar("No se encontraron datos del perfil");
                    mostrarContenido();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                showSnackbar("Error al recuperar el perfil");
                mostrarContenido();
            }
        };

        /* delegamos en el servicio del rol para leer el nodo correcto de RTDB */
        if (rolUsuario == Rol.CLIENTE) {
            clienteService.getClienteById(uid, listener);
        } else if (rolUsuario == Rol.PROFESIONAL) {
            profesionalService.getProfesionalById(uid, listener);
        } else {
            administradorService.getAdministradorById(uid, listener);
        }
    }

    /**
     * Parsea el DataSnapshot al modelo correcto según el rol activo y pinta las vistas.
     *
     * @param snapshot DataSnapshot con los datos del perfil desde RTDB
     */
    private void parsearYPintarPerfil(@NonNull DataSnapshot snapshot) {
        if (rolUsuario == Rol.CLIENTE) {
            Cliente c = snapshot.getValue(Cliente.class);
            if (c == null) { mostrarContenido(); return; }
            c.setId(snapshot.getKey());
            perfil          = c;
            urlFotoOriginal = c.getFoto();

            etNombre.setText(c.getNombre());
            etApellidos.setText(c.getApellidos());
            etDni.setText(c.getDni());
            etDireccion.setText(c.getDireccion());
            pintarHeader(c.getNombre(), c.getApellidos(), c.getFoto());

        } else if (rolUsuario == Rol.PROFESIONAL) {
            Profesional p = snapshot.getValue(Profesional.class);
            if (p == null) { mostrarContenido(); return; }
            p.setId(snapshot.getKey());
            perfil          = p;
            urlFotoOriginal = p.getFoto();

            etNombre.setText(p.getNombre());
            etApellidos.setText(p.getApellidos());
            etDescripcion.setText(p.getDescripcion());
            etAnnosExp.setText(String.valueOf(p.getAnnos_experiencia()));
            pintarHeader(p.getNombre(), p.getApellidos(), p.getFoto());

        } else {
            Administrador a = snapshot.getValue(Administrador.class);
            if (a == null) { mostrarContenido(); return; }
            a.setId(snapshot.getKey());
            perfil          = a;
            urlFotoOriginal = a.getFoto();

            etNombre.setText(a.getNombre());
            etApellidos.setText(a.getApellidos());
            pintarHeader(a.getNombre(), a.getApellidos(), a.getFoto());
        }
    }

    /**
     * Actualiza el header con nombre completo, email, badge de rol y foto de perfil.
     * Si hay URL remota espera a que Glide termine de cargar la imagen antes de llamar
     * a mostrarContenido() — así el usuario nunca ve el placeholder.
     * Sin foto llama a mostrarContenido() directamente.
     *
     * @param nombre    Nombre del usuario
     * @param apellidos Apellidos del usuario
     * @param foto      URL de la foto almacenada en Storage, o vacío si no tiene
     */
    private void pintarHeader(String nombre, String apellidos, String foto) {
        tvNombreHeader.setText(String.format("%s %s", nombre, apellidos));
        tvEmailHeader.setText(emailUsuario);
        tvRolBadge.setText(rolUsuario.name());

        if (foto != null && !foto.isEmpty()) {
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
                            ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
                            mostrarContenido();
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
                            mostrarContenido();
                            return false;
                        }
                    })
                    .into(ivFotoPerfil);
        } else {
            ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
            mostrarContenido();
        }
    }

    /**
     * Oculta la pantalla de carga global y revela el contenido del perfil.
     * Solo se llama una vez que datos y foto están completamente listos.
     */
    private void mostrarContenido() {
        if (!isAdded()) return;
        layoutCargando.setVisibility(View.GONE);
        layoutContenido.setVisibility(View.VISIBLE);
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
                .into(ivFotoPerfil);
    }

    /**
     * Muestra un Snackbar de confirmación antes de eliminar la foto.
     * El borrado real en Storage y el reseteo visual solo ocurren si el usuario confirma.
     */
    private void confirmarEliminarFoto() {
        if (getView() == null) return;

        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Eliminar la foto de perfil? Esta acción no se puede deshacer.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("ELIMINAR", v -> eliminarFoto());
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
     * Ejecuta el borrado visual de la foto tras confirmación del usuario.
     * El borrado real en Storage ocurre al guardar, no en este momento.
     */
    private void eliminarFoto() {
        imagenSeleccionada = null;
        urlFotoOriginal    = null;
        ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
    }

    /**
     * Sube la imagen nueva a Storage bajo la ruta Users/uuid, borra la anterior si existía
     * y delega en persistirCambios() una vez disponible la URL de descarga.
     */
    private void subirFotoYGuardar() {
        if (uid == null || imagenSeleccionada == null) return;

        /* si había foto anterior la eliminamos de Storage antes de subir la nueva */
        if (urlFotoOriginal != null && !urlFotoOriginal.isEmpty()) {
            try {
                firebaseStorage.getReferenceFromUrl(urlFotoOriginal).delete();
            } catch (Exception ignored) {}
        }

        /* uuid para garantizar unicidad y evitar conflictos de caché */
        StorageReference fileRef = firebaseStorage.getReference()
                .child("Users/" + UUID.randomUUID().toString());

        fileRef.putFile(imagenSeleccionada)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return fileRef.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    urlFotoOriginal    = uri.toString();
                    imagenSeleccionada = null;
                    actualizarFotoEnModelo(urlFotoOriginal);
                    persistirCambios();
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Error al subir la imagen de perfil");
                    setLoading(false);
                });
    }

    /**
     * Actualiza el campo foto en el modelo en memoria antes de persistir en RTDB.
     *
     * @param url Nueva URL de la foto ya subida a Storage
     */
    private void actualizarFotoEnModelo(String url) {
        if (perfil instanceof Cliente)            ((Cliente) perfil).setFoto(url);
        else if (perfil instanceof Profesional)   ((Profesional) perfil).setFoto(url);
        else if (perfil instanceof Administrador) ((Administrador) perfil).setFoto(url);
    }



    /**
     * Mapea los campos del formulario al modelo en memoria y persiste los cambios
     * delegando en el servicio correspondiente al rol activo.
     * Los servicios usan setValue, que es fire-and-forget, por lo que cerramos
     * el modo edición directamente sin esperar callback de escritura.
     */
    private void persistirCambios() {
        if (uid == null || perfil == null) return;

        String nombre    = etNombre.getText().toString().trim();
        String apellidos = etApellidos.getText().toString().trim();
        String foto      = urlFotoOriginal != null ? urlFotoOriginal : "";

        if (rolUsuario == Rol.CLIENTE) {
            Cliente c = (Cliente) perfil;
            c.setNombre(nombre);
            c.setApellidos(apellidos);
            c.setFoto(foto);
            c.setDni(etDni.getText().toString().trim());
            c.setDireccion(etDireccion.getText().toString().trim());
            clienteService.updateCliente(c);

        } else if (rolUsuario == Rol.PROFESIONAL) {
            Profesional p = (Profesional) perfil;
            p.setNombre(nombre);
            p.setApellidos(apellidos);
            p.setFoto(foto);
            p.setDescripcion(etDescripcion.getText().toString().trim());
            String annosStr = etAnnosExp.getText().toString().trim();
            p.setAnnos_experiencia(annosStr.isEmpty() ? 0 : Integer.parseInt(annosStr));
            profesionalService.updateProfesional(p);

        } else {
            Administrador a = (Administrador) perfil;
            a.setNombre(nombre);
            a.setApellidos(apellidos);
            a.setFoto(foto);
            administradorService.updateAdministrador(a);
        }

        showSnackbar("Información actualizada correctamente");
        setModoEdicion(false);
        setLoading(false);
    }

    /**
     * Punto de entrada público para guardar cambios desde los botones del layout.
     * Orquesta la subida de imagen si la hay, el borrado si procede,
     * y delega la escritura final en persistirCambios().
     */
    private void guardarCambios() {
        if (uid == null || perfil == null || isLoading) return;

        setLoading(true);

        /* si hay imagen nueva pendiente primero la subimos y después persistimos */
        if (imagenSeleccionada != null) {
            subirFotoYGuardar();
            return;
        }

        /* si el usuario eliminó la foto la borramos de Storage antes de persistir */
        if (urlFotoOriginal == null && fotoSnapshotUrl != null && !fotoSnapshotUrl.isEmpty()) {
            try {
                firebaseStorage.getReferenceFromUrl(fotoSnapshotUrl).delete();
            } catch (Exception ignored) {}
        }

        /* sin cambios de imagen persistimos directamente */
        persistirCambios();
    }



    /**
     * Alterna entre modo lectura y modo edición.
     * Al activar guarda un snapshot del estado actual para poder restaurarlo si se cancela.
     * Al cancelar descarta los cambios visuales sin tocar Storage ni RTDB
     * e informa al usuario mediante Snackbar.
     */
    private void toggleEdicion() {
        if (!modoEdicion) {
            /* guardamos snapshot para poder revertir si el usuario cancela */
            fotoSnapshotUrl   = urlFotoOriginal;
            nombreSnapshot    = etNombre.getText().toString();
            apellidosSnapshot = etApellidos.getText().toString();
            setModoEdicion(true);

        } else {
            /* descartamos la imagen pendiente y restauramos el estado previo */
            imagenSeleccionada = null;
            urlFotoOriginal    = fotoSnapshotUrl;

            etNombre.setText(nombreSnapshot);
            etApellidos.setText(apellidosSnapshot);

            /* restauramos la foto o el placeholder según si había URL guardada */
            if (fotoSnapshotUrl != null && !fotoSnapshotUrl.isEmpty()) {
                Glide.with(this)
                        .load(fotoSnapshotUrl)
                        .circleCrop()
                        .into(ivFotoPerfil);
            } else {
                ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
            }

            setModoEdicion(false);
            showSnackbar("Edición cancelada. No se han guardado cambios.");
        }
    }

    /**
     * Controla la visibilidad de botones y el estado editable de los campos
     * según si estamos en modo edición o lectura.
     *
     * @param edicion true para activar el modo edición, false para el modo lectura
     */
    private void setModoEdicion(boolean edicion) {
        modoEdicion = edicion;

        etNombre.setEnabled(edicion);
        etApellidos.setEnabled(edicion);
        etDni.setEnabled(edicion);
        etDireccion.setEnabled(edicion);
        etDescripcion.setEnabled(edicion);
        etAnnosExp.setEnabled(edicion);

        btnEditar.setVisibility(!edicion ? View.VISIBLE : View.GONE);
        btnGuardar.setVisibility(edicion ? View.VISIBLE : View.GONE);
        btnCancelar.setVisibility(edicion ? View.VISIBLE : View.GONE);
        btnCambiarFoto.setVisibility(edicion ? View.VISIBLE : View.GONE);
        btnEliminarFoto.setVisibility(edicion ? View.VISIBLE : View.GONE);
    }



    /**
     * Muestra u oculta el loader y bloquea los botones de acción durante operaciones asíncronas
     * para evitar llamadas duplicadas mientras hay trabajo pendiente.
     *
     * @param loading true para mostrar el loader y bloquear interacción, false para restaurarla
     */
    private void setLoading(boolean loading) {
        isLoading = loading;
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnGuardar.setEnabled(!loading);
        btnEditar.setEnabled(!loading);
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