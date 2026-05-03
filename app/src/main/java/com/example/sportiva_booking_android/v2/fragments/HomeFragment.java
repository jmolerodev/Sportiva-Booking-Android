package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Membership;
import com.example.sportiva_booking_android.v2.models.SportCentre;
import com.example.sportiva_booking_android.v2.services.MembershipService;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    /*Clave para pasar el rol como argumento al fragment*/
    private static final String ARG_ROL = "rol";

    /*Rol del usuario autenticado*/
    private Rol userRol;

    /*UID del usuario autenticado*/
    private String currentUid;

    /*Servicios*/
    private SportCentreService sportCentreService;
    private MembershipService membershipService;

    /*Spinner de carga global*/
    private View layoutLoading;

    /*Contenedor principal que se muestra cuando termina la carga*/
    private View layoutContent;

    /*Secciones por rol*/
    private LinearLayout sectionAdmin;
    private LinearLayout sectionPro;
    private LinearLayout sectionCliente;
    private LinearLayout sectionRoot;

    /*
     * Contenedores donde se infla dinámicamente item_centro_card.xml.
     * Se usan como parent en inflarCardCentro() para cada rol.
     */
    private LinearLayout cardCentroAdmin;
    private LinearLayout cardCentroPro;

    /*Divs informativos vacíos*/
    private LinearLayout divSinCentroAdmin;
    private LinearLayout divSinCentroPro;
    private LinearLayout divSinCentrosCliente;

    /*Secciones de listas del cliente*/
    private LinearLayout sectionConMembresia;
    private LinearLayout sectionSinMembresia;
    private LinearLayout listConMembresia;
    private LinearLayout listSinMembresia;
    private LinearLayout bannerSinMembresia;

    /*
     * Botones de acción del Admin.
     * Se reasignan en inflarCardCentro() cada vez que se infla la card,
     * para que confirmarEliminacion() y eliminarCentro() siempre apunten
     * a la instancia activa dentro de item_centro_card.xml.
     */
    private Button btnCrearCentro;
    private Button btnEditarCentro;
    private Button btnEliminarCentro;


    private Button btnVerCentro;

    /**
     * Método de fábrica estático para instanciar el fragment con el rol del usuario.
     * Es la forma correcta de pasar datos a un Fragment en Android.
     *
     * @param rol Rol del usuario autenticado
     * @return Nueva instancia de HomeFragment con el rol encapsulado en sus argumentos
     */
    public static HomeFragment newInstance(Rol rol) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL, rol.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Recuperamos el rol de los argumentos*/
        if (getArguments() != null) {
            try {
                userRol = Rol.valueOf(getArguments().getString(ARG_ROL));
            } catch (IllegalArgumentException e) {
                userRol = Rol.CLIENTE;
            }
        }

        /*Recuperamos el UID del usuario autenticado*/
        currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        /*Inicializamos los servicios*/
        sportCentreService = new SportCentreService();
        membershipService = new MembershipService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /*Enlazamos todas las vistas del layout*/
        bindViews(view);

        /*Mostramos el spinner mientras cargamos datos*/
        mostrarLoading(true);

        /*Ocultamos todas las secciones antes de saber qué mostrar*/
        ocultarTodasLasSecciones();

        /*Lanzamos la carga según el rol*/
        cargarSegunRol();
    }

    /**
     * Enlaza todas las vistas del layout con sus referencias Java.
     * Los botones btnEditarCentro y btnEliminarCentro se obtienen aquí como null
     * porque viven dentro de item_centro_card.xml, que se infla dinámicamente.
     * Se reasignan en inflarCardCentro() cuando se construye la card del Admin.
     *
     * @param view Vista raíz del fragment inflada
     */
    private void bindViews(View view) {
        layoutLoading = view.findViewById(R.id.layoutLoading);
        layoutContent = view.findViewById(R.id.layoutContent);
        sectionAdmin = view.findViewById(R.id.sectionAdmin);
        sectionPro = view.findViewById(R.id.sectionPro);
        sectionCliente = view.findViewById(R.id.sectionCliente);
        sectionRoot = view.findViewById(R.id.sectionRoot);
        cardCentroAdmin = view.findViewById(R.id.cardCentroAdmin);
        cardCentroPro = view.findViewById(R.id.cardCentroPro);
        divSinCentroAdmin = view.findViewById(R.id.divSinCentroAdmin);
        divSinCentroPro = view.findViewById(R.id.divSinCentroPro);
        divSinCentrosCliente = view.findViewById(R.id.divSinCentrosCliente);
        sectionConMembresia = view.findViewById(R.id.sectionConMembresia);
        sectionSinMembresia = view.findViewById(R.id.sectionSinMembresia);
        listConMembresia = view.findViewById(R.id.listConMembresia);
        listSinMembresia = view.findViewById(R.id.listSinMembresia);
        bannerSinMembresia = view.findViewById(R.id.bannerSinMembresia);
        btnCrearCentro = view.findViewById(R.id.btnCrearCentro);

        /*
         * Estos dos botones no existen en fragment_home.xml: viven en item_centro_card.xml.
         * Se asignan desde inflarCardCentro() al inflar la card del Administrador.
         */
        btnEditarCentro = null;
        btnEliminarCentro = null;
    }

    /**
     * Oculta todas las secciones de rol antes de la carga inicial.
     * Evita parpadeos o contenido visible antes de tiempo.
     */
    private void ocultarTodasLasSecciones() {
        sectionAdmin.setVisibility(View.GONE);
        sectionPro.setVisibility(View.GONE);
        sectionCliente.setVisibility(View.GONE);
        sectionRoot.setVisibility(View.GONE);
    }

    /**
     * Controla la visibilidad del spinner de carga global y del contenido principal.
     *
     * @param loading true para mostrar el spinner, false para mostrar el contenido
     */
    private void mostrarLoading(boolean loading) {
        layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        layoutContent.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    /**
     * Delega la carga de datos al método correspondiente según el rol del usuario.
     * Equivale al bloque de switchMap del ngOnInit de Angular.
     */
    private void cargarSegunRol() {
        if (currentUid == null) return;

        switch (userRol) {
            case ADMINISTRADOR:
                cargarDatosAdmin();
                break;
            case PROFESIONAL:
                cargarDatosPro();
                break;
            case CLIENTE:
                cargarDatosCliente();
                break;
            case ROOT:
                mostrarSeccionRoot();
                break;
        }
    }

    /**
     * Carga el centro deportivo del administrador autenticado.
     * Si existe, infla item_centro_card con botones Editar y Eliminar visibles.
     * Si no existe, muestra el div informativo con el botón de crear.
     * Replica el bloque esAdministrador del Home de Angular.
     */
    private void cargarDatosAdmin() {
        sportCentreService.getSportCentreByAdminUid(currentUid, centro -> {
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                sectionAdmin.setVisibility(View.VISIBLE);

                if (centro != null) {
                    /*Tenemos centro: inflamos la card compartida con botones y ocultamos el div vacío*/
                    cardCentroAdmin.setVisibility(View.VISIBLE);
                    divSinCentroAdmin.setVisibility(View.GONE);
                    rellenarDatosCentroAdmin(centro);

                } else {
                    /*Sin centro: mostramos el div informativo con el botón de crear*/
                    cardCentroAdmin.setVisibility(View.GONE);
                    divSinCentroAdmin.setVisibility(View.VISIBLE);

                    /*Crear: abrimos el fragment en modo creación*/
                    btnCrearCentro.setOnClickListener(v -> abrirAddSportCentre(false));
                }

                mostrarLoading(false);
            });
        });
    }

    /**
     * Carga el centro deportivo donde trabaja el profesional autenticado.
     * Si tiene centroId asignado, infla item_centro_card sin botones.
     * Si no, muestra el div de vinculación pendiente.
     * Replica el bloque esProfesional del Home de Angular.
     */
    private void cargarDatosPro() {
        sportCentreService.getSportCentreByProfessionalUid(currentUid, centro -> {
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                sectionPro.setVisibility(View.VISIBLE);

                if (centro != null) {
                    /*Tiene centro asignado: inflamos la card compartida sin botones*/
                    cardCentroPro.setVisibility(View.VISIBLE);
                    divSinCentroPro.setVisibility(View.GONE);
                    rellenarDatosCentroPro(centro);
                } else {
                    /*Sin centro asignado: mostramos el div de vinculación pendiente*/
                    cardCentroPro.setVisibility(View.GONE);
                    divSinCentroPro.setVisibility(View.VISIBLE);
                }

                mostrarLoading(false);
            });
        });
    }

    /**
     * Carga todas las membresías del cliente y separa los centros en dos listas:
     * con membresía activa y sin ella. Replica el cargarCentrosCliente() de Angular.
     */
    private void cargarDatosCliente() {
        membershipService.getMembresiasByCliente(currentUid, membresias -> {
            if (getActivity() == null) return;

            /*Filtramos las membresías activas y vigentes*/
            long ahora = System.currentTimeMillis();
            List<String> centrosConMembresia = new ArrayList<>();

            for (Membership m : membresias) {
                if ("ACTIVA".equals(m.getEstado()) && m.getFechaFin() > ahora) {
                    centrosConMembresia.add(m.getCentroId());
                }
            }

            /*Cargamos todos los centros para separar listas*/
            sportCentreService.getAllSportCentres(new ValueEventListener() {

                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (getActivity() == null) return;

                    List<SportCentre> conMembresia = new ArrayList<>();
                    List<SportCentre> sinMembresia = new ArrayList<>();

                    /*Iteramos sobre los hijos del snapshot (Sports-Centre)*/
                    for (DataSnapshot child : snapshot.getChildren()) {
                        SportCentre centro = child.getValue(SportCentre.class);
                        if (centro == null) continue;

                        /*Sincronizamos el ID del nodo*/
                        centro.setId(child.getKey());

                        /*Separamos los centros según si el usuario tiene membresía en ellos*/
                        if (centrosConMembresia.contains(centro.getId())) {
                            conMembresia.add(centro);
                        } else {
                            sinMembresia.add(centro);
                        }
                    }

                    /*Actualizamos la interfaz en el hilo principal*/
                    getActivity().runOnUiThread(() -> {
                        sectionCliente.setVisibility(View.VISIBLE);
                        renderizarListasCliente(conMembresia, sinMembresia);
                        mostrarLoading(false);
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            mostrarLoading(false);
                            showCenteredSnackbar("Error al recuperar los centros deportivos.");
                        });
                    }
                }
            });
        });
    }

    /**
     * Muestra la sección del usuario Root con su div informativo.
     * No requiere llamadas a Firebase.
     */
    private void mostrarSeccionRoot() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            sectionRoot.setVisibility(View.VISIBLE);
            mostrarLoading(false);
        });
    }

    /**
     * Delega en inflarCardCentro() para construir la card del Administrador.
     * Pasa mostrarBotones=true para que aparezcan Editar y Eliminar.
     *
     * @param centro Centro deportivo encontrado en Firebase
     */
    private void rellenarDatosCentroAdmin(SportCentre centro) {
        inflarCardCentro(
                cardCentroAdmin,
                centro,
                "🏢 Mi centro deportivo",
                true   /*Admin: mostramos botones Editar y Eliminar*/
        );
    }

    /**
     * Delega en inflarCardCentro() para construir la card del Profesional.
     * Pasa mostrarBotones=false para que no aparezcan acciones de gestión.
     *
     * @param centro Centro deportivo encontrado en Firebase
     */
    private void rellenarDatosCentroPro(SportCentre centro) {
        inflarCardCentro(
                cardCentroPro,
                centro,
                "💼 Mi centro de trabajo",
                false  /*Pro: solo datos, sin botones*/
        );
    }

    /**
     * Infla item_centro_card.xml dentro del parent indicado, rellena los datos
     * comunes y carga la foto del centro con Glide.
     * Si el campo foto está vacío o la URL falla, muestra error_default.png.
     * Cuando mostrarBotones=true, hace visible layoutBotonesCentro y reasigna
     * btnEditarCentro y btnEliminarCentro para que los métodos de gestión sigan
     * apuntando a la instancia activa de la card.
     * Método reutilizable entre Admin y Pro: replica el patrón de componente
     * compartido de Angular adaptado a la vista dinámica de Android.
     *
     * @param parent         ViewGroup donde se añadirá la card inflada
     * @param centro         Centro deportivo cuyos datos se van a mostrar
     * @param labelRol       Texto del label superior según el rol del usuario
     * @param mostrarBotones true → muestra Editar/Eliminar (solo Administrador)
     */
    private void inflarCardCentro(LinearLayout parent,
                                  SportCentre centro,
                                  String labelRol,
                                  boolean mostrarBotones) {

        /*Limpiamos el contenedor antes de inflar para evitar duplicados*/
        parent.removeAllViews();

        View card = LayoutInflater.from(getContext())
                .inflate(R.layout.item_centro_card, parent, false);

        /*Referencias a las vistas de la card*/
        ImageView ivFoto = card.findViewById(R.id.ivFotoCentro);
        TextView tvLabel = card.findViewById(R.id.tvLabelRol);
        TextView tvNombre = card.findViewById(R.id.tvNombreCentroCard);
        TextView tvDireccion = card.findViewById(R.id.tvDireccionCentroCard);
        TextView tvTelefono = card.findViewById(R.id.tvTelefonoCentroCard);
        LinearLayout layoutBotones = card.findViewById(R.id.layoutBotonesCentro);
        Button btnEditarCard = card.findViewById(R.id.btnEditarCentroCard);
        Button btnEliminarCard = card.findViewById(R.id.btnEliminarCentroCard);

        /*Rellenamos los datos del centro*/
        tvLabel.setText(labelRol);
        tvNombre.setText(centro.getNombre());
        tvDireccion.setText(centro.getDireccion());
        tvTelefono.setText(centro.getTelefono() != null
                ? centro.getTelefono()
                : "Teléfono no disponible");

        /*
         * Cargamos la foto del centro con Glide.
         * Si el campo foto está vacío o es nulo, mostramos directamente error_default.
         * Si la URL existe pero falla la descarga, Glide la sustituye por error_default.
         */
        String fotoUrl = centro.getFoto();
        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(fotoUrl)
                    .centerCrop()
                    .placeholder(R.drawable.error_default)
                    .error(R.drawable.error_default)
                    .into(ivFoto);
        } else {
            ivFoto.setImageResource(R.drawable.error_default);
        }

        /*
         * Botones: solo visibles para el Administrador.
         * Los reasignamos en los campos del fragment para que confirmarEliminacion()
         * y eliminarCentro() sigan funcionando sin modificaciones.
         */
        if (mostrarBotones) {
            layoutBotones.setVisibility(View.VISIBLE);

            /*Reasignamos las referencias globales a los botones activos de la card*/
            btnEditarCentro = btnEditarCard;
            btnEliminarCentro = btnEliminarCard;

            /*Editar: abrimos el fragment en modo edición*/
            btnEditarCard.setOnClickListener(v -> abrirAddSportCentre(true));

            /*Eliminar: mostramos confirmación antes de borrar*/
            btnEliminarCard.setOnClickListener(v -> confirmarEliminacion(centro));
        }

        parent.addView(card);
    }

    /**
     * Renderiza las dos listas de centros del cliente: con membresía y sin ella.
     * Gestiona también los estados vacíos con sus divs informativos correspondientes.
     * Replica la lógica del bloque esCliente de la plantilla Angular.
     *
     * @param conMembresia Lista de centros donde el cliente tiene membresía activa
     * @param sinMembresia Lista de centros donde el cliente no tiene membresía
     */
    private void renderizarListasCliente(List<SportCentre> conMembresia,
                                         List<SportCentre> sinMembresia) {

        /*Si no hay centros en absoluto, mostramos solo el div vacío general*/
        if (conMembresia.isEmpty() && sinMembresia.isEmpty()) {
            divSinCentrosCliente.setVisibility(View.VISIBLE);
            sectionConMembresia.setVisibility(View.GONE);
            sectionSinMembresia.setVisibility(View.GONE);
            bannerSinMembresia.setVisibility(View.GONE);
            return;
        }

        divSinCentrosCliente.setVisibility(View.GONE);

        /*Banner informativo si no tiene ninguna membresía pero sí hay centros*/
        bannerSinMembresia.setVisibility(
                conMembresia.isEmpty() && !sinMembresia.isEmpty()
                        ? View.VISIBLE : View.GONE
        );

        /*Lista de centros con membresía*/
        if (!conMembresia.isEmpty()) {
            sectionConMembresia.setVisibility(View.VISIBLE);
            listConMembresia.removeAllViews();
            for (SportCentre centro : conMembresia) {
                listConMembresia.addView(crearCardCentroCliente(centro, true));
            }
        } else {
            sectionConMembresia.setVisibility(View.GONE);
        }

        /*Lista de centros sin membresía*/
        if (!sinMembresia.isEmpty()) {
            sectionSinMembresia.setVisibility(View.VISIBLE);
            listSinMembresia.removeAllViews();
            for (SportCentre centro : sinMembresia) {
                listSinMembresia.addView(crearCardCentroCliente(centro, false));
            }
        } else {
            sectionSinMembresia.setVisibility(View.GONE);
        }
    }

    /**
     * Infla y rellena una card de centro deportivo para las listas del cliente.
     * Al pulsar "Ver Centro" navega a SportCentreDetailFragment pasando el centroId por Bundle.
     *
     * @param centro       Centro deportivo a mostrar
     * @param conMembresia true si el cliente tiene membresía activa en este centro
     * @return Vista inflada y rellena lista para añadir al layout
     */
    private View crearCardCentroCliente(SportCentre centro, boolean conMembresia) {
        View card = LayoutInflater.from(getContext())
                .inflate(R.layout.item_centro_cliente, listConMembresia, false);

        TextView tvNombre    = card.findViewById(R.id.tvNombreCentro);
        TextView tvDireccion = card.findViewById(R.id.tvDireccionCentro);
        TextView tvTelefono  = card.findViewById(R.id.tvTelefonoCentro);
        View     badgeActiva = card.findViewById(R.id.badgeMembresia);
        Button   btnVerCentro = card.findViewById(R.id.btnVerCentro);

        tvNombre.setText(centro.getNombre());
        tvDireccion.setText(centro.getDireccion());
        tvTelefono.setText(centro.getTelefono() != null
                ? centro.getTelefono()
                : "Sin teléfono");

        /*Mostramos el badge solo en los centros con membresía activa*/
        badgeActiva.setVisibility(conMembresia ? View.VISIBLE : View.GONE);

        /*Navegamos al detalle del centro pasando su UID por Bundle*/
        btnVerCentro.setOnClickListener(v -> {
            SportCentreDetailFragment fragment =
                    SportCentreDetailFragment.newInstance(centro.getId());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        return card;
    }

    /**
     * Abre AddSportCentreFragment en modo creación o edición.
     * Replica la navegación router.navigate(['/add-sport-centre']) de Angular.
     *
     * @param modoEdicion true si el admin ya tiene centro y quiere editarlo
     */
    private void abrirAddSportCentre(boolean modoEdicion) {
        AddSportCentreFragment fragment = AddSportCentreFragment.newInstance(modoEdicion);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    /**
     * Muestra un Snackbar de confirmación antes de proceder con la eliminación del centro.
     * Advierte al usuario del alcance del borrado e incluye acción de confirmación.
     * Replica el patrón confirmarEliminacion() de AdminListFragment.
     *
     * @param centro Centro deportivo que se desea eliminar
     */
    private void confirmarEliminacion(SportCentre centro) {
        if (getView() == null) return;

        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Eliminar " + centro.getNombre() + "? Esta acción no se puede deshacer.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("ELIMINAR", v -> eliminarCentro(centro));
        snackbar.setActionTextColor(getResources().getColor(android.R.color.holo_red_light, null));

        /*Centramos el texto igual que en el resto de Snackbars del proyecto*/
        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);

        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }

    /**
     * Gestiona el proceso de eliminación del centro deportivo delegando al servicio.
     * Deshabilita el botón mientras procesa para evitar pulsaciones dobles.
     * Replica el patrón eliminarAdministrador() de AdminListFragment.
     *
     * @param centro Centro deportivo a eliminar
     */
    private void eliminarCentro(SportCentre centro) {

        /*Deshabilitamos el botón mientras dura la operación*/
        btnEliminarCentro.setEnabled(false);

        sportCentreService.deleteSportCentreCompleto(currentUid, new SportCentreService.OperationCallback() {

            @Override
            public void onSuccess() {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    btnEliminarCentro.setEnabled(true);

                    /*Ocultamos la card y mostramos el div de creación*/
                    cardCentroAdmin.setVisibility(View.GONE);
                    divSinCentroAdmin.setVisibility(View.VISIBLE);
                    btnCrearCentro.setOnClickListener(v -> abrirAddSportCentre(false));

                    showCenteredSnackbar("Centro eliminado correctamente");
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    btnEliminarCentro.setEnabled(true);
                    showCenteredSnackbar("Error al eliminar el centro. Inténtalo de nuevo.");
                });
            }
        });
    }

    /**
     * Método utilitario para mostrar Snackbars centrados.
     * En Fragments, usamos requireView() para obtener la raíz del layout.
     *
     * @param message Texto a mostrar
     */
    private void showCenteredSnackbar(String message) {
        if (getView() == null) return;

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