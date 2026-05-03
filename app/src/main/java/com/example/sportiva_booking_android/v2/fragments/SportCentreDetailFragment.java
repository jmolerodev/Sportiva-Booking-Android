package com.example.sportiva_booking_android.v2.fragments;

import android.annotation.SuppressLint;
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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.EstadoChat;
import com.example.sportiva_booking_android.v2.enums.EstadoReserva;
import com.example.sportiva_booking_android.v2.enums.EstadoSesion;
import com.example.sportiva_booking_android.v2.models.Booking;
import com.example.sportiva_booking_android.v2.models.Media;
import com.example.sportiva_booking_android.v2.models.Session;
import com.example.sportiva_booking_android.v2.models.SoporteChat;
import com.example.sportiva_booking_android.v2.models.SportCentre;
import com.example.sportiva_booking_android.v2.services.BookingService;
import com.example.sportiva_booking_android.v2.services.MediaService;
import com.example.sportiva_booking_android.v2.services.MembershipService;
import com.example.sportiva_booking_android.v2.services.SessionService;
import com.example.sportiva_booking_android.v2.services.SoporteService;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SportCentreDetailFragment extends Fragment {

    private static final String ARG_CENTRO_ID = "centroId";

    /*Argumentos*/
    private String centroId;
    private String clienteUid;

    /*Modelos en memoria*/
    private SportCentre   centro          = null;
    private Session       sesionSeleccionada = null;
    private Booking       reservaActual   = null;
    private SoporteChat   chatActual      = null;
    private String        chatActualId    = null;

    /*Cache local de reservas confirmadas del cliente en este centro*/
    private List<Booking> reservasCliente = new ArrayList<>();

    /*Calendario*/
    private Calendar mesActual        = Calendar.getInstance();
    private Calendar fechaSeleccionada = Calendar.getInstance();

    /*Flags de carga asíncrona independiente*/
    private boolean loadingCentro    = true;
    private boolean loadingMembresia = true;
    private boolean loadingMedia     = true;
    private boolean tieneMembresia   = false;
    private boolean loadingReserva   = false;

    /*Servicios*/
    private SportCentreService sportCentreService;
    private MembershipService  membershipService;
    private SessionService     sessionService;
    private MediaService       mediaService;
    private BookingService     bookingService;
    private SoporteService     soporteService;

    /*Listeners activos: se cancelan en onDestroyView*/
    private ValueEventListener listenerReservas = null;
    private ValueEventListener listenerChats    = null;

    /*Elementos de la vista sobre la pantalla de carga*/
    private View layoutCargando;
    private View layoutContenido;

    /*Elementos de la vista del header*/
    private ImageView ivFoto;
    private TextView  tvNombre;
    private TextView  tvDireccion;
    private TextView  tvTelefono;

    /*Elementos de las vistas de secciones de membresía*/
    private LinearLayout sectionSinMembresia;
    private LinearLayout sectionConMembresia;
    private Button       btnContratarMembresia;

    /*Elementos de la vista del calendario*/
    private TextView     tvMesActual;
    private LinearLayout layoutDiasSemana;
    private LinearLayout layoutCalendario;
    private TextView     tvTituloSlots;
    private LinearLayout layoutSlots;

    /*Elementos de la vista sobre los detalles de la Sesion*/
    private CardView  cardDetalleSesion;
    private TextView  tvDetalleTitulo;
    private TextView  tvDetalleDescripcion;
    private TextView  tvDetalleHorario;
    private TextView  tvDetalleAforo;
    private TextView  tvAforoCompleto;
    private Button    btnReservar;
    private Button    btnCancelarReserva;
    private Button    btnCerrarDetalle;

    /*Elementos de la vista Soporte*/
    private LinearLayout      layoutSolicitudChat;
    private LinearLayout      layoutChatPendiente;
    private LinearLayout      layoutChatActivo;
    private LinearLayout      layoutChatCerrado;
    private TextInputEditText etPrimerMensaje;
    private Button            btnEnviarSolicitud;
    private Button            btnIrAlChat;
    private Button            btnNuevaSolicitud;

    /*Elementos de la vista Multimedia*/
    private TextView     tvSinVideos;
    private LinearLayout layoutVideos;


    /**
     * Crea una nueva instancia del fragment pasando el centroId por Bundle.
     *
     * @param centroId UID del centro deportivo a mostrar
     * @return Instancia configurada del fragment
     */
    public static SportCentreDetailFragment newInstance(String centroId) {
        SportCentreDetailFragment fragment = new SportCentreDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CENTRO_ID, centroId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Recuperamos el centroId del Bundle*/
        if (getArguments() != null) {
            centroId = getArguments().getString(ARG_CENTRO_ID);
        }

        /*UID del cliente autenticado*/
        clienteUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        /*Instanciamos los servicios*/
        sportCentreService = new SportCentreService();
        membershipService  = new MembershipService();
        sessionService     = new SessionService();
        mediaService       = new MediaService();
        bookingService     = new BookingService();
        soporteService     = new SoporteService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sport_centre_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        inicializarVistas(view);
        configurarListeners();
        generarCalendario();
        cargarDatos();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        /*Cancelamos los listeners de larga vida para evitar fugas de memoria*/
        bookingService.cancelarListenerReservas(listenerReservas);
        soporteService.cancelarListenerChats(listenerChats);
    }

    /**
     * Enlaza todas las vistas del layout con sus referencias Java.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
     */
    private void inicializarVistas(View view) {
        layoutCargando       = view.findViewById(R.id.layoutCargandoDetalle);
        layoutContenido      = view.findViewById(R.id.layoutContenidoDetalle);

        ivFoto               = view.findViewById(R.id.ivFotoCentroDetalle);
        tvNombre             = view.findViewById(R.id.tvNombreCentroDetalle);
        tvDireccion          = view.findViewById(R.id.tvDireccionCentroDetalle);
        tvTelefono           = view.findViewById(R.id.tvTelefonoCentroDetalle);

        sectionSinMembresia  = view.findViewById(R.id.sectionSinMembresia);
        sectionConMembresia  = view.findViewById(R.id.sectionConMembresia);
        btnContratarMembresia = view.findViewById(R.id.btnContratarMembresia);

        tvMesActual          = view.findViewById(R.id.tvMesActual);
        layoutDiasSemana     = view.findViewById(R.id.layoutDiasSemana);
        layoutCalendario     = view.findViewById(R.id.layoutCalendario);
        tvTituloSlots        = view.findViewById(R.id.tvTituloSlots);
        layoutSlots          = view.findViewById(R.id.layoutSlots);

        cardDetalleSesion    = view.findViewById(R.id.cardDetalleSesion);
        tvDetalleTitulo      = view.findViewById(R.id.tvDetalleTituloSesion);
        tvDetalleDescripcion = view.findViewById(R.id.tvDetalleDescripcionSesion);
        tvDetalleHorario     = view.findViewById(R.id.tvDetalleHorarioSesion);
        tvDetalleAforo       = view.findViewById(R.id.tvDetalleAforoSesion);
        tvAforoCompleto      = view.findViewById(R.id.tvAforoCompleto);
        btnReservar          = view.findViewById(R.id.btnReservarSesion);
        btnCancelarReserva   = view.findViewById(R.id.btnCancelarReserva);
        btnCerrarDetalle     = view.findViewById(R.id.btnCerrarDetalleSesion);

        layoutSolicitudChat  = view.findViewById(R.id.layoutSolicitudChat);
        layoutChatPendiente  = view.findViewById(R.id.layoutChatPendiente);
        layoutChatActivo     = view.findViewById(R.id.layoutChatActivo);
        layoutChatCerrado    = view.findViewById(R.id.layoutChatCerrado);
        etPrimerMensaje      = view.findViewById(R.id.etPrimerMensaje);
        btnEnviarSolicitud   = view.findViewById(R.id.btnEnviarSolicitud);
        btnIrAlChat          = view.findViewById(R.id.btnIrAlChat);
        btnNuevaSolicitud    = view.findViewById(R.id.btnNuevaSolicitud);

        tvSinVideos          = view.findViewById(R.id.tvSinVideos);
        layoutVideos         = view.findViewById(R.id.layoutVideos);

        /*Arranque con pantalla de carga activa*/
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    /**
     * Asigna los listeners a todos los botones del fragment.
     */
    private void configurarListeners() {

        btnContratarMembresia.setOnClickListener(v -> abrirContratarMembresia());
        btnNuevaSolicitud.setOnClickListener(v -> mostrarEstadoSoporte(null));
        btnEnviarSolicitud.setOnClickListener(v -> solicitarChat());
        btnIrAlChat.setOnClickListener(v -> abrirSoporteCliente());
        btnCerrarDetalle.setOnClickListener(v -> cerrarDetalleSesion());
        btnReservar.setOnClickListener(v -> reservarSesion());
        btnCancelarReserva.setOnClickListener(v -> confirmarCancelarReserva());

        requireView().findViewById(R.id.btnMesAnterior)
                .setOnClickListener(v -> {
                    mesActual.add(Calendar.MONTH, -1);
                    fechaSeleccionada = (Calendar) mesActual.clone();
                    generarCalendario();
                    cargarSesionesDelDia();
                });

        requireView().findViewById(R.id.btnMesSiguiente)
                .setOnClickListener(v -> {
                    mesActual.add(Calendar.MONTH, 1);
                    fechaSeleccionada = (Calendar) mesActual.clone();
                    generarCalendario();
                    cargarSesionesDelDia();
                });
    }

    /**
     * Orquesta las tres cargas asíncronas independientes que alimentan el fragment:
     * datos del centro, verificación de membresía y multimedia.
     * El spinner global se oculta cuando las tres han finalizado mediante checkLoading().
     */
    private void cargarDatos() {
        if (centroId == null || clienteUid == null) return;
        cargarCentro();
        verificarMembresia();
        cargarMedia();
    }

    /**
     * Recupera los datos del centro deportivo desde Firebase y pinta el header.
     * Una vez finalizada descuenta su flag en checkLoading().
     */
    private void cargarCentro() {
        sportCentreService.getSportCentreByUid(centroId, centro -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                this.centro = centro;
                if (centro != null) pintarHeader(centro);
                loadingCentro = false;
                checkLoading();
            });
        });
    }

    /**
     * Verifica si el cliente tiene membresía activa y vigente en este centro.
     * Si la tiene, inicia la escucha en tiempo real de reservas y chats.
     * Una vez finalizada descuenta su flag en checkLoading().
     */
    private void verificarMembresia() {
        membershipService.getMembresiaActivaByClienteYCentro(clienteUid, centroId, membresia -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                tieneMembresia = (membresia != null);

                if (tieneMembresia) {
                    escucharReservasCliente();
                    escucharChats();
                    cargarSesionesDelDia();
                }

                loadingMembresia = false;
                checkLoading();
            });
        });
    }

    /**
     * Carga el contenido multimedia vinculado al centro para la galería.
     * Una vez finalizada descuenta su flag en checkLoading().
     */
    private void cargarMedia() {
        mediaService.getMediaByCentro(centroId, new MediaService.MediaListCallback() {
            @Override
            public void onSuccess(List<Media> mediaList) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    renderizarVideos(mediaList);
                    loadingMedia = false;
                    checkLoading();
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    loadingMedia = false;
                    checkLoading();
                });
            }
        });
    }

    /**
     * Escucha en tiempo real todas las reservas confirmadas del cliente en este centro.
     * Mantiene la cache local actualizada y refresca el estado de la sesión
     * seleccionada si estaba siendo visualizada cuando llegó la actualización.
     * El listener se cancela en onDestroyView para evitar fugas de memoria.
     */
    private void escucharReservasCliente() {
        listenerReservas = bookingService.escucharReservasByCliente(clienteUid, new BookingService.BookingsCallback() {
            @Override
            public void onBookingsObtenidas(List<Booking> bookings) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    reservasCliente.clear();
                    for (Booking b : bookings) {
                        if (centroId.equals(b.getCentroId())
                                && EstadoReserva.CONFIRMADA.equals(b.getEstado())) {
                            reservasCliente.add(b);
                        }
                    }
                    /*Refrescamos el estado de reserva si hay sesión activa en el panel*/
                    if (sesionSeleccionada != null) actualizarEstadoReservaActual();
                });
            }

            @Override
            public void onError(String mensaje) { /* silencioso — no bloqueante */ }
        });
    }

    /**
     * Escucha en tiempo real los chats de soporte del cliente filtrados por este centro.
     * Prioriza PENDIENTE o ACTIVO sobre CERRADO para mostrar el estado vigente.
     * El listener se cancela en onDestroyView para evitar fugas de memoria.
     */
    private void escucharChats() {
        listenerChats = soporteService.escucharChatsByCliente(clienteUid, new SoporteService.ChatsCallback() {
            @Override
            public void onChatsObtenidos(List<SoporteChat> chats) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {

                    /* Filtramos los chats de este centro concreto */
                    List<SoporteChat> delCentro = new ArrayList<>();
                    List<String>      ids       = new ArrayList<>();
                    for (int i = 0; i < chats.size(); i++) {
                        SoporteChat c = chats.get(i);
                        if (centroId.equals(c.getCentroId())) {
                            delCentro.add(c);
                            ids.add(c.getId());
                        }
                    }

                    /*Priorizamos PENDIENTE o ACTIVO sobre CERRADO*/
                    SoporteChat abierto = null;
                    String      abiertoId = null;
                    for (int i = 0; i < delCentro.size(); i++) {
                        SoporteChat c = delCentro.get(i);
                        if (EstadoChat.PENDIENTE.equals(c.getEstado())
                                || EstadoChat.ACTIVO.equals(c.getEstado())) {
                            abierto   = c;
                            abiertoId = ids.get(i);
                            break;
                        }
                    }

                    if (abierto != null) {
                        chatActual   = abierto;
                        chatActualId = abiertoId;
                    } else if (!delCentro.isEmpty()) {
                        chatActual   = delCentro.get(delCentro.size() - 1);
                        chatActualId = ids.get(ids.size() - 1);
                    } else {
                        chatActual   = null;
                        chatActualId = null;
                    }

                    mostrarEstadoSoporte(chatActual);
                });
            }

            @Override
            public void onError(String mensaje) { /* silencioso — no bloqueante */ }
        });
    }


    /**
     * Pinta el header del fragment con los datos básicos del centro.
     *
     * @param centro Centro deportivo cargado desde Firebase
     */
    private void pintarHeader(SportCentre centro) {
        tvNombre.setText(centro.getNombre());
        tvDireccion.setText(centro.getDireccion());
        tvTelefono.setText(centro.getTelefono() != null
                ? centro.getTelefono() : "Sin teléfono");

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
    }


    /**
     * Muestra la sección correcta según si el cliente tiene membresía activa.
     * Se invoca desde checkLoading() una vez que todas las cargas han finalizado.
     */
    private void pintarSecciones() {
        if (tieneMembresia) {
            sectionSinMembresia.setVisibility(View.GONE);
            sectionConMembresia.setVisibility(View.VISIBLE);
        } else {
            sectionSinMembresia.setVisibility(View.VISIBLE);
            sectionConMembresia.setVisibility(View.GONE);
        }
    }

    /**
     * Genera la cuadrícula del calendario para el mes actualmente visualizado.
     * Rellena con celdas vacías las posiciones anteriores al primer día de la semana
     * y construye cada fila de 7 celdas dinámicamente.
     */
    private void generarCalendario() {
        if (!isAdded()) return;

        /*Cabecera con el nombre del mes y año*/
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
        tvMesActual.setText(capitalize(sdf.format(mesActual.getTime())));

        /* Cabecera de días de la semana */
        layoutDiasSemana.removeAllViews();
        String[] diasSemana = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        for (String dia : diasSemana) {
            TextView tv = new TextView(getContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setGravity(Gravity.CENTER);
            tv.setText(dia);
            tv.setTextSize(11f);
            tv.setTextColor(getResources().getColor(R.color.black, null));
            layoutDiasSemana.addView(tv);
        }

        /*Cuadrícula del mes*/
        layoutCalendario.removeAllViews();

        Calendar cal = (Calendar) mesActual.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        /*Offset: lunes = 0, domingo = 6*/
        int offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int totalDias = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        /*Construimos semana a semana*/
        int dia = 1 - offset;
        while (dia <= totalDias) {
            LinearLayout fila = new LinearLayout(getContext());
            fila.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            fila.setOrientation(LinearLayout.HORIZONTAL);

            for (int col = 0; col < 7; col++, dia++) {
                final int diaActual = dia;
                TextView celda = new TextView(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, dpToPx(36), 1f);
                celda.setLayoutParams(params);
                celda.setGravity(Gravity.CENTER);
                celda.setTextSize(13f);

                if (diaActual < 1 || diaActual > totalDias) {
                    /*Celda vacía fuera del mes*/
                    celda.setText("");
                } else {
                    celda.setText(String.valueOf(diaActual));
                    Calendar diaCal = (Calendar) mesActual.clone();
                    diaCal.set(Calendar.DAY_OF_MONTH, diaActual);

                    /*Días pasados: deshabilitados visualmente*/
                    Calendar hoy = Calendar.getInstance();
                    hoy.set(Calendar.HOUR_OF_DAY, 0);
                    hoy.set(Calendar.MINUTE, 0);
                    hoy.set(Calendar.SECOND, 0);
                    hoy.set(Calendar.MILLISECOND, 0);

                    if (diaCal.before(hoy)) {
                        celda.setTextColor(0xFFBBBBBB);
                    } else {
                        celda.setTextColor(getResources().getColor(R.color.black, null));

                        /*Resaltamos el día seleccionado*/
                        if (mismodia(diaCal, fechaSeleccionada)) {
                            celda.setBackgroundResource(R.drawable.bg_badge_rol);
                            celda.setTextColor(
                                    getResources().getColor(R.color.white, null));
                        }

                        celda.setOnClickListener(v -> {
                            fechaSeleccionada = (Calendar) diaCal.clone();
                            generarCalendario();
                            cerrarDetalleSesion();
                            cargarSesionesDelDia();
                        });
                    }
                }
                fila.addView(celda);
            }
            layoutCalendario.addView(fila);
        }
    }

    /**
     * Carga las sesiones activas del centro para el día seleccionado y
     * construye la lista de slots horarios con su estado correspondiente.
     */
    private void cargarSesionesDelDia() {
        if (centro == null || !tieneMembresia) return;

        Calendar inicioDia = (Calendar) fechaSeleccionada.clone();
        inicioDia.set(Calendar.HOUR_OF_DAY, 0);
        inicioDia.set(Calendar.MINUTE, 0);
        inicioDia.set(Calendar.SECOND, 0);
        inicioDia.set(Calendar.MILLISECOND, 0);

        Calendar finDia = (Calendar) fechaSeleccionada.clone();
        finDia.set(Calendar.HOUR_OF_DAY, 23);
        finDia.set(Calendar.MINUTE, 59);
        finDia.set(Calendar.SECOND, 59);

        sessionService.getSessionsByCentroAndFecha(
                centroId,
                inicioDia.getTimeInMillis(),
                finDia.getTimeInMillis(),
                new SessionService.SessionsCallback() {
                    @Override
                    public void onSuccess(List<Session> sesiones) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> renderizarSlots(sesiones));
                    }

                    @Override
                    public void onError(String error) { /* silencioso */ }
                });
    }

    /**
     * Genera y renderiza los slots horarios del día seleccionado a partir del
     * horario del centro y las sesiones activas encontradas.
     * Cada slot libre muestra solo el horario; cada slot ocupado muestra además
     * el título de la sesión y es pulsable para abrir su detalle.
     *
     * @param sesiones Lista de sesiones activas del día seleccionado
     */
    @SuppressLint("SetTextI18n")
    private void renderizarSlots(List<Session> sesiones) {
        layoutSlots.removeAllViews();

        if (centro == null || centro.getHorario() == null) return;

        String nombreDia = getNombreDia(fechaSeleccionada);
        SportCentre.HorarioDia horarioDia = centro.getHorario().get(nombreDia);

        if (horarioDia == null || !horarioDia.isAbierto()) {
            tvTituloSlots.setVisibility(View.VISIBLE);
            tvTituloSlots.setText("Centro cerrado este día");
            return;
        }

        int hApertura = Integer.parseInt(horarioDia.getApertura().split(":")[0]);
        int hCierre   = Integer.parseInt(horarioDia.getCierre().split(":")[0]);

        Calendar ahora = Calendar.getInstance();
        boolean esHoy  = mismodia(fechaSeleccionada, Calendar.getInstance());

        if (esHoy && ahora.get(Calendar.HOUR_OF_DAY) >= hCierre) {
            tvTituloSlots.setVisibility(View.VISIBLE);
            tvTituloSlots.setText("No hay más slots disponibles hoy");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", new Locale("es", "ES"));
        tvTituloSlots.setVisibility(View.VISIBLE);
        tvTituloSlots.setText("Sesiones — " + capitalize(sdf.format(fechaSeleccionada.getTime())));

        boolean haySlots = false;

        for (int h = hApertura; h < hCierre; h++) {
            if (esHoy && h <= ahora.get(Calendar.HOUR_OF_DAY)) continue;

            haySlots = true;
            String horaInicio = String.format(Locale.getDefault(), "%02d:00", h);
            String horaFin    = String.format(Locale.getDefault(), "%02d:00", h + 1);

            /*Buscamos si hay sesión activa en este slot*/
            Session sesion = null;
            for (Session s : sesiones) {
                if (horaInicio.equals(s.getHoraInicio())
                        && EstadoSesion.ACTIVA.equals(s.getEstado())) {
                    sesion = s;
                    break;
                }
            }

            View itemSlot = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_slot_horario, layoutSlots, false);

            TextView tvHora    = itemSlot.findViewById(R.id.tvHoraSlot);
            TextView tvEstado  = itemSlot.findViewById(R.id.tvEstadoSlot);
            TextView tvTitulo  = itemSlot.findViewById(R.id.tvTituloSesionSlot);

            tvHora.setText(horaInicio + " – " + horaFin);

            if (sesion != null) {
                tvEstado.setText("OCUPADO");
                tvEstado.setTextColor(0xFFE53935);
                tvTitulo.setVisibility(View.VISIBLE);
                tvTitulo.setText(sesion.getTitulo());

                final Session sesionFinal = sesion;
                itemSlot.setOnClickListener(v -> seleccionarSesion(sesionFinal));
            } else {
                tvEstado.setText("LIBRE");
                tvEstado.setTextColor(0xFF2E7D32);
                tvTitulo.setVisibility(View.GONE);
            }

            layoutSlots.addView(itemSlot);
        }

        if (!haySlots) {
            tvTituloSlots.setText("No hay slots disponibles para este día");
        }
    }


    /**
     * Selecciona una sesión del slot pulsado, calcula el estado de reserva actual
     * y muestra el panel de detalle con las acciones disponibles.
     *
     * @param sesion Sesión pulsada por el cliente
     */
    private void seleccionarSesion(Session sesion) {
        sesionSeleccionada = sesion;
        actualizarEstadoReservaActual();
        pintarDetalleSesion();
    }

    /**
     * Actualiza reservaActual a partir de la cache local de reservas confirmadas
     * y la sesión seleccionada activa en el panel de detalle.
     * Se invoca tanto al seleccionar una sesión como al recibir actualizaciones
     * del listener de reservas para mantener la vista siempre sincronizada.
     */
    private void actualizarEstadoReservaActual() {
        if (sesionSeleccionada == null) return;

        reservaActual = null;
        for (Booking b : reservasCliente) {
            if (sesionSeleccionada.getId().equals(b.getSesionId())) {
                reservaActual = b;
                break;
            }
        }
        pintarDetalleSesion();
    }

    /**
     * Pinta el panel de detalle con los datos de la sesión seleccionada
     * y muestra u oculta los botones según el estado de reserva del cliente.
     */
    @SuppressLint("SetTextI18n")
    private void pintarDetalleSesion() {
        if (sesionSeleccionada == null) return;

        cardDetalleSesion.setVisibility(View.VISIBLE);

        tvDetalleTitulo.setText(sesionSeleccionada.getTitulo());
        tvDetalleDescripcion.setText(sesionSeleccionada.getDescripcion());
        tvDetalleHorario.setText(
                sesionSeleccionada.getHoraInicio() + " – " + sesionSeleccionada.getHoraFin());
        tvDetalleAforo.setText(
                "Aforo: " + sesionSeleccionada.getAforoActual()
                        + " / " + sesionSeleccionada.getAforoMax());

        boolean aforoCompleto = sesionSeleccionada.getAforoActual()
                >= sesionSeleccionada.getAforoMax();

        if (reservaActual != null) {
            /*El cliente ya tiene reserva: mostramos solo cancelar*/
            btnReservar.setVisibility(View.GONE);
            btnCancelarReserva.setVisibility(View.VISIBLE);
            tvAforoCompleto.setVisibility(View.GONE);
        } else if (aforoCompleto) {
            /*Sin reserva y aforo completo*/
            btnReservar.setVisibility(View.GONE);
            btnCancelarReserva.setVisibility(View.GONE);
            tvAforoCompleto.setVisibility(View.VISIBLE);
        } else {
            /*Sin reserva y hay plaza*/
            btnReservar.setVisibility(View.VISIBLE);
            btnCancelarReserva.setVisibility(View.GONE);
            tvAforoCompleto.setVisibility(View.GONE);
        }
    }

    /**
     * Cierra el panel de detalle de la sesión seleccionada y limpia el estado.
     */
    private void cerrarDetalleSesion() {
        sesionSeleccionada = null;
        reservaActual      = null;
        cardDetalleSesion.setVisibility(View.GONE);
    }

    /**
     * Reserva la sesión seleccionada para el cliente autenticado.
     * Verifica que no haya reserva previa y que el aforo no esté completo
     * antes de persistir en Firebase de forma atómica.
     */
    private void reservarSesion() {
        if (sesionSeleccionada == null || reservaActual != null || loadingReserva) return;

        boolean aforoCompleto = sesionSeleccionada.getAforoActual()
                >= sesionSeleccionada.getAforoMax();
        if (aforoCompleto) return;

        loadingReserva = true;
        btnReservar.setEnabled(false);

        Booking reserva = new Booking();
        reserva.setSesionId(sesionSeleccionada.getId());
        reserva.setClienteId(clienteUid);
        reserva.setCentroId(centroId);
        reserva.setFecha(sesionSeleccionada.getFecha());
        reserva.setEstado(EstadoReserva.CONFIRMADA);

        int aforoNuevo = sesionSeleccionada.getAforoActual() + 1;

        bookingService.crearReserva(reserva, aforoNuevo, new BookingService.WriteCallback() {
            @Override
            public void onExito() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    loadingReserva = false;
                    btnReservar.setEnabled(true);
                    showSnackbar("¡Reserva confirmada correctamente!");
                });
            }

            @Override
            public void onError(String mensaje) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    loadingReserva = false;
                    btnReservar.setEnabled(true);
                    showSnackbar("Error al realizar la reserva");
                });
            }
        });
    }

    /**
     * Muestra un Snackbar de confirmación antes de cancelar la reserva activa.
     * La cancelación real solo ocurre si el usuario confirma la acción.
     */
    private void confirmarCancelarReserva() {
        if (reservaActual == null || sesionSeleccionada == null || getView() == null) return;

        Snackbar snackbar = Snackbar.make(
                getView(),
                "¿Confirmas la cancelación de esta reserva? La plaza quedará libre.",
                Snackbar.LENGTH_LONG
        );
        snackbar.setAction("CANCELAR RESERVA", v -> cancelarReserva());
        snackbar.setActionTextColor(
                getResources().getColor(android.R.color.holo_red_light, null));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    /**
     * Cancela la reserva activa del cliente para la sesión seleccionada
     * y decrementa el aforoActual de forma atómica en Firebase.
     */
    private void cancelarReserva() {
        if (reservaActual == null || sesionSeleccionada == null) return;

        int aforoNuevo = Math.max(0, sesionSeleccionada.getAforoActual() - 1);

        bookingService.cancelarReserva(
                reservaActual.getId(),
                sesionSeleccionada.getId(),
                aforoNuevo,
                new BookingService.WriteCallback() {
                    @Override
                    public void onExito() {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                showSnackbar("Reserva cancelada correctamente"));
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() ->
                                showSnackbar("Error al cancelar la reserva"));
                    }
                });
    }


    /**
     * Actualiza la sección de soporte mostrando el estado correcto según el chat vigente.
     * Sin chat → formulario de solicitud.
     * PENDIENTE → banner de espera.
     * ACTIVO    → botón para ir al chat.
     * CERRADO   → mensaje de cierre con opción de nueva solicitud.
     *
     * @param chat Chat de soporte vigente del cliente con este centro, o null si no existe
     */
    private void mostrarEstadoSoporte(@Nullable SoporteChat chat) {
        layoutSolicitudChat.setVisibility(View.GONE);
        layoutChatPendiente.setVisibility(View.GONE);
        layoutChatActivo.setVisibility(View.GONE);
        layoutChatCerrado.setVisibility(View.GONE);

        if (chat == null) {
            layoutSolicitudChat.setVisibility(View.VISIBLE);
            return;
        }

        if (EstadoChat.PENDIENTE.equals(chat.getEstado())) {
            layoutChatPendiente.setVisibility(View.VISIBLE);
        } else if (EstadoChat.ACTIVO.equals(chat.getEstado())) {
            layoutChatActivo.setVisibility(View.VISIBLE);
        } else {
            layoutChatCerrado.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Envía la solicitud de chat de soporte al administrador del centro.
     * Solo es posible si el cliente tiene membresía activa y no hay un chat abierto.
     */
    private void solicitarChat() {
        if (centro == null || clienteUid == null || centroId == null) return;

        String texto = etPrimerMensaje.getText() != null
                ? etPrimerMensaje.getText().toString().trim() : "";

        if (texto.isEmpty()) {
            showSnackbar("Escribe tu consulta antes de enviar");
            return;
        }

        btnEnviarSolicitud.setEnabled(false);

        soporteService.solicitarChat(
                centroId,
                clienteUid,
                centro.getAdminUid(),
                texto,
                new SoporteService.WriteCallback() {
                    @Override
                    public void onExito() {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            etPrimerMensaje.setText("");
                            btnEnviarSolicitud.setEnabled(true);
                            showSnackbar("Solicitud enviada. El administrador la revisará en breve");
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            btnEnviarSolicitud.setEnabled(true);
                            showSnackbar("Error al enviar la solicitud de soporte");
                        });
                    }
                });
    }


    /**
     * Renderiza la lista de vídeos del centro inflando item_media_video.xml
     * para cada elemento. Si la lista está vacía muestra el texto informativo.
     *
     * @param videos Lista de objetos Media a mostrar
     */
    private void renderizarVideos(List<Media> videos) {
        layoutVideos.removeAllViews();

        if (videos == null || videos.isEmpty()) {
            tvSinVideos.setVisibility(View.VISIBLE);
            return;
        }

        tvSinVideos.setVisibility(View.GONE);

        for (Media media : videos) {
            View item = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_media, layoutVideos, false);

            TextView tvNombreVideo = item.findViewById(R.id.tvMediaNombre);
            TextView tvDescVideo   = item.findViewById(R.id.tvMediaDescripcion);

            tvNombreVideo.setText(media.getNombre());
            tvDescVideo.setText(media.getDescripcion() != null
                    ? media.getDescripcion() : "");

            layoutVideos.addView(item);
        }
    }


    /**
     * Abre el fragment de contratación de membresía pasando el centroId por Bundle.
     * Se registra con el tag membership_payment para que MainActivity pueda
     * localizarlo desde onNewIntent al recibir el deep link de retorno de PayPal.
     */
    private void abrirContratarMembresia() {
        MembershipPaymentFragment fragment =
                MembershipPaymentFragment.newInstance(centroId);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment, "membership_payment")
                .addToBackStack(null)
                .commit();
    }

    /**
     * Abre el fragment de soporte del cliente para continuar la conversación activa.
     */
    private void abrirSoporteCliente() {
        /*TODO: conectar SoporteClienteFragment cuando esté disponible*/
        showSnackbar("El chat completo estará disponible en breve");
    }


    /**
     * Oculta el spinner global y revela el contenido una vez que las tres
     * cargas asíncronas independientes han finalizado.
     * Pinta las secciones de membresía en el mismo momento.
     */
    private void checkLoading() {
        if (!isAdded()) return;
        if (!loadingCentro && !loadingMembresia && !loadingMedia) {
            pintarSecciones();
            layoutCargando.setVisibility(View.GONE);
            layoutContenido.setVisibility(View.VISIBLE);
        }
    }


    /**
     * Convierte dp a píxeles usando la densidad de pantalla del dispositivo.
     *
     * @param dp Valor en dp a convertir
     * @return Valor equivalente en píxeles
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Devuelve el nombre del día de la semana en español para un Calendar dado.
     * Se usa para consultar el horario del centro por clave de día.
     *
     * @param cal Calendar del que extraer el nombre del día
     * @return Nombre del día en español con la primera letra en mayúscula
     */
    private String getNombreDia(Calendar cal) {
        String[] dias = {"Domingo", "Lunes", "Martes", "Miércoles",
                "Jueves", "Viernes", "Sábado"};
        return dias[cal.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /**
     * Comprueba si dos calendarios representan el mismo día del año.
     *
     * @param a Primer Calendar a comparar
     * @param b Segundo Calendar a comparar
     * @return true si año, mes y día coinciden
     */
    private boolean mismodia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR)         == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH)    == b.get(Calendar.MONTH)
                && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Pone la primera letra de una cadena en mayúscula.
     * Se usa para capitalizar el nombre del mes en la cabecera del calendario.
     *
     * @param texto Cadena a capitalizar
     * @return Cadena con la primera letra en mayúscula
     */
    private String capitalize(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    /**
     * Muestra un Snackbar centrado con el mensaje recibido.
     * Comprueba isAdded() antes de actuar para evitar crashes si el fragment
     * ya no está adjunto a la Activity.
     *
     * @param message Texto a mostrar en el Snackbar
     */
    private void showSnackbar(String message) {
        if (!isAdded() || getView() == null) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    /**
     * Centra el texto del Snackbar horizontalmente para mantener la coherencia
     * visual con el resto de notificaciones del proyecto.
     *
     * @param snackbar Instancia del Snackbar a centrar
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