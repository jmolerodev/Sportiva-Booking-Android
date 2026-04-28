package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
    private MembershipService  membershipService;

    /*--- Vistas del layout ---*/

    /*Spinner de carga global*/
    private View layoutLoading;

    /*Contenedor principal que se muestra cuando termina la carga*/
    private View layoutContent;

    /*Secciones por rol*/
    private LinearLayout sectionAdmin;
    private LinearLayout sectionPro;
    private LinearLayout sectionCliente;
    private LinearLayout sectionRoot;

    /*Cards de datos del centro (Admin y Pro)*/
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

    /*TextViews de datos del centro Admin*/
    private TextView tvNombreAdmin;
    private TextView tvDireccionAdmin;
    private TextView tvTelefonoAdmin;

    /*TextViews de datos del centro Pro*/
    private TextView tvNombrePro;
    private TextView tvDireccionPro;
    private TextView tvTelefonoPro;

    /**
     * Método de fábrica estático para instanciar el fragment con el rol del usuario.
     * Es la forma correcta de pasar datos a un Fragment en Android.
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
        membershipService  = new MembershipService();
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
     * @param view Vista raíz del fragment inflada
     */
    private void bindViews(View view) {
        layoutLoading       = view.findViewById(R.id.layoutLoading);
        layoutContent       = view.findViewById(R.id.layoutContent);

        sectionAdmin        = view.findViewById(R.id.sectionAdmin);
        sectionPro          = view.findViewById(R.id.sectionPro);
        sectionCliente      = view.findViewById(R.id.sectionCliente);
        sectionRoot         = view.findViewById(R.id.sectionRoot);

        cardCentroAdmin     = view.findViewById(R.id.cardCentroAdmin);
        cardCentroPro       = view.findViewById(R.id.cardCentroPro);

        divSinCentroAdmin   = view.findViewById(R.id.divSinCentroAdmin);
        divSinCentroPro     = view.findViewById(R.id.divSinCentroPro);
        divSinCentrosCliente= view.findViewById(R.id.divSinCentrosCliente);

        sectionConMembresia = view.findViewById(R.id.sectionConMembresia);
        sectionSinMembresia = view.findViewById(R.id.sectionSinMembresia);
        listConMembresia    = view.findViewById(R.id.listConMembresia);
        listSinMembresia    = view.findViewById(R.id.listSinMembresia);
        bannerSinMembresia  = view.findViewById(R.id.bannerSinMembresia);

        tvNombreAdmin       = view.findViewById(R.id.tvNombreAdmin);
        tvDireccionAdmin    = view.findViewById(R.id.tvDireccionAdmin);
        tvTelefonoAdmin     = view.findViewById(R.id.tvTelefonoAdmin);

        tvNombrePro         = view.findViewById(R.id.tvNombrePro);
        tvDireccionPro      = view.findViewById(R.id.tvDireccionPro);
        tvTelefonoPro       = view.findViewById(R.id.tvTelefonoPro);
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
     * @param loading true para mostrar el spinner, false para mostrar el contenido
     */
    private void mostrarLoading(boolean loading) {
        layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        layoutContent.setVisibility(loading ? View.GONE   : View.VISIBLE);
    }

    /**
     * Delega la carga de datos al método correspondiente según el rol del usuario.
     * Equivale al bloque de switchMap del ngOnInit de Angular.
     */
    private void cargarSegunRol() {
        if (currentUid == null) return;

        switch (userRol) {
            case ADMINISTRADOR: cargarDatosAdmin();     break;
            case PROFESIONAL:   cargarDatosPro();       break;
            case CLIENTE:       cargarDatosCliente();   break;
            case ROOT:          mostrarSeccionRoot();   break;
        }
    }

    /**
     * Carga el centro deportivo del administrador autenticado.
     * Si existe, muestra la card con los datos. Si no, muestra el div informativo.
     * Replica el bloque esAdministrador del Home de Angular.
     */
    private void cargarDatosAdmin() {
        sportCentreService.getSportCentreByAdminUid(currentUid, centro -> {
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                sectionAdmin.setVisibility(View.VISIBLE);

                if (centro != null) {
                    /*Tenemos centro: mostramos la card y ocultamos el div vacío*/
                    cardCentroAdmin.setVisibility(View.VISIBLE);
                    divSinCentroAdmin.setVisibility(View.GONE);
                    rellenarDatosCentroAdmin(centro);
                } else {
                    /*Sin centro: mostramos el div informativo con el botón de crear*/
                    cardCentroAdmin.setVisibility(View.GONE);
                    divSinCentroAdmin.setVisibility(View.VISIBLE);
                }

                mostrarLoading(false);
            });
        });
    }

    /**
     * Carga el centro deportivo donde trabaja el profesional autenticado.
     * Si tiene centroId asignado, muestra los datos. Si no, muestra el div de vinculación pendiente.
     * Replica el bloque esProfesional del Home de Angular.
     */
    private void cargarDatosPro() {
        sportCentreService.getSportCentreByProfessionalUid(currentUid, centro -> {
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                sectionPro.setVisibility(View.VISIBLE);

                if (centro != null) {
                    /*Tiene centro asignado: mostramos la card*/
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
                        mostrarLoading(false);
                        showCenteredSnackbar("Error al recuperar los centros deportivos.");
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
     * Rellena los TextViews de la card del centro del administrador.
     * @param centro Centro deportivo encontrado en Firebase
     */
    private void rellenarDatosCentroAdmin(SportCentre centro) {
        tvNombreAdmin.setText(centro.getNombre());
        tvDireccionAdmin.setText(centro.getDireccion());
        tvTelefonoAdmin.setText(centro.getTelefono() != null
                ? centro.getTelefono()
                : "Teléfono no disponible");
    }

    /**
     * Rellena los TextViews de la card del centro del profesional.
     * @param centro Centro deportivo encontrado en Firebase
     */
    private void rellenarDatosCentroPro(SportCentre centro) {
        tvNombrePro.setText(centro.getNombre());
        tvDireccionPro.setText(centro.getDireccion());
        tvTelefonoPro.setText(centro.getTelefono() != null
                ? centro.getTelefono()
                : "Teléfono no disponible");
    }

    /**
     * Renderiza las dos listas de centros del cliente: con membresía y sin ella.
     * Gestiona también los estados vacíos con sus divs informativos correspondientes.
     * Replica la lógica del bloque esCliente de la plantilla Angular.
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

        tvNombre.setText(centro.getNombre());
        tvDireccion.setText(centro.getDireccion());
        tvTelefono.setText(centro.getTelefono() != null
                ? centro.getTelefono()
                : "Sin teléfono");

        /*Mostramos el badge solo en los centros con membresía activa*/
        badgeActiva.setVisibility(conMembresia ? View.VISIBLE : View.GONE);

        return card;
    }

    /**
     * Método utilitario para mostrar Snackbars centrados.
     * En Fragments, usamos requireView() para obtener la raíz del layout.
     */
    private void showCenteredSnackbar(String message) {
        if (getView() == null) return;

        Snackbar snackbar = Snackbar.make(getView(), message, Snackbar.LENGTH_LONG);

        /*Obtenemos el TextView interno del Snackbar para centrarlo*/
        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);

        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }
}