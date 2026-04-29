package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.AdminAdapter;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Administrador;
import com.example.sportiva_booking_android.v2.services.AdministradorService;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class AdminListFragment extends Fragment {

    /*Clave para pasar el rol como argumento al fragment*/
    private static final String ARG_ROL = "rol";

    /*Rol del usuario autenticado*/
    private Rol userRol;

    /*Servicio para operaciones sobre administradores*/
    private AdministradorService administradorService;

    /*Adapter del RecyclerView*/
    private AdminAdapter adminAdapter;

    /*Lista local de administradores cargados*/
    private List<Administrador> administradores = new ArrayList<>();

    /*--- Vistas del layout ---*/

    /*Spinner de carga global*/
    private View layoutLoading;

    /*Contenedor principal que se muestra cuando termina la carga*/
    private View layoutContent;

    /*Estado vacío cuando no hay administradores*/
    private View layoutEmpty;

    /*CardView que contiene el Panel de Control y el RecyclerView*/
    private View cardPanelControl;

    /*RecyclerView con la lista de administradores*/
    private RecyclerView recyclerAdmins;

    /**
     * Método de fábrica estático para instanciar el fragment con el rol del usuario.
     * Es la forma correcta de pasar datos a un Fragment en Android.
     * @param rol Rol del usuario autenticado
     * @return Nueva instancia de AdminListFragment con el rol encapsulado en sus argumentos
     */
    public static AdminListFragment newInstance(Rol rol) {
        AdminListFragment fragment = new AdminListFragment();
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

        /*Inicializamos el servicio*/
        administradorService = new AdministradorService(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /*Enlazamos todas las vistas del layout*/
        bindViews(view);

        /*Configuramos el RecyclerView con su adapter*/
        setupRecyclerView();

        /*Mostramos el spinner mientras cargamos datos*/
        mostrarLoading(true);

        /*Lanzamos la carga de administradores*/
        cargarAdministradores();
    }

    /**
     * Enlaza todas las vistas del layout con sus referencias Java.
     * @param view Vista raíz del fragment inflada
     */
    private void bindViews(View view) {
        layoutLoading    = view.findViewById(R.id.layoutLoading);
        layoutContent    = view.findViewById(R.id.layoutContent);
        layoutEmpty      = view.findViewById(R.id.layoutEmpty);
        cardPanelControl = view.findViewById(R.id.cardPanelControl);
        recyclerAdmins   = view.findViewById(R.id.recyclerAdmins);
    }

    /**
     * Inicializa el RecyclerView con su LayoutManager y su Adapter,
     * pasando el callback de eliminación como lambda.
     */
    private void setupRecyclerView() {
        adminAdapter = new AdminAdapter(administradores, this::confirmarEliminacion);
        recyclerAdmins.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerAdmins.setAdapter(adminAdapter);
    }

    /**
     * Controla la visibilidad del spinner de carga global y del contenido principal.
     * @param loading true para mostrar el spinner, false para mostrar el contenido
     */
    private void mostrarLoading(boolean loading) {
        layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        layoutContent.setVisibility(loading ? View.GONE    : View.VISIBLE);
    }

    /**
     * Actualiza la visibilidad de los componentes según si hay datos o no.
     */
    private void actualizarEstadoVisibilidad() {
        boolean isEmpty = administradores.isEmpty();
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        cardPanelControl.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * Obtiene del servicio la lista completa de administradores
     * y la vuelca en el adapter para su representación en la vista.
     */
    private void cargarAdministradores() {
        administradorService.getAllAdministradores(new AdministradorService.AdminListCallback() {

            @Override
            public void onSuccess(List<Administrador> admins) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    administradores.clear();
                    administradores.addAll(admins);
                    adminAdapter.notifyDataSetChanged();

                    /*Actualizamos visibilidad: Solo mostramos la tarjeta correspondiente*/
                    actualizarEstadoVisibilidad();

                    mostrarLoading(false);
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    mostrarLoading(false);
                    showCenteredSnackbar("Error al cargar los administradores");
                });
            }
        });
    }

    /**
     * Muestra un Snackbar de confirmación antes de proceder con la eliminación del administrador.
     * Advierte al usuario del alcance completo del borrado: administrador y centro deportivo.
     * @param administrador Administrador que se desea eliminar
     */
    private void confirmarEliminacion(Administrador administrador) {
        if (getView() == null) return;

        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Eliminar a " + administrador.getNombre() + "? Esta acción no se puede deshacer.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("ELIMINAR", v -> eliminarAdministrador(administrador));
        snackbar.setActionTextColor(getResources().getColor(android.R.color.holo_red_light, null));

        /*Centramos el texto igual que en el resto de Snackbars*/
        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);

        if (textView != null) {
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        snackbar.show();
    }

    /**
     * Gestiona el proceso de eliminación del administrador y todos sus datos asociados.
     * Actualiza la lista local tras el éxito y notifica al adapter.
     * @param administrador Administrador que se desea eliminar
     */
    private void eliminarAdministrador(Administrador administrador) {

        /*Activamos el spinner de la fila mientras se procesa*/
        adminAdapter.setDeletingUid(administrador.getId());

        administradorService.deleteAdministradorCompleto(administrador.getId(), new AdministradorService.OperationCallback() {

            @Override
            public void onSuccess() {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    administradores.remove(administrador);
                    adminAdapter.notifyDataSetChanged();
                    adminAdapter.setDeletingUid(null);

                    /*Actualizamos visibilidad tras eliminar: si era el último, se muestra layoutEmpty*/
                    actualizarEstadoVisibilidad();

                    showCenteredSnackbar("Administrador y todos sus datos eliminados correctamente");
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    adminAdapter.setDeletingUid(null);
                    showCenteredSnackbar("Error al procesar la eliminación en la base de datos");
                });
            }
        });
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