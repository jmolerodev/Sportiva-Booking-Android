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

    /* vistas */
    private ProgressBar       pbLoading;
    private ImageView         ivFotoPerfil;
    private TextView          tvNombreHeader, tvEmailHeader, tvRolBadge;
    private LinearLayout      seccionCliente, seccionProfesional;
    private TextInputLayout   tilNombre, tilApellidos;
    private TextInputLayout   tilDni, tilDireccion;
    private TextInputLayout   tilDescripcion, tilAnnosExp;
    private TextInputEditText etNombre, etApellidos;
    private TextInputEditText etDni, etDireccion;
    private TextInputEditText etDescripcion, etAnnosExp;
    private Button            btnEditar, btnGuardar, btnCancelar;
    private Button            btnCambiarFoto, btnEliminarFoto;

    /* firebase */
    private FirebaseAuth    firebaseAuth;
    private FirebaseStorage firebaseStorage;

    /* servicios */
    private ClienteService      clienteService;
    private ProfesionalService  profesionalService;
    private AdministradorService administradorService;

    /* perfil en memoria — lo casteamos al tipo correcto cuando hace falta */
    private Object perfil            = null;
    private String uid               = null;
    private String emailUsuario      = null;
    private Rol    rolUsuario        = null;

    /* control de edición y carga */
    private boolean modoEdicion      = false;
    private boolean isLoading        = false;

    /* imagen seleccionada pendiente de subir */
    private Uri    imagenSeleccionada = null;

    /* url de la foto actual en storage — necesaria para borrarla si cambia o se elimina */
    private String urlFotoOriginal    = null;

    /* snapshot del estado al entrar en edición — permite restaurar si el usuario cancela */
    private String fotoSnapshotUrl    = null;
    private String nombreSnapshot     = null;
    private String apellidosSnapshot  = null;

    /* launcher para el picker de imagen del sistema */
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    /**
     * Crea una nueva instancia del fragment pasando el rol por Bundle.
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
     * Inicializa las instancias de Firebase que vamos a necesitar.
     */
    private void inicializarFirebase() {
        firebaseAuth    = FirebaseAuth.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
    }

    /**
     * Instancia los servicios que gestionan cada tipo de perfil.
     */
    private void inicializarServicios() {
        clienteService       = new ClienteService(requireContext());
        profesionalService   = new ProfesionalService(requireContext());
        administradorService = new AdministradorService(requireContext());
    }

    /**
     * Enlaza todas las vistas del layout y configura el estado inicial.
     */
    private void inicializarVistas(View view) {
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

        /* solo mostramos la sección que corresponde al rol */
        seccionCliente.setVisibility(rolUsuario == Rol.CLIENTE ? View.VISIBLE : View.GONE);
        seccionProfesional.setVisibility(rolUsuario == Rol.PROFESIONAL ? View.VISIBLE : View.GONE);

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
        btnCambiarFoto.setOnClickListener(v -> abrirPickerImagen());
        btnEliminarFoto.setOnClickListener(v -> eliminarFoto());
    }

    /**
     * Recupera el usuario de Auth y luego sus datos de RTDB usando el servicio correspondiente al rol.
     * Todos los perfiles viven en /Persons/:uid independientemente del rol.
     */
    private void cargarPerfil() {
        if (!isAdded()) return;

        setLoading(true);

        if (firebaseAuth.getCurrentUser() == null) {
            showSnackbar("No hay sesión activa");
            setLoading(false);
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
                } else {
                    showSnackbar("No se encontraron datos del perfil");
                }
                setLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                showSnackbar("Error al recuperar el perfil");
                setLoading(false);
            }
        };

        /* usamos el servicio que corresponde al rol para leer el perfil */
        if (rolUsuario == Rol.CLIENTE) {
            clienteService.getClienteById(uid, listener);
        } else if (rolUsuario == Rol.PROFESIONAL) {
            profesionalService.getProfesionalById(uid, listener);
        } else {
            administradorService.getAdministradorById(uid, listener);
        }
    }

    /**
     * Parsea el DataSnapshot al modelo correcto según el rol y pinta las vistas.
     * @param snapshot DataSnapshot con los datos del perfil desde RTDB
     */
    private void parsearYPintarPerfil(@NonNull DataSnapshot snapshot) {
        if (rolUsuario == Rol.CLIENTE) {
            Cliente c = snapshot.getValue(Cliente.class);
            if (c == null) return;
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
            if (p == null) return;
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
            if (a == null) return;
            a.setId(snapshot.getKey());
            perfil          = a;
            urlFotoOriginal = a.getFoto();

            etNombre.setText(a.getNombre());
            etApellidos.setText(a.getApellidos());
            pintarHeader(a.getNombre(), a.getApellidos(), a.getFoto());
        }
    }

    /**
     * Actualiza el header con nombre, email, badge de rol y foto de perfil.
     * @param nombre    Nombre del usuario
     * @param apellidos Apellidos del usuario
     * @param foto      URL de la foto o vacío si no tiene
     */
    private void pintarHeader(String nombre, String apellidos, String foto) {
        tvNombreHeader.setText(nombre + " " + apellidos);
        tvEmailHeader.setText(emailUsuario);
        tvRolBadge.setText(rolUsuario.name());

        /* si hay foto la cargamos con glide, si no mostramos el placeholder directamente */
        if (foto != null && !foto.isEmpty()) {
            Glide.with(this)
                    .load(foto)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(ivFotoPerfil);
        } else {
            ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    /**
     * Abre el picker de imágenes del sistema.
     */
    private void abrirPickerImagen() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    /**
     * Recibe la URI seleccionada, genera la preview inmediata y guarda la imagen pendiente de subir.
     * @param uri URI de la imagen seleccionada por el usuario
     */
    private void onImagenSeleccionada(Uri uri) {
        imagenSeleccionada = uri;

        /* preview inmediata con la uri local antes de subir nada */
        Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(ivFotoPerfil);
    }

    /**
     * Limpia la foto seleccionada y restaura el placeholder.
     * El borrado real en Storage ocurre al guardar, no aquí.
     */
    private void eliminarFoto() {
        imagenSeleccionada = null;
        urlFotoOriginal    = null;
        ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
    }

    /**
     * Sube la imagen nueva a Storage bajo la ruta Users/uuid,
     * borra la anterior si existía y luego delega en persistirCambios().
     */
    private void subirFotoYGuardar() {
        if (uid == null || imagenSeleccionada == null) return;

        /* si había foto anterior la eliminamos antes de subir la nueva */
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
     * Actualiza el campo foto en el modelo en memoria antes de persistir.
     * @param url Nueva URL de la foto ya subida a Storage
     */
    private void actualizarFotoEnModelo(String url) {
        if (perfil instanceof Cliente)           ((Cliente) perfil).setFoto(url);
        else if (perfil instanceof Profesional)  ((Profesional) perfil).setFoto(url);
        else if (perfil instanceof Administrador)((Administrador) perfil).setFoto(url);
    }

    /**
     * Mapea los campos del formulario al modelo en memoria y persiste los cambios
     * delegando en el servicio correspondiente al rol activo.
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
            /* administrador — solo campos base */
            Administrador a = (Administrador) perfil;
            a.setNombre(nombre);
            a.setApellidos(apellidos);
            a.setFoto(foto);
            administradorService.updateAdministrador(a);
        }

        /* setValue es fire-and-forget en estos servicios, así que cerramos edición directamente */
        showSnackbar("Información actualizada correctamente");
        setModoEdicion(false);
        setLoading(false);
    }

    /**
     * Punto de entrada para guardar cambios desde los botones del layout.
     * Orquesta la subida de imagen si la hay, el borrado si procede,
     * y delega la escritura final en persistirCambios().
     */
    private void guardarCambios() {
        if (uid == null || perfil == null || isLoading) return;

        setLoading(true);

        /* si hay imagen nueva pendiente primero la subimos y luego persistimos */
        if (imagenSeleccionada != null) {
            subirFotoYGuardar();
            return;
        }

        /* si el usuario eliminó la foto la borramos de storage antes de persistir */
        if (urlFotoOriginal == null && fotoSnapshotUrl != null && !fotoSnapshotUrl.isEmpty()) {
            try {
                firebaseStorage.getReferenceFromUrl(fotoSnapshotUrl).delete();
            } catch (Exception ignored) {}
        }

        /* sin cambios de imagen persistimos directamente */
        persistirCambios();
    }

    /**
     * Activa o desactiva el modo edición.
     * Al activarlo guarda un snapshot para poder restaurar si se cancela.
     * Al cancelar descarta los cambios visuales sin tocar Storage ni RTDB.
     */
    private void toggleEdicion() {
        if (!modoEdicion) {

            /* entramos en edición — guardamos snapshot para poder cancelar */
            fotoSnapshotUrl   = urlFotoOriginal;
            nombreSnapshot    = etNombre.getText().toString();
            apellidosSnapshot = etApellidos.getText().toString();
            setModoEdicion(true);

        } else {

            /* cancelamos — descartamos la imagen seleccionada y restauramos el estado previo */
            imagenSeleccionada = null;
            urlFotoOriginal    = fotoSnapshotUrl;

            etNombre.setText(nombreSnapshot);
            etApellidos.setText(apellidosSnapshot);

            /* restauramos la foto o el placeholder según si había url guardada */
            if (fotoSnapshotUrl != null && !fotoSnapshotUrl.isEmpty()) {
                Glide.with(this)
                        .load(fotoSnapshotUrl)
                        .circleCrop()
                        .into(ivFotoPerfil);
            } else {
                ivFotoPerfil.setImageResource(R.drawable.ic_profile_placeholder);
            }

            setModoEdicion(false);
        }
    }

    /**
     * Controla la visibilidad de los botones y el estado editable de los campos.
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
     * Muestra u oculta el loader y bloquea la interacción durante operaciones asíncronas.
     * @param loading true para mostrar el loader, false para ocultarlo
     */
    private void setLoading(boolean loading) {
        isLoading = loading;
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnGuardar.setEnabled(!loading);
        btnEditar.setEnabled(!loading);
    }

    private void showSnackbar(String message) {
        if (!isAdded()) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        TextView textView = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        snackbar.show();
    }
}