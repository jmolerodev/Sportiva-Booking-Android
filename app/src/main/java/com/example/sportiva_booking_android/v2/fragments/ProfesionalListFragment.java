package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.ProfesionalListAdapter;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Profesional;
import com.example.sportiva_booking_android.v2.services.ProfesionalService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class ProfesionalListFragment extends Fragment {

    private static final String ARG_ROL = "ROL";

    /* vistas — pantalla de carga global */
    private LinearLayout layoutCargando;

    /* vistas — contenido real (oculto hasta que los datos estén listos) */
    private LinearLayout layoutContent;

    /* vistas — card estado vacío */
    private CardView layoutVacio;

    /* vistas — card panel con la tabla */
    private CardView cardPanel;

    /* vistas — RecyclerView de profesionales */
    private RecyclerView rvProfesionales;

    /* vistas — botón de vuelta al home */
    private Button btnVolverHome;

    /* firebase */
    private FirebaseAuth firebaseAuth;

    /* servicios */
    private ProfesionalService profesionalService;

    /* lista en memoria de profesionales cargados */
    private final List<Profesional> profesionales = new ArrayList<>();

    /* UID del profesional que está siendo eliminado — controla el spinner de fila */
    private String deletingUid = null;

    /* adaptador del RecyclerView */
    private ProfesionalListAdapter adapter;

    /* rol del usuario autenticado — necesario para regenerar el HomeFragment al volver */
    private Rol rolUsuarioLogueado;



    /**
     * Crea una nueva instancia del fragment pasando el rol por Bundle.
     *
     * @param rol Rol del usuario autenticado
     * @return Instancia configurada del fragment
     */
    public static ProfesionalListFragment newInstance(Rol rol) {
        ProfesionalListFragment fragment = new ProfesionalListFragment();
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
        return inflater.inflate(R.layout.fragment_profesional_list, container, false);
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
     * Instancia los servicios que gestionan los profesionales en RTDB.
     */
    private void inicializarServicios() {
        profesionalService = new ProfesionalService(requireContext());
    }

    /**
     * Enlaza todas las vistas del layout y configura el RecyclerView con su adaptador.
     * Arranca con la pantalla de carga visible y el contenido oculto.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
     */
    private void inicializarVistas(View view) {
        layoutCargando  = view.findViewById(R.id.layoutCargandoProfList);
        layoutContent   = view.findViewById(R.id.layoutContentProfList);
        layoutVacio     = view.findViewById(R.id.layoutVacioProfList);
        cardPanel       = view.findViewById(R.id.cardPanelProfesionales);
        rvProfesionales = view.findViewById(R.id.rvProfesionales);
        btnVolverHome   = view.findViewById(R.id.btnVolverHomeProfList);

        /* configuramos el RecyclerView con su adaptador */
        adapter = new ProfesionalListAdapter(profesionales, this::onEliminarProfesional);
        rvProfesionales.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvProfesionales.setAdapter(adapter);

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
     * Equivale al authState$ con take(1) del componente Angular.
     */
    private void inicializarCarga() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user != null) {
            cargarProfesionales(user.getUid());
        } else {
            showSnackbar("Error: No se ha detectado una sesión de administrador activa");
            mostrarContenido();
        }
    }

    /**
     * Obtiene la lista de profesionales vinculados al administrador autenticado.
     * Actualiza el estado visual según el resultado: lista, vacío o error.
     *
     * @param adminUid UID del administrador propietario
     */
    private void cargarProfesionales(String adminUid) {
        profesionalService.getProfesionalesByAdmin(adminUid,
                new ProfesionalService.ProfesionalListCallback() {

                    @Override
                    public void onSuccess(List<Profesional> profs) {
                        if (!isAdded()) return;

                        profesionales.clear();
                        profesionales.addAll(profs);
                        adapter.notifyDataSetChanged();

                        mostrarContenido();
                        actualizarEstadoVista();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded()) return;
                        showSnackbar("Error al recuperar la lista de profesionales");
                        mostrarContenido();
                    }
                });
    }

    /**
     * Elimina un profesional junto con todas sus sesiones y reservas asociadas.
     * Muestra un Snackbar de confirmación antes de ejecutar el borrado en cascada,
     * replicando el showConfirm del SnackbarService Angular.
     * La acción de confirmación activa el spinner de fila y delega en
     * {@link ProfesionalService#deleteProfesionalCompleto} la orquestación del borrado.
     *
     * @param uid UID del profesional a eliminar
     */
    private void onEliminarProfesional(String uid) {
        if (!isAdded()) return;

        Snackbar snackbar = Snackbar.make(
                requireView(),
                "¿Deseas eliminar a este profesional? Se borrarán todas sus sesiones, reservas y datos del sistema.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("ELIMINAR", v -> ejecutarEliminacion(uid));
        snackbar.setActionTextColor(
                getResources().getColor(android.R.color.holo_red_light, null)
        );

        /* centramos el texto del Snackbar igual que en ProfileFragment */
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
     * Ejecuta el borrado en cascada tras la confirmación del usuario.
     * Activa el spinner de fila para el profesional afectado y, una vez completado,
     * actualiza la lista en memoria sin necesidad de recargar desde RTDB.
     *
     * @param uid UID del profesional a eliminar
     */
    private void ejecutarEliminacion(String uid) {
        deletingUid = uid;
        adapter.setDeletingUid(uid);
        adapter.notifyDataSetChanged();

        profesionalService.deleteProfesionalCompleto(uid,
                new ProfesionalService.OperationCallback() {

                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;

                        showSnackbar("Profesional eliminado correctamente");

                        /* actualizamos la lista en memoria sin recargar de RTDB */
                        for (int i = 0; i < profesionales.size(); i++) {
                            if (profesionales.get(i).getId().equals(uid)) {
                                profesionales.remove(i);
                                adapter.notifyItemRemoved(i);
                                break;
                            }
                        }

                        deletingUid = null;
                        adapter.setDeletingUid(null);
                        actualizarEstadoVista();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded()) return;
                        showSnackbar("Hubo un error al intentar eliminar el registro");
                        deletingUid = null;
                        adapter.setDeletingUid(null);
                        adapter.notifyDataSetChanged();
                    }
                });
    }



    /**
     * Muestra el panel con la tabla si hay profesionales, o el card vacío si no hay ninguno.
     * Ambos viven dentro de layoutContent que ya está visible cuando se llama a este método.
     */
    private void actualizarEstadoVista() {
        if (profesionales.isEmpty()) {
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
     * Usa el mismo patrón que el resto de fragments del proyecto para garantizar
     * que el Home refleje los cambios realizados.
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