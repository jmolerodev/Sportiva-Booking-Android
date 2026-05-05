package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.ChatAdapter;
import com.example.sportiva_booking_android.v2.adapters.MensajeAdapter;
import com.example.sportiva_booking_android.v2.enums.EstadoChat;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Mensaje;
import com.example.sportiva_booking_android.v2.models.SoporteChat;
import com.example.sportiva_booking_android.v2.services.SoporteService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SoporteAdminFragment extends Fragment {

    private static final String ARG_ROL = "ROL";

    /*Vistas*/
    private View                  layoutCargando;
    private View                  layoutContenido;
    private LinearLayout          layoutSinSolicitudes;
    private RecyclerView          rvSolicitudes;
    private LinearLayout          layoutDetalleChat;
    private TextView              tvNombreCliente;
    private TextView              tvEstadoChat;
    private android.widget.Button btnAceptarChat;
    private ImageButton btnCerrarChat;
    private ImageButton btnEliminarChat;
    private LinearLayout          layoutInputAdmin;
    private RecyclerView          rvMensajesAdmin;
    private TextInputEditText     etMensajeAdmin;
    private ImageButton           btnEnviarAdmin;
    private TextView              tvContadorPendientes;

    /*Adaptadores*/
    private ChatAdapter    chatAdapter;
    private MensajeAdapter mensajeAdapter;

    /*Servicios*/
    private SoporteService soporteService;

    /*Estado*/
    private Rol         rolUsuarioLogueado;
    private String      adminUid;
    private SoporteChat chatSeleccionado;

    /* Guard: evita destruir el ChildEventListener de mensajes cuando
     * escucharChatsByCentro re-emite por un cambio de fechaUltimoMensaje */
    private String chatIdEscuchando;

    /*Listeners Firebase (cancelados en onDestroyView)*/
    private ValueEventListener chatsListener;
    private ChildEventListener mensajesListener;

    /*Resolución de nombres de clientes*/
    private final Map<String, String> nombresClientes = new HashMap<>();



    /**
     * Método de factoría. Pasamos el rol por Bundle igual que MediaManagementFragment.
     */
    public static SoporteAdminFragment newInstance(Rol rol) {
        SoporteAdminFragment fragment = new SoporteAdminFragment();
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
        return inflater.inflate(R.layout.fragment_soporte_admin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recuperarRol();
        inicializarServicios();
        inicializarVistas(view);
        configurarRecyclers();
        inicializarCarga();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        /* Cancelamos los listeners de Firebase para evitar fugas de memoria */
        soporteService.cancelarListenerChats(chatsListener);
        pararMensajesListener();
    }

    /*Inicialización*/

    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuarioLogueado = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.ADMINISTRADOR.name()));
            } catch (IllegalArgumentException e) {
                rolUsuarioLogueado = Rol.ADMINISTRADOR;
            }
        } else {
            rolUsuarioLogueado = Rol.ADMINISTRADOR;
        }
    }

    private void inicializarServicios() {
        soporteService = new SoporteService();
        adminUid       = FirebaseAuth.getInstance().getUid();
    }

    private void inicializarVistas(View view) {
        layoutCargando       = view.findViewById(R.id.layoutCargandoSoporteAdmin);
        layoutContenido      = view.findViewById(R.id.layoutContenidoSoporteAdmin);
        layoutSinSolicitudes = view.findViewById(R.id.layoutSinSolicitudes);
        rvSolicitudes        = view.findViewById(R.id.rvSolicitudes);
        layoutDetalleChat    = view.findViewById(R.id.layoutDetalleChat);
        tvNombreCliente      = view.findViewById(R.id.tvNombreCliente);
        tvEstadoChat         = view.findViewById(R.id.tvEstadoChat);
        btnAceptarChat       = view.findViewById(R.id.btnAceptarChat);
        btnCerrarChat        = view.findViewById(R.id.btnCerrarChat);
        btnEliminarChat      = view.findViewById(R.id.btnEliminarChat);
        layoutInputAdmin     = view.findViewById(R.id.layoutInputAdmin);
        rvMensajesAdmin      = view.findViewById(R.id.rvMensajesAdmin);
        etMensajeAdmin       = view.findViewById(R.id.etMensajeAdmin);
        btnEnviarAdmin       = view.findViewById(R.id.btnEnviarAdmin);
        tvContadorPendientes = view.findViewById(R.id.tvContadorPendientes);

        /* Arranque con pantalla de carga activa */
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    private void configurarRecyclers() {
        chatAdapter = new ChatAdapter(this::seleccionarChat);
        rvSolicitudes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSolicitudes.setAdapter(chatAdapter);

        mensajeAdapter = new MensajeAdapter(adminUid != null ? adminUid : "");
        rvMensajesAdmin.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMensajesAdmin.setAdapter(mensajeAdapter);
    }

    /**
     * Comprueba que hay sesión activa e inicia la escucha de chats.
     */
    private void inicializarCarga() {
        if (adminUid == null) {
            mostrarContenido();
            return;
        }
        escucharChats();
    }

    /*Lógica principal*/

    /**
     * Escucha en tiempo real todos los chats del centro del administrador.
     * Ordena la lista PENDIENTE → ACTIVO → CERRADO y sincroniza el chat
     * seleccionado con sus datos frescos de Firebase.
     */
    private void escucharChats() {
        chatsListener = soporteService.escucharChatsByCentro(adminUid,
                new SoporteService.ChatsCallback() {
                    @Override
                    public void onChatsObtenidos(List<SoporteChat> chats) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {

                            /* Orden: PENDIENTE(0) → ACTIVO(1) → CERRADO(2) */
                            chats.sort((a, b) ->
                                    Integer.compare(ordenEstado(a.getEstado()),
                                            ordenEstado(b.getEstado())));

                            /* Badge de pendientes */
                            long pendientes = 0;
                            for (SoporteChat c : chats)
                                if (EstadoChat.PENDIENTE.equals(c.getEstado())) pendientes++;

                            if (pendientes > 0) {
                                tvContadorPendientes.setVisibility(View.VISIBLE);
                                tvContadorPendientes.setText(pendientes + " pendiente"
                                        + (pendientes > 1 ? "s" : ""));
                            } else {
                                tvContadorPendientes.setVisibility(View.GONE);
                            }

                            boolean hayChats = !chats.isEmpty();
                            layoutSinSolicitudes.setVisibility(hayChats ? View.GONE : View.VISIBLE);
                            rvSolicitudes.setVisibility(hayChats ? View.VISIBLE : View.GONE);

                            chatAdapter.submitList(chats);
                            resolverNombresClientes(chats);

                            /* Sincronizamos el chat seleccionado con datos frescos */
                            if (chatSeleccionado != null) {
                                SoporteChat actualizado = null;
                                for (SoporteChat c : chats) {
                                    if (c.getId().equals(chatSeleccionado.getId())) {
                                        actualizado = c;
                                        break;
                                    }
                                }

                                if (actualizado != null) {
                                    EstadoChat estadoAnterior = chatSeleccionado.getEstado();
                                    chatSeleccionado = actualizado;
                                    actualizarBotonesAccion();

                                    /* Si acaba de pasar a ACTIVO arrancamos mensajes */
                                    if (estadoAnterior != EstadoChat.ACTIVO &&
                                            EstadoChat.ACTIVO.equals(actualizado.getEstado())) {
                                        escucharMensajes(actualizado.getId());
                                    }
                                } else {
                                    /* El chat fue eliminado de Firebase */
                                    chatSeleccionado = null;
                                    layoutDetalleChat.setVisibility(View.GONE);
                                    pararMensajesListener();
                                }
                            }

                            mostrarContenido();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            mostrarContenido();
                            showSnackbar("Error al cargar los chats: " + mensaje);
                        });
                    }
                });
    }

    private int ordenEstado(EstadoChat e) {
        if (e == null) return 2;
        switch (e) {
            case PENDIENTE: return 0;
            case ACTIVO:    return 1;
            default:        return 2;
        }
    }

    /**
     * Resuelve el nombre completo de cada cliente aún no conocido
     * consultando el nodo Persons de Firebase.
     */
    private void resolverNombresClientes(List<SoporteChat> chats) {
        for (SoporteChat chat : chats) {
            String id = chat.getClienteId();
            if (id == null || nombresClientes.containsKey(id)) continue;

            FirebaseDatabase.getInstance()
                    .getReference("Persons")
                    .child(id).get()
                    .addOnSuccessListener(snapshot -> {
                        if (!isAdded()) return;
                        if (snapshot.exists()) {
                            String nombre    = snapshot.child("nombre").getValue(String.class);
                            String apellidos = snapshot.child("apellidos").getValue(String.class);
                            nombresClientes.put(id,
                                    (nombre    != null ? nombre    : "") + " " +
                                            (apellidos != null ? apellidos : ""));
                        } else {
                            nombresClientes.put(id, "Cliente desconocido");
                        }
                        chatAdapter.setNombresClientes(nombresClientes);
                    });
        }
    }

    /**
     * Selecciona un chat de la lista para ver su conversación.
     * No hace nada si el chat ya estaba seleccionado para no interrumpir
     * el ChildEventListener de mensajes innecesariamente.
     */
    private void seleccionarChat(SoporteChat chat) {
        if (chatSeleccionado != null && chatSeleccionado.getId().equals(chat.getId())) return;

        chatSeleccionado = chat;
        layoutDetalleChat.setVisibility(View.VISIBLE);
        mensajeAdapter.submitList(new ArrayList<>());

        String nombre = nombresClientes.containsKey(chat.getClienteId())
                ? nombresClientes.get(chat.getClienteId()) : "Cliente";

        tvNombreCliente.setText(nombre);
        tvEstadoChat.setText(chat.getEstado() != null ? chat.getEstado().name() : "");

        actualizarBotonesAccion();

        EstadoChat estado = chat.getEstado() != null ? chat.getEstado() : EstadoChat.CERRADO;

        switch (estado) {
            case ACTIVO:
                escucharMensajes(chat.getId());
                break;
            case PENDIENTE:
                cargarMensajesPendiente(chat.getId());
                break;
            case CERRADO:
                pararMensajesListener();
                break;
        }
    }

    /**
     * Actualiza la visibilidad de los botones de acción según el estado del chat.
     */
    private void actualizarBotonesAccion() {
        if (chatSeleccionado == null) return;

        EstadoChat estado = chatSeleccionado.getEstado();

        btnAceptarChat.setVisibility(View.GONE);
        btnCerrarChat.setVisibility(View.GONE);
        btnEliminarChat.setVisibility(View.GONE);
        layoutInputAdmin.setVisibility(View.GONE);

        if (estado == null) return;

        switch (estado) {
            case PENDIENTE:
                btnAceptarChat.setVisibility(View.VISIBLE);
                btnAceptarChat.setOnClickListener(v -> aceptarChat(chatSeleccionado));
                break;

            case ACTIVO:
                btnCerrarChat.setVisibility(View.VISIBLE);
                layoutInputAdmin.setVisibility(View.VISIBLE);
                btnCerrarChat.setOnClickListener(v -> confirmarCerrarChat(chatSeleccionado));
                btnEnviarAdmin.setOnClickListener(v -> enviarMensaje(chatSeleccionado.getId()));
                break;

            case CERRADO:
                btnEliminarChat.setVisibility(View.VISIBLE);
                btnEliminarChat.setOnClickListener(v -> confirmarEliminarChat(chatSeleccionado));
                break;
        }
    }

    /**
     * Inicia la escucha en tiempo real de mensajes con el guard chatIdEscuchando.
     * Si ya estamos escuchando este chat no destruimos el ChildEventListener.
     */
    private void escucharMensajes(String chatId) {
        if (chatId.equals(chatIdEscuchando)) return; /* guard clave */

        pararMensajesListener();
        chatIdEscuchando = chatId;

        mensajesListener = soporteService.escucharMensajesByChat(chatId,
                new SoporteService.MensajesCallback() {
                    @Override
                    public void onMensajesObtenidos(List<Mensaje> mensajes) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            mensajeAdapter.submitList(mensajes);
                            if (!mensajes.isEmpty())
                                rvMensajesAdmin.scrollToPosition(mensajes.size() - 1);
                        });
                    }
                    @Override
                    public void onError(String mensaje) { /* silencioso */ }
                });
    }

    /**
     * Carga puntual de mensajes para chats en estado PENDIENTE (sin listener continuo).
     */
    private void cargarMensajesPendiente(String chatId) {
        pararMensajesListener();
        chatIdEscuchando = chatId;

        mensajesListener = soporteService.escucharMensajesByChat(chatId,
                new SoporteService.MensajesCallback() {
                    @Override
                    public void onMensajesObtenidos(List<Mensaje> mensajes) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                mensajeAdapter.submitList(mensajes));
                    }
                    @Override
                    public void onError(String mensaje) { /* silencioso */ }
                });
    }

    private void pararMensajesListener() {
        if (mensajesListener != null && chatIdEscuchando != null)
            soporteService.cancelarListenerMensajes(chatIdEscuchando, mensajesListener);
        mensajesListener = null;
        chatIdEscuchando = null;
    }

    /*Acciones con Snackbar de confirmación (igual que SportCentreDetail)*/

    private void aceptarChat(SoporteChat chat) {
        soporteService.aceptarChat(chat.getId(), new SoporteService.WriteCallback() {
            @Override public void onExito() {
                if (!isAdded()) return;
                showSnackbar("Chat aceptado. Ya puedes conversar con el cliente");
            }
            @Override public void onError(String msg) {
                if (!isAdded()) return;
                showSnackbar("Error al aceptar el chat");
            }
        });
    }

    private void confirmarCerrarChat(SoporteChat chat) {
        if (getView() == null) return;
        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Confirmas el cierre de este chat de soporte?",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("CERRAR CHAT", v ->
                soporteService.cerrarChat(chat.getId(), new SoporteService.WriteCallback() {
                    @Override public void onExito() {
                        if (!isAdded()) return;
                        pararMensajesListener();
                        mensajeAdapter.submitList(new ArrayList<>());
                        showSnackbar("Chat cerrado correctamente");
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        showSnackbar("Error al cerrar el chat");
                    }
                }));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    private void confirmarEliminarChat(SoporteChat chat) {
        if (getView() == null) return;
        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Eliminar este chat permanentemente? Esta acción no se puede deshacer.",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("ELIMINAR", v ->
                soporteService.eliminarChat(chat.getId(), new SoporteService.WriteCallback() {
                    @Override public void onExito() {
                        if (!isAdded()) return;
                        if (chatSeleccionado != null &&
                                chatSeleccionado.getId().equals(chat.getId())) {
                            pararMensajesListener();
                            chatSeleccionado = null;
                            layoutDetalleChat.setVisibility(View.GONE);
                            mensajeAdapter.submitList(new ArrayList<>());
                        }
                        showSnackbar("Chat eliminado");
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        showSnackbar("Error al eliminar el chat");
                    }
                }));
        snackbar.setActionTextColor(
                getResources().getColor(android.R.color.holo_red_light, null));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    private void enviarMensaje(String chatId) {
        if (etMensajeAdmin.getText() == null || adminUid == null) return;
        String texto = etMensajeAdmin.getText().toString().trim();
        if (texto.isEmpty()) return;

        soporteService.enviarMensaje(chatId, adminUid, texto, new SoporteService.WriteCallback() {
            @Override public void onExito() {
                if (!isAdded()) return;
                etMensajeAdmin.setText("");
            }
            @Override public void onError(String msg) {
                if (!isAdded()) return;
                showSnackbar("Error al enviar el mensaje");
            }
        });
    }



    private void mostrarContenido() {
        if (!isAdded()) return;
        layoutCargando.setVisibility(View.GONE);
        layoutContenido.setVisibility(View.VISIBLE);
    }

    private void showSnackbar(String message) {
        if (!isAdded() || getView() == null) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    private void centrarTextoSnackbar(Snackbar snackbar) {
        TextView tv = snackbar.getView()
                .findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }
}