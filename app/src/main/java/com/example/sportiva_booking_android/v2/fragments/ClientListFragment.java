package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class ClientListFragment extends Fragment {

    private static final String ARG_ROL = "ROL";

    /* vistas — pantalla de carga global */
    private LinearLayout layoutCargando;

    /* vistas — contenido real (oculto hasta que los datos estén listos) */
    private ScrollView layoutContent;

    /* vistas — card estado vacío */
    private CardView layoutVacio;

    /* vistas — card panel con la lista */
    private CardView cardPanel;

    /* vistas — RecyclerView de clientes */
    private RecyclerView rvClientes;

    /* vistas — botón de vuelta al home */
    private Button btnVolverHome;

    /* firebase */
    private FirebaseAuth firebaseAuth;

    /* TODO: inyectar ClienteService y MembershipService cuando estén listos */

    /* lista en memoria de clientes cargados */
    private final List<Object> clientes = new ArrayList<>();

    /* rol del usuario autenticado — necesario para regenerar el HomeFragment al volver */
    private Rol rolUsuarioLogueado;



    /**
     * Crea una nueva instancia del fragment pasando el rol por Bundle.
     *
     * @param rol Rol del usuario autenticado
     * @return Instancia configurada del fragment
     */
    public static ClientListFragment newInstance(Rol rol) {
        ClientListFragment fragment = new ClientListFragment();
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
        return inflater.inflate(R.layout.fragment_client_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recuperarRol();
        inicializarFirebase();
        inicializarVistas(view);
        configurarListeners();
        inicializarCarga();
    }


    /**
     * Recupera el rol del Bundle de argumentos.
     * Si no se encuentra o el valor no es válido usa ADMINISTRADOR como fallback.
     */
    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuarioLogueado = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.ADMINISTRADOR.name())
                );
            } catch (IllegalArgumentException e) {
                rolUsuarioLogueado = Rol.ADMINISTRADOR;
            }
        } else {
            rolUsuarioLogueado = Rol.ADMINISTRADOR;
        }
    }

    /**
     * Inicializa las instancias de Firebase necesarias para la autenticación.
     */
    private void inicializarFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    /**
     * Enlaza todas las vistas del layout y configura el RecyclerView.
     * Arranca con la pantalla de carga visible y el contenido oculto.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
     */
    private void inicializarVistas(View view) {
        layoutCargando = view.findViewById(R.id.layoutCargandoClientList);
        layoutContent  = view.findViewById(R.id.layoutContentClientList);
        layoutVacio    = view.findViewById(R.id.layoutVacioClientList);
        cardPanel      = view.findViewById(R.id.cardPanelClientes);
        rvClientes     = view.findViewById(R.id.rvClientes);
        btnVolverHome  = view.findViewById(R.id.btnVolverHomeClientList);

        /* Cuando el adaptador esté listo, enlazarlo aquí igual que en ProfesionalListFragment */
        rvClientes.setLayoutManager(new LinearLayoutManager(requireContext()));

        /* arrancamos con la pantalla de carga activa y el contenido oculto */
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
    }

    /**
     * Asigna los listeners a todos los botones del fragment.
     */
    private void configurarListeners() {
        btnVolverHome.setOnClickListener(v -> navegarAlHome());
    }



    /**
     * Inicializa la carga de datos comprobando que existe sesión activa en Firebase Auth.
     * Cuando ClienteService y MembershipService estén implementados, la lógica real
     * irá en {@link #cargarClientes(String)}.
     */
    private void inicializarCarga() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user != null) {
            cargarClientes(user.getUid());
        } else {
            showSnackbar("Error: No se ha detectado una sesión de administrador activa");
            mostrarContenido();
        }
    }

    /**
     * Obtiene la lista de clientes con membresía activa en el centro del administrador.
     * TODO: implementar cuando ClienteService y MembershipService estén listos.
     * @param adminUid UID del administrador propietario del centro
     */
    private void cargarClientes(String adminUid) {
        /* --- PLACEHOLDER: simula respuesta vacía hasta integrar los servicios --- */
        clientes.clear();
        mostrarContenido();
        actualizarEstadoVista();
    }



    /**
     * Muestra el panel con la lista si hay clientes, o el card vacío si no hay ninguno.
     * Ambos viven dentro de layoutContent, que ya está visible cuando se llama a este método.
     */
    private void actualizarEstadoVista() {
        if (clientes.isEmpty()) {
            cardPanel.setVisibility(View.GONE);
            layoutVacio.setVisibility(View.VISIBLE);
        } else {
            layoutVacio.setVisibility(View.GONE);
            cardPanel.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Oculta la pantalla de carga global y revela el contenido una vez que los datos están listos.
     */
    private void mostrarContenido() {
        if (!isAdded()) return;
        layoutCargando.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
    }



    /**
     * Limpia el backstack y navega al HomeFragment regenerando la vista principal.
     * Usa el mismo patrón que el resto de fragments del proyecto.
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