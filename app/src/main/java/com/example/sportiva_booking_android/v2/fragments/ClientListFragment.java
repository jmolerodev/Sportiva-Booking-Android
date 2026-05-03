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
import com.example.sportiva_booking_android.v2.adapters.ClienteListAdapter;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Cliente;
import com.example.sportiva_booking_android.v2.models.Membership;
import com.example.sportiva_booking_android.v2.services.ClienteService;
import com.example.sportiva_booking_android.v2.services.MembershipService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientListFragment extends Fragment {

    private static final String ARG_ROL = "ROL";

    /*Vistas — pantalla de carga global*/
    private LinearLayout layoutCargando;

    /*Vistas — contenido real, oculto hasta que los datos estén listos*/
    private ScrollView layoutContent;

    /*Vistas — card informativo cuando no hay clientes vinculados*/
    private CardView layoutVacio;

    /*Vistas — card con el listado de clientes activos*/
    private CardView cardPanel;

    /*Vistas — RecyclerView donde se mostrarán los clientes*/
    private RecyclerView rvClientes;

    /*Vistas — botón para volver al home*/
    private Button btnVolverHome;

    /*Instancia de FirebaseAuth para obtener el usuario autenticado*/
    private FirebaseAuth firebaseAuth;

    /*Servicios necesarios para obtener los datos de membresías y clientes*/
    private MembershipService membershipService;
    private ClienteService clienteService;

    /*Lista en memoria con los clientes que tienen membresía activa en el centro*/
    private final List<Cliente> clientes = new ArrayList<>();

    /*Adaptador del RecyclerView*/
    private ClienteListAdapter adapter;

    /*Rol del usuario autenticado, necesario para regenerar el HomeFragment al volver*/
    private Rol rolUsuarioLogueado;


    /**
     * Crea una nueva instancia del fragment pasando el rol por Bundle
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
        inicializarServicios();
        inicializarVistas(view);
        configurarListeners();
        inicializarCarga();
    }


    /**
     * Recupera el rol del Bundle de argumentos.
     * Si no se encuentra o el valor no es válido, usamos ADMINISTRADOR como fallback
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
     * Inicializa la instancia de FirebaseAuth para poder obtener el usuario activo
     */
    private void inicializarFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    /**
     * Instancia los servicios que necesitamos para cargar membresías y clientes
     */
    private void inicializarServicios() {
        membershipService = new MembershipService();
        clienteService = new ClienteService(requireContext());
    }

    /**
     * Enlaza todas las vistas del layout con sus respectivos IDs,
     * prepara el RecyclerView con su adaptador y arranca mostrando
     * la pantalla de carga mientras los datos se obtienen de Firebase
     *
     * @param view Vista raíz del fragment
     */
    private void inicializarVistas(View view) {
        layoutCargando = view.findViewById(R.id.layoutCargandoClientList);
        layoutContent = view.findViewById(R.id.layoutContentClientList);
        layoutVacio = view.findViewById(R.id.layoutVacioClientList);
        cardPanel = view.findViewById(R.id.cardPanelClientes);
        rvClientes = view.findViewById(R.id.rvClientes);
        btnVolverHome = view.findViewById(R.id.btnVolverHomeClientList);

        adapter = new ClienteListAdapter(clientes, this::onDarDeBaja);
        rvClientes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvClientes.setAdapter(adapter);

        layoutCargando.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
    }

    /**
     * Asigna los listeners a los botones del fragment
     */
    private void configurarListeners() {
        btnVolverHome.setOnClickListener(v -> navegarAlHome());
    }


    /**
     * Punto de entrada de la carga de datos.
     * Comprobamos que existe una sesión activa antes de lanzar las consultas
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
     * Primer paso de la carga: obtenemos todas las membresías activas y vigentes
     * que pertenecen al centro de este administrador.
     * Con esa lista extraemos un Set con los IDs únicos de los clientes vinculados
     * y se lo pasamos al segundo paso para cruzarlos con los datos de Persons
     *
     * @param adminUid UID del administrador, que coincide con el centroId en la Base de Datos
     */
    private void cargarClientes(String adminUid) {
        membershipService.getMembresiasByCentro(adminUid,
                membresias -> {
                    if (!isAdded()) return;

                    /*Recorremos las membresías y guardamos los IDs de cliente en un Set
                      para evitar duplicados en caso de que un cliente tuviera varias*/
                    Set<String> uidsConMembresia = new HashSet<>();
                    for (Membership m : membresias) {
                        if (m.getClienteId() != null) {
                            uidsConMembresia.add(m.getClienteId());
                        }
                    }

                    /*Con el Set listo pasamos al segundo paso*/
                    cargarYFiltrarClientes(uidsConMembresia);
                });
    }

    /**
     * Segundo paso de la carga: obtenemos todos los usuarios con rol CLIENTE
     * y nos quedamos únicamente con los que están dentro del Set de membresías activas.
     * De esta forma cruzamos Memberships con Persons sin necesidad de múltiples consultas
     *
     * @param uidsConMembresia Set con los IDs de clientes que tienen membresía activa en el centro
     */
    private void cargarYFiltrarClientes(Set<String> uidsConMembresia) {
        clienteService.getAllClientesConRol(
                new ClienteService.ClienteListCallback() {

                    @Override
                    public void onSuccess(List<Cliente> todosLosClientes) {
                        if (!isAdded()) return;

                        clientes.clear();

                        /*Filtramos únicamente los clientes cuyo ID esté en el Set*/
                        for (Cliente c : todosLosClientes) {
                            if (uidsConMembresia.contains(c.getId())) {
                                clientes.add(c);
                            }
                        }

                        adapter.notifyDataSetChanged();
                        mostrarContenido();
                        actualizarEstadoVista();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded()) return;
                        showSnackbar("Error al cargar los clientes del centro");
                        mostrarContenido();
                    }
                });
    }


    /**
     * Muestra un Snackbar de confirmación antes de proceder con la baja del cliente.
     * Si el administrador confirma, se ejecuta el proceso de eliminación en cascada
     *
     * @param clienteUid UID del cliente a dar de baja
     */
    private void onDarDeBaja(String clienteUid) {
        if (!isAdded()) return;

        Snackbar snackbar = Snackbar.make(
                requireView(),
                "¿Deseas dar de baja a este cliente? Se eliminarán sus reservas, membresía y cuenta.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("CONFIRMAR", v -> ejecutarBaja(clienteUid));
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
     * Ejecuta la baja completa del cliente una vez confirmada por el administrador.
     * El proceso elimina en orden: reservas del cliente, su membresía activa
     * y finalmente su nodo en Persons
     *
     * @param clienteUid UID del cliente a eliminar
     */
    private void ejecutarBaja(String clienteUid) {
        /*Paso 1: buscamos la membresía activa del cliente en el centro para obtener su ID*/
        membershipService.getMembresiasByCliente(clienteUid, membresias -> {
            if (!isAdded()) return;

            /*Paso 2: eliminamos su membresía si la tiene*/
            if (!membresias.isEmpty()) {
                Membership membresia = membresias.get(0);
                if (membresia.getId() != null) {
                    membershipService.databaseReference
                            .child(membresia.getId())
                            .removeValue();
                }
            }

            /*Paso 3: buscamos el cliente en memoria y lo eliminamos de Persons*/
            Cliente clienteAEliminar = null;
            for (Cliente c : clientes) {
                if (clienteUid.equals(c.getId())) {
                    clienteAEliminar = c;
                    break;
                }
            }

            if (clienteAEliminar != null) {
                clienteService.deleteCliente(clienteAEliminar);

                /*Actualizamos la lista en memoria sin necesidad de recargar de Firebase*/
                clientes.remove(clienteAEliminar);
                adapter.notifyDataSetChanged();
                actualizarEstadoVista();

                showSnackbar("Cliente dado de baja correctamente");
            } else {
                showSnackbar("Error al procesar la baja del cliente");
            }
        });
    }


    /**
     * Decide qué mostrar una vez cargados los datos:
     * si la lista está vacía mostramos el card informativo,
     * si hay clientes mostramos el panel con el RecyclerView
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
     * Oculta el spinner de carga y muestra el contenido real del fragment
     */
    private void mostrarContenido() {
        if (!isAdded()) return;
        layoutCargando.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
    }


    /**
     * Limpia el backstack completo y vuelve al HomeFragment
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
     * Muestra un Snackbar con el texto centrado.
     * Comprobamos isAdded() para evitar crashes si el fragment ya no está activo
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