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

    /*Vistas — layouts de estado*/
    private View              layoutCargando;
    private View              layoutContenido;
    private LinearLayout      layoutSinMembresia;
    private ScrollView        layoutSolicitarChat;
    private LinearLayout      layoutChatPendiente;
    private LinearLayout      layoutChatActivo;
    private LinearLayout      layoutChatCerrado;

    /*Vistas — formulario y conversación*/
    private TextInputEditText etPrimerMensaje;
    private TextInputEditText etMensaje;
    private Button            btnEnviarSolicitud;
    private Button            btnNuevaSolicitud;
    private ImageButton       btnEnviar;
    private RecyclerView      rvMensajes;
    private RecyclerView      rvMensajesPendiente;
    private TextView          tvNombreAdmin;

    /*Adaptadores*/
    private MensajeAdapter mensajeAdapter;
    private MensajeAdapter mensajePendienteAdapter;

    /*Servicios*/
    private SoporteService     soporteService;
    private MembershipService  membershipService;
    private SportCentreService sportCentreService;

    /*Estado*/
    private Rol         rolUsuarioLogueado;
    private String      clienteUid;
    private String      centroId;
    private String      adminId;
    private String      nombreAdmin;
    private SoporteChat chatActual;

    /* Guard: evita destruir el ChildEventListener de mensajes cuando
     * escucharChatsByCliente re-emite por un cambio de fechaUltimoMensaje */
    private String chatIdEscuchando;

    /*Listeners Firebase (cancelados en onDestroyView)*/
    private ValueEventListener chatsListener;
    private ChildEventListener mensajesListener;


    /**
     * Método de factoría para navegación general desde el menú principal.
     * Solo recibe el rol; centroId y adminId se resuelven internamente a partir
     * de la membresía activa del cliente.
     *
     * @param rol Rol del usuario autenticado (normalmente CLIENTE)
     * @return Instancia configurada del Fragment
     */
    public static SoporteClienteFragment newInstance(Rol rol) {
        SoporteClienteFragment fragment = new SoporteClienteFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL, rol.name());
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Método de factoría para navegación directa desde la vista de detalle de un centro.
     * Recibe centroId, adminId y nombreAdmin ya resueltos para evitar consultas adicionales
     * a Firebase cuando el contexto del centro ya es conocido.
     *
     * @param rol         Rol del usuario autenticado (normalmente CLIENTE)
     * @param centroId    ID del centro deportivo desde el que se abre el soporte
     * @param adminId     UID del administrador del centro
     * @param nombreAdmin Nombre completo del administrador, mostrado en la cabecera del chat
     * @return Instancia configurada del Fragment
     */
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

    /**
     * Infla el layout del fragment sin inicializar vistas todavía.
     * La inicialización real ocurre en {@link #onViewCreated} una vez que
     * la jerarquía de vistas está completamente construida.
     *
     * @param inflater           Inflater proporcionado por el sistema
     * @param container          ViewGroup padre al que se adjuntará el fragment
     * @param savedInstanceState Estado previo del fragment, si existe
     * @return Vista raíz del fragment
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_soporte_cliente, container, false);
    }

    /**
     * Punto de entrada principal del fragment una vez que la vista está lista.
     * Recupera argumentos, inicializa servicios, vistas y RecyclerViews, y arranca
     * la carga inicial decidiendo si resolver la membresía o usar el centro ya conocido.
     *
     * @param view               Vista raíz devuelta por {@link #onCreateView}
     * @param savedInstanceState Estado previo del fragment, si existe
     */
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

    /**
     * Cancela los listeners de Firebase activos para evitar fugas de memoria
     * cuando la vista del fragment es destruida.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        soporteService.cancelarListenerChats(chatsListener);
        pararMensajesListener();
    }

    /**
     * Recupera rol, centroId, adminId y nombreAdmin del Bundle de argumentos.
     * Si el Bundle es nulo o el rol es desconocido, se asigna CLIENTE como fallback seguro.
     * centroId, adminId y nombreAdmin pueden ser nulos si el fragment se abrió desde
     * el menú general y aún no se ha resuelto la membresía.
     */
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

    /**
     * Instancia los servicios necesarios y obtiene el UID del cliente
     * autenticado en la sesión activa de Firebase.
     */
    private void inicializarServicios() {
        soporteService     = new SoporteService();
        membershipService  = new MembershipService();
        sportCentreService = new SportCentreService();
        clienteUid         = FirebaseAuth.getInstance().getUid();
    }

    /**
     * Enlaza todas las vistas del layout con sus variables y establece
     * el estado inicial de visibilidad: pantalla de carga activa y contenido oculto.
     *
     * @param view Vista raíz del fragment desde la que se resuelven los IDs
     */
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

    /**
     * Configura los dos RecyclerView del fragment:
     * <ul>
     *   <li>{@code rvMensajes} — conversación del chat activo.</li>
     *   <li>{@code rvMensajesPendiente} — mensajes del chat mientras está en estado PENDIENTE.</li>
     * </ul>
     * Ambos usan {@link MensajeAdapter} inicializado con el UID del cliente para
     * diferenciar visualmente los mensajes propios de los del administrador.
     */
    private void configurarRecyclers() {
        String uid = clienteUid != null ? clienteUid : "";

        mensajeAdapter = new MensajeAdapter(uid);
        rvMensajes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMensajes.setAdapter(mensajeAdapter);

        mensajePendienteAdapter = new MensajeAdapter(uid);
        rvMensajesPendiente.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMensajesPendiente.setAdapter(mensajePendienteAdapter);
    }

    /**
     * Decide el flujo de carga inicial según el contexto con el que fue abierto el fragment:
     * <ul>
     *   <li>Sin UID de cliente — muestra el empty state de membresía directamente.</li>
     *   <li>Con centroId y adminId ya resueltos (venimos de un centro) — muestra el nombre
     *       del administrador y arranca la escucha de chats.</li>
     *   <li>Sin centroId ni adminId (acceso desde menú general) — resuelve primero la membresía
     *       activa del cliente antes de arrancar la escucha.</li>
     * </ul>
     */
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

    /**
     * Obtiene las membresías del cliente y selecciona la primera membresía ACTIVA
     * y no caducada para extraer el centroId.
     * <p>
     * Si no existe ninguna membresía válida, muestra el empty state de membresía.
     * Si se encuentra una activa, resuelve el nombre del administrador del centro
     * mediante {@link #resolverNombreAdmin(String)} y arranca la escucha de chats.
     */
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

    /**
     * Resuelve el nombre completo del administrador del centro a partir de su UID.
     * Primero obtiene el centro para extraer {@code adminUid}, luego consulta
     * el nodo Persons de Firebase para componer nombre y apellidos.
     * Si el nodo no existe, asigna "Soporte" como nombre de fallback.
     *
     * @param cId ID del centro deportivo cuyo administrador se quiere resolver
     */
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

    /**
     * Escucha en tiempo real todos los chats del cliente y filtra los que pertenecen
     * al centro actual. Determina el chat relevante priorizando los estados abiertos
     * (PENDIENTE o ACTIVO) sobre el CERRADO más reciente.
     * <p>
     * Transiciones gestionadas:
     * <ul>
     *   <li>Chat ACTIVO recién recibido sin chat anterior — arranca la escucha de mensajes.</li>
     *   <li>Chat que pasa de PENDIENTE a ACTIVO — notifica al cliente y arranca mensajes.</li>
     * </ul>
     * Tras cada emisión actualiza la vista de estado mediante {@link #mostrarEstadoChat()}.
     */
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

    /**
     * Actualiza la vista de estado del chat según el valor de {@code chatActual}:
     * <ul>
     *   <li>{@code null} — muestra el formulario de nueva solicitud.</li>
     *   <li>PENDIENTE — muestra el panel de espera y carga los mensajes de forma puntual.</li>
     *   <li>ACTIVO — muestra el panel de conversación y conecta el botón de envío.</li>
     *   <li>CERRADO — muestra el panel de chat cerrado y permite abrir una nueva solicitud.</li>
     * </ul>
     */
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

    /**
     * Inicia la escucha en tiempo real de mensajes con el guard {@code chatIdEscuchando}.
     * Si ya estamos escuchando este chat no destruimos el ChildEventListener.
     * <p>
     * Tras recibir nuevos mensajes desplaza el RecyclerView al último elemento
     * para mantener el scroll al final de la conversación.
     *
     * @param chatId ID del chat activo cuyos mensajes se van a escuchar
     */
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

    /**
     * Carga puntual de mensajes para chats en estado PENDIENTE (sin listener continuo).
     * A diferencia de {@link #escucharMensajes(String)}, no mantiene la conexión abierta
     * ya que un chat pendiente no admite nuevos mensajes hasta ser aceptado por el administrador.
     *
     * @param chatId ID del chat pendiente cuyos mensajes se van a cargar
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
                                mensajePendienteAdapter.submitList(mensajes));
                    }
                    @Override
                    public void onError(String mensaje) {}
                });
    }

    /**
     * Cancela el ChildEventListener de mensajes activo y limpia el guard
     * {@code chatIdEscuchando} para que el próximo chat pueda arrancar su listener
     * desde cero sin restricciones.
     */
    private void pararMensajesListener() {
        if (mensajesListener != null && chatIdEscuchando != null)
            soporteService.cancelarListenerMensajes(chatIdEscuchando, mensajesListener);
        mensajesListener = null;
        chatIdEscuchando = null;
    }

    /**
     * Envía la solicitud de soporte inicial creando un nuevo chat en Firebase.
     * Valida que el campo del primer mensaje no esté vacío y que todos los datos
     * necesarios (clienteUid, centroId, adminId) estén disponibles antes de operar.
     * Deshabilita el botón durante la operación para evitar doble envío.
     */
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

    /**
     * Envía el mensaje escrito por el cliente al chat activo.
     * No hace nada si el campo de texto está vacío o si {@code clienteUid} es nulo.
     * Limpia el campo de texto tras un envío exitoso.
     *
     * @param chatId ID del chat activo al que se enviará el mensaje
     */
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

    /**
     * Oculta todos los layouts de estado y muestra únicamente el indicado.
     * Centraliza la lógica de visibilidad para evitar repetir la secuencia
     * de GONE/VISIBLE en cada punto del fragment que necesite cambiar de estado.
     *
     * @param visible Layout que debe quedar visible tras la llamada
     */
    private void mostrarLayout(View visible) {
        layoutSinMembresia.setVisibility(View.GONE);
        layoutSolicitarChat.setVisibility(View.GONE);
        layoutChatPendiente.setVisibility(View.GONE);
        layoutChatActivo.setVisibility(View.GONE);
        layoutChatCerrado.setVisibility(View.GONE);
        visible.setVisibility(View.VISIBLE);
    }

    /**
     * Oculta el spinner de carga y muestra el contenido principal del fragment.
     * Incluye una comprobación de {@code isAdded()} para evitar actualizaciones de vista
     * cuando el fragment ya no está adjunto a su actividad.
     */
    private void mostrarContenido() {
        if (!isAdded()) return;
        layoutCargando.setVisibility(View.GONE);
        layoutContenido.setVisibility(View.VISIBLE);
    }

    /**
     * Muestra un {@link Snackbar} con el texto centrado horizontalmente.
     * Incluye comprobaciones de seguridad para evitar llamadas cuando el fragment
     * no está adjunto o su vista ha sido destruida.
     *
     * @param message Mensaje a mostrar al usuario
     */
    private void showSnackbar(String message) {
        if (!isAdded() || getView() == null) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    /**
     * Aplica alineación centrada al texto del {@link Snackbar} proporcionado.
     * Extraído como método auxiliar para reutilizarlo tanto en los Snackbars simples
     * como en los de confirmación con acción.
     *
     * @param snackbar Snackbar cuyo texto se va a centrar
     */
    private void centrarTextoSnackbar(Snackbar snackbar) {
        TextView tv = snackbar.getView()
                .findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }
}