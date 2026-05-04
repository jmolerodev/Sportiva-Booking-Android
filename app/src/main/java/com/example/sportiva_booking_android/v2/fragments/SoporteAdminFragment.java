package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.google.android.material.button.MaterialButton;
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
    private View              layoutCargando;
    private View              layoutContenido;
    private ProgressBar       progressBar;
    private TextView          tvSinChats;
    private RecyclerView      rvChats;
    private RecyclerView      rvMensajes;
    private LinearLayout      panelConversacion;
    private LinearLayout      layoutAccionesPendiente;
    private LinearLayout      layoutEnviarMensaje;
    private MaterialButton    btnAceptar;
    private MaterialButton    btnRechazar;
    private MaterialButton    btnCerrarChat;
    private MaterialButton    btnEliminarChat;
    private ImageButton       btnEnviar;
    private TextInputEditText etMensaje;
    private TextView          tvChatNombreCliente;
    private TextView          tvChatEstado;

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
        layoutCargando          = view.findViewById(R.id.layoutCargandoSoporteAdmin);
        layoutContenido         = view.findViewById(R.id.layoutContenidoSoporteAdmin);
        progressBar             = view.findViewById(R.id.progressBar);
        tvSinChats              = view.findViewById(R.id.tvSinChats);
        rvChats                 = view.findViewById(R.id.rvChats);
        rvMensajes              = view.findViewById(R.id.rvMensajes);
        panelConversacion       = view.findViewById(R.id.panelConversacion);
        layoutAccionesPendiente = view.findViewById(R.id.layoutAccionesPendiente);
        layoutEnviarMensaje     = view.findViewById(R.id.layoutEnviarMensaje);
        btnAceptar              = view.findViewById(R.id.btnAceptar);
        btnRechazar             = view.findViewById(R.id.btnRechazar);
        btnCerrarChat           = view.findViewById(R.id.btnCerrarChat);
        btnEliminarChat         = view.findViewById(R.id.btnEliminarChat);
        btnEnviar               = view.findViewById(R.id.btnEnviar);
        etMensaje               = view.findViewById(R.id.etMensaje);
        tvChatNombreCliente     = view.findViewById(R.id.tvChatNombreCliente);
        tvChatEstado            = view.findViewById(R.id.tvChatEstado);

        /* Arranque con pantalla de carga activa */
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    private void configurarRecyclers() {
        chatAdapter = new ChatAdapter(this::seleccionarChat);
        rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvChats.setAdapter(chatAdapter);

        mensajeAdapter = new MensajeAdapter(adminUid != null ? adminUid : "");
        rvMensajes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMensajes.setAdapter(mensajeAdapter);
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

                            tvSinChats.setVisibility(chats.isEmpty() ? View.VISIBLE : View.GONE);
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
                                    panelConversacion.setVisibility(View.GONE);
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
        android.util.Log.d("SOPORTE_ADMIN", "seleccionarChat() llamado, chat id: " + (chat != null ? chat.getId() : "null"));

        if (chatSeleccionado != null && chatSeleccionado.getId().equals(chat.getId())) {
            android.util.Log.d("SOPORTE_ADMIN", "Chat ya seleccionado, saliendo");
            return;
        }

        chatSeleccionado = chat;
        android.util.Log.d("SOPORTE_ADMIN", "Chat asignado, estado: " + (chat.getEstado() != null ? chat.getEstado().name() : "null"));

        panelConversacion.setVisibility(View.VISIBLE);
        mensajeAdapter.submitList(new ArrayList<>());

        String nombre = nombresClientes.containsKey(chat.getClienteId())
                ? nombresClientes.get(chat.getClienteId()) : "Cliente";
        android.util.Log.d("SOPORTE_ADMIN", "Nombre cliente: " + nombre);

        tvChatNombreCliente.setText(nombre);
        tvChatEstado.setText(chat.getEstado() != null ? chat.getEstado().name() : "");

        actualizarBotonesAccion();
        android.util.Log.d("SOPORTE_ADMIN", "Botones actualizados");

        EstadoChat estado = chat.getEstado() != null ? chat.getEstado() : EstadoChat.CERRADO;
        android.util.Log.d("SOPORTE_ADMIN", "Estado final: " + estado.name());

        switch (estado) {
            case ACTIVO:
                android.util.Log.d("SOPORTE_ADMIN", "Arrancando escucharMensajes");
                escucharMensajes(chat.getId());
                break;
            case PENDIENTE:
                android.util.Log.d("SOPORTE_ADMIN", "Arrancando cargarMensajesPendiente");
                cargarMensajesPendiente(chat.getId());
                break;
            case CERRADO:
                android.util.Log.d("SOPORTE_ADMIN", "Chat cerrado, parando listener");
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

        layoutAccionesPendiente.setVisibility(View.GONE);
        btnCerrarChat.setVisibility(View.GONE);
        btnEliminarChat.setVisibility(View.GONE);
        layoutEnviarMensaje.setVisibility(View.GONE);

        if (estado == null) return;

        switch (estado) {
            case PENDIENTE:
                layoutAccionesPendiente.setVisibility(View.VISIBLE);
                btnAceptar.setOnClickListener(v -> aceptarChat(chatSeleccionado));
                btnRechazar.setOnClickListener(v -> confirmarRechazarChat(chatSeleccionado));
                break;

            case ACTIVO:
                btnCerrarChat.setVisibility(View.VISIBLE);
                layoutEnviarMensaje.setVisibility(View.VISIBLE);
                btnCerrarChat.setOnClickListener(v -> confirmarCerrarChat(chatSeleccionado));
                btnEnviar.setOnClickListener(v -> enviarMensaje(chatSeleccionado.getId()));
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
                                rvMensajes.scrollToPosition(mensajes.size() - 1);
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

    private void confirmarRechazarChat(SoporteChat chat) {
        if (getView() == null) return;
        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Confirmas el rechazo de esta solicitud de soporte?",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("RECHAZAR", v ->
                soporteService.rechazarChat(chat.getId(), new SoporteService.WriteCallback() {
                    @Override public void onExito() {
                        if (!isAdded()) return;
                        showSnackbar("Solicitud rechazada");
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        showSnackbar("Error al rechazar el chat");
                    }
                }));
        snackbar.setActionTextColor(
                getResources().getColor(android.R.color.holo_red_light, null));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
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
                            panelConversacion.setVisibility(View.GONE);
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
        if (etMensaje.getText() == null || adminUid == null) return;
        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) return;

        soporteService.enviarMensaje(chatId, adminUid, texto, new SoporteService.WriteCallback() {
            @Override public void onExito() {
                if (!isAdded()) return;
                etMensaje.setText("");
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