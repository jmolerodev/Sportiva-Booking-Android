package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.MensajeAdapter;
import com.example.sportiva_booking_android.v2.enums.EstadoChat;
import com.example.sportiva_booking_android.v2.enums.EstadoMembresia;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Mensaje;
import com.example.sportiva_booking_android.v2.models.Membership;
import com.example.sportiva_booking_android.v2.models.SoporteChat;
import com.example.sportiva_booking_android.v2.services.MembershipService;
import com.example.sportiva_booking_android.v2.services.SoporteService;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;


public class SoporteClienteFragment extends Fragment {

    private static final String ARG_ROL          = "ROL";
    private static final String ARG_CENTRO_ID    = "centroId";
    private static final String ARG_ADMIN_ID     = "adminId";
    private static final String ARG_NOMBRE_ADMIN = "nombreAdmin";

    private View              layoutCargando;
    private View              layoutContenido;
    private LinearLayout      layoutSinMembresia;
    private ScrollView        layoutSolicitarChat;
    private LinearLayout      layoutChatPendiente;
    private LinearLayout      layoutChatActivo;
    private LinearLayout      layoutChatCerrado;
    private TextInputEditText etPrimerMensaje;
    private TextInputEditText etMensaje;
    private Button btnEnviarSolicitud;
    private Button    btnNuevaSolicitud;
    private ImageButton       btnEnviar;
    private RecyclerView      rvMensajes;
    private RecyclerView      rvMensajesPendiente;
    private TextView          tvNombreAdmin;

    private MensajeAdapter mensajeAdapter;
    private MensajeAdapter mensajePendienteAdapter;

    private SoporteService     soporteService;
    private MembershipService  membershipService;
    private SportCentreService sportCentreService;

    private Rol         rolUsuarioLogueado;
    private String      clienteUid;
    private String      centroId;
    private String      adminId;
    private String      nombreAdmin;
    private SoporteChat chatActual;

    private String chatIdEscuchando;

    private ValueEventListener chatsListener;
    private ChildEventListener mensajesListener;


    public static SoporteClienteFragment newInstance(Rol rol) {
        SoporteClienteFragment fragment = new SoporteClienteFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL, rol.name());
        fragment.setArguments(args);
        return fragment;
    }

    public static SoporteClienteFragment newInstanceFromCentro(Rol rol,
                                                               String centroId,
                                                               String adminId,
                                                               String nombreAdmin) {
        SoporteClienteFragment fragment = new SoporteClienteFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL,          rol.name());
        args.putString(ARG_CENTRO_ID,    centroId);
        args.putString(ARG_ADMIN_ID,     adminId);
        args.putString(ARG_NOMBRE_ADMIN, nombreAdmin);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_soporte_cliente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recuperarArgumentos();
        inicializarServicios();
        inicializarVistas(view);
        configurarRecyclers();
        inicializarCarga();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        soporteService.cancelarListenerChats(chatsListener);
        pararMensajesListener();
    }

    private void recuperarArgumentos() {
        if (getArguments() != null) {
            try {
                rolUsuarioLogueado = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.CLIENTE.name()));
            } catch (IllegalArgumentException e) {
                rolUsuarioLogueado = Rol.CLIENTE;
            }
            centroId    = getArguments().getString(ARG_CENTRO_ID);
            adminId     = getArguments().getString(ARG_ADMIN_ID);
            nombreAdmin = getArguments().getString(ARG_NOMBRE_ADMIN);
        } else {
            rolUsuarioLogueado = Rol.CLIENTE;
        }
    }

    private void inicializarServicios() {
        soporteService     = new SoporteService();
        membershipService  = new MembershipService();
        sportCentreService = new SportCentreService();
        clienteUid         = FirebaseAuth.getInstance().getUid();
    }

    private void inicializarVistas(View view) {
        layoutCargando      = view.findViewById(R.id.layoutCargandoSoporteCliente);
        layoutContenido     = view.findViewById(R.id.layoutContenidoSoporteCliente);
        layoutSinMembresia  = view.findViewById(R.id.layoutSinMembresia);
        layoutSolicitarChat = view.findViewById(R.id.layoutSolicitarChat);
        layoutChatPendiente = view.findViewById(R.id.layoutChatPendiente);
        layoutChatActivo    = view.findViewById(R.id.layoutChatActivo);
        layoutChatCerrado   = view.findViewById(R.id.layoutChatCerrado);
        etPrimerMensaje     = view.findViewById(R.id.etPrimerMensaje);
        etMensaje           = view.findViewById(R.id.etMensaje);
        btnEnviarSolicitud  = view.findViewById(R.id.btnEnviarSolicitud);
        btnNuevaSolicitud   = view.findViewById(R.id.btnNuevaSolicitud);
        btnEnviar           = view.findViewById(R.id.btnEnviar);
        rvMensajes          = view.findViewById(R.id.rvMensajes);
        rvMensajesPendiente = view.findViewById(R.id.rvMensajesPendiente);
        tvNombreAdmin       = view.findViewById(R.id.tvNombreAdmin);

        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    private void configurarRecyclers() {
        String uid = clienteUid != null ? clienteUid : "";

        mensajeAdapter = new MensajeAdapter(uid);
        rvMensajes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMensajes.setAdapter(mensajeAdapter);

        mensajePendienteAdapter = new MensajeAdapter(uid);
        rvMensajesPendiente.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMensajesPendiente.setAdapter(mensajePendienteAdapter);
    }

    private void inicializarCarga() {
        if (clienteUid == null) {
            mostrarContenido();
            mostrarLayout(layoutSinMembresia);
            return;
        }

        if (centroId != null && adminId != null) {
            if (nombreAdmin != null && tvNombreAdmin != null)
                tvNombreAdmin.setText(nombreAdmin);
            escucharChats();
        } else {
            cargarMembresiaYChat();
        }
    }

    private void cargarMembresiaYChat() {
        membershipService.getMembresiasByCliente(clienteUid,
                new MembershipService.MembershipListCallback() {
                    @Override
                    public void onResult(List<Membership> membresias) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {

                            if (membresias == null || membresias.isEmpty()) {
                                mostrarContenido();
                                mostrarLayout(layoutSinMembresia);
                                return;
                            }

                            long ahora = System.currentTimeMillis();
                            Membership activa = null;
                            for (Membership m : membresias) {
                                if (EstadoMembresia.ACTIVA.equals(m.getEstado())
                                        && m.getFechaFin() > ahora) {
                                    activa = m;
                                    break;
                                }
                            }
                            if (activa == null) {
                                mostrarContenido();
                                mostrarLayout(layoutSinMembresia);
                                return;
                            }

                            centroId = activa.getCentroId();
                            resolverNombreAdmin(activa.getCentroId());
                            escucharChats();
                        });
                    }
                });
    }

    private void resolverNombreAdmin(String cId) {
        sportCentreService.getSportCentreByUid(cId, centro -> {
            if (getActivity() == null || centro == null) return;
            getActivity().runOnUiThread(() -> {
                adminId = centro.getAdminUid();

                com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("Persons")
                        .child(adminId).get()
                        .addOnSuccessListener(snapshot -> {
                            if (!isAdded()) return;
                            if (snapshot.exists()) {
                                String nombre    = snapshot.child("nombre").getValue(String.class);
                                String apellidos = snapshot.child("apellidos").getValue(String.class);
                                nombreAdmin = (nombre != null ? nombre : "") + " "
                                        + (apellidos != null ? apellidos : "");
                            } else {
                                nombreAdmin = "Soporte";
                            }
                            if (tvNombreAdmin != null) tvNombreAdmin.setText(nombreAdmin);
                        });
            });
        });
    }

    private void escucharChats() {
        chatsListener = soporteService.escucharChatsByCliente(clienteUid,
                new SoporteService.ChatsCallback() {
                    @Override
                    public void onChatsObtenidos(List<SoporteChat> chats) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {

                            List<SoporteChat> delCentro = new ArrayList<>();
                            for (SoporteChat c : chats) {
                                if (centroId != null && centroId.equals(c.getCentroId()))
                                    delCentro.add(c);
                            }

                            SoporteChat abierto = null;
                            for (SoporteChat c : delCentro) {
                                if (EstadoChat.PENDIENTE.equals(c.getEstado())
                                        || EstadoChat.ACTIVO.equals(c.getEstado())) {
                                    abierto = c;
                                    break;
                                }
                            }

                            SoporteChat anterior = chatActual;
                            chatActual = abierto != null ? abierto :
                                    (!delCentro.isEmpty()
                                            ? delCentro.get(delCentro.size() - 1)
                                            : null);

                            if (chatActual != null &&
                                    EstadoChat.ACTIVO.equals(chatActual.getEstado())
                                    && anterior == null) {
                                escucharMensajes(chatActual.getId());
                                mostrarContenido();
                                mostrarEstadoChat();
                                return;
                            }

                            if (anterior != null &&
                                    EstadoChat.PENDIENTE.equals(anterior.getEstado()) &&
                                    chatActual != null &&
                                    EstadoChat.ACTIVO.equals(chatActual.getEstado())) {
                                showSnackbar("¡Tu solicitud ha sido aceptada! Ya puedes chatear.");
                                escucharMensajes(chatActual.getId());
                            }

                            mostrarContenido();
                            mostrarEstadoChat();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            mostrarContenido();
                            showSnackbar("Error al cargar el chat");
                        });
                    }
                });
    }

    private void mostrarEstadoChat() {
        if (chatActual == null) {
            mostrarLayout(layoutSolicitarChat);
            btnEnviarSolicitud.setOnClickListener(v -> solicitarChat());
            return;
        }

        EstadoChat estado = chatActual.getEstado();
        if (estado == null) {
            mostrarLayout(layoutSolicitarChat);
            return;
        }

        switch (estado) {
            case PENDIENTE:
                mostrarLayout(layoutChatPendiente);
                cargarMensajesPendiente(chatActual.getId());
                break;

            case ACTIVO:
                mostrarLayout(layoutChatActivo);
                btnEnviar.setOnClickListener(v -> enviarMensaje(chatActual.getId()));
                break;

            case CERRADO:
                pararMensajesListener();
                mostrarLayout(layoutChatCerrado);
                btnNuevaSolicitud.setOnClickListener(v -> {
                    chatActual = null;
                    mostrarLayout(layoutSolicitarChat);
                    btnEnviarSolicitud.setOnClickListener(vv -> solicitarChat());
                });
                break;
        }
    }

    private void escucharMensajes(String chatId) {
        if (chatId.equals(chatIdEscuchando)) return;

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
                    public void onError(String mensaje) {}
                });
    }

    private void cargarMensajesPendiente(String chatId) {
        pararMensajesListener();
        chatIdEscuchando = chatId;

        mensajesListener = soporteService.escucharMensajesByChat(chatId,
                new SoporteService.MensajesCallback() {
                    @Override
                    public void onMensajesObtenidos(List<Mensaje> mensajes) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                mensajePendienteAdapter.submitList(mensajes));
                    }
                    @Override
                    public void onError(String mensaje) {}
                });
    }

    private void pararMensajesListener() {
        if (mensajesListener != null && chatIdEscuchando != null)
            soporteService.cancelarListenerMensajes(chatIdEscuchando, mensajesListener);
        mensajesListener = null;
        chatIdEscuchando = null;
    }

    private void solicitarChat() {
        if (etPrimerMensaje.getText() == null) return;
        String texto = etPrimerMensaje.getText().toString().trim();

        if (texto.isEmpty()) {
            showSnackbar("Escribe tu consulta antes de enviar");
            return;
        }

        if (clienteUid == null || centroId == null || adminId == null) {
            showSnackbar("No se puede iniciar el chat: membresía no encontrada");
            return;
        }

        btnEnviarSolicitud.setEnabled(false);

        soporteService.solicitarChat(centroId, clienteUid, adminId, texto,
                new SoporteService.WriteCallback() {
                    @Override public void onExito() {
                        if (!isAdded()) return;
                        etPrimerMensaje.setText("");
                        btnEnviarSolicitud.setEnabled(true);
                        showSnackbar("Solicitud enviada. El administrador la revisará en breve.");
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        btnEnviarSolicitud.setEnabled(true);
                        showSnackbar("Error al enviar la solicitud de soporte");
                    }
                });
    }

    private void enviarMensaje(String chatId) {
        if (etMensaje.getText() == null || clienteUid == null) return;
        String texto = etMensaje.getText().toString().trim();
        if (texto.isEmpty()) return;

        soporteService.enviarMensaje(chatId, clienteUid, texto, new SoporteService.WriteCallback() {
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

    private void mostrarLayout(View visible) {
        layoutSinMembresia.setVisibility(View.GONE);
        layoutSolicitarChat.setVisibility(View.GONE);
        layoutChatPendiente.setVisibility(View.GONE);
        layoutChatActivo.setVisibility(View.GONE);
        layoutChatCerrado.setVisibility(View.GONE);
        visible.setVisibility(View.VISIBLE);
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