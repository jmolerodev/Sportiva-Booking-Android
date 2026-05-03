package com.example.sportiva_booking_android.v2.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.ReservasDiaAdapter;
import com.example.sportiva_booking_android.v2.adapters.ReservasHistorialAdapter;
import com.example.sportiva_booking_android.v2.adapters.ReservasPendientesAdapter;
import com.example.sportiva_booking_android.v2.enums.EstadoReserva;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.models.Booking;
import com.example.sportiva_booking_android.v2.models.Session;
import com.example.sportiva_booking_android.v2.services.BookingService;
import com.example.sportiva_booking_android.v2.services.SessionService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fragment de historial y gestión de reservas del cliente.
 *
 * Puerto directo del componente Angular ClienteSessions al stack Android + Java.
 * Mantiene la misma lógica de dos listeners Firebase en paralelo (sesiones + reservas)
 * con un spinner global que se oculta solo cuando ambos completan, igual que los
 * flags loadingReservas / loadingSesiones del componente Angular.
 *
 * El enriquecimiento reserva → sesión se hace en los adapters cruzando por sesionId,
 * evitando modelos auxiliares (opción B acordada).
 */
public class ClienteSessionsFragment extends Fragment
        implements ReservasDiaAdapter.OnCancelarReservaListener,
        ReservasHistorialAdapter.OnEliminarReservaListener {

    private static final String ARG_ROL = "ROL";

    /* ── Vistas ──────────────────────────────────────────────────────── */
    private android.widget.LinearLayout layoutCargando;
    private android.widget.LinearLayout layoutContenido;
    private ImageButton                 btnMesAnterior;
    private ImageButton                 btnMesSiguiente;
    private TextView                    tvMesActual;
    private GridLayout                  gridCalendario;
    private TextView                    tvFechaSeleccionada;
    private TextView                    tvReservasDiaVacio;
    private RecyclerView                recyclerReservasDia;
    private TextView                    tvPendientesVacio;
    private RecyclerView                recyclerPendientes;
    private TextView                    tvHistorialVacio;
    private RecyclerView                recyclerHistorial;
    private Button                      btnVolverHome;

    /* ── Servicios ───────────────────────────────────────────────────── */
    private BookingService bookingService;
    private SessionService sessionService;

    /* ── Estado ──────────────────────────────────────────────────────── */
    private Rol    rolUsuario;
    private String clienteUid;

    private Calendar fechaSeleccionada = Calendar.getInstance();
    private Calendar mesActual         = Calendar.getInstance();

    /*
     * Dos caches locales en paralelo — equivalen a todasLasSesiones y
     * todasLasReservas del componente Angular.
     * Los adapters cruzan por sesionId en cada bind() en lugar de usar
     * un modelo auxiliar enriquecido.
     */
    private final List<Session> todasLasSesiones = new ArrayList<>();
    private final List<Booking> todasLasReservas = new ArrayList<>();

    /* Listas derivadas que se pasan a los adapters */
    private final List<Booking> reservasDelDia   = new ArrayList<>();
    private final List<Booking> reservasPendientes = new ArrayList<>();
    private final List<Booking> reservasHistorial  = new ArrayList<>();

    /*
     * Set de claves YYYY-M-D de días con al menos una reserva CONFIRMADA.
     * Equivale a diasConReserva del componente Angular.
     */
    private final Set<String> diasConReserva = new HashSet<>();

    /* Adapters */
    private ReservasDiaAdapter        adapterDia;
    private ReservasPendientesAdapter adapterPendientes;
    private ReservasHistorialAdapter  adapterHistorial;

    /*
     * Listeners activos de Firebase — se guardan para cancelarlos en onDestroyView
     * y evitar fugas de memoria, igual que destroy$ en Angular.
     */
    private ValueEventListener listenerReservas;
    private ValueEventListener listenerSesiones;

    /*
     * Flags de carga asíncrona — equivalen a loadingReservas / loadingSesiones
     * del componente Angular. El spinner se oculta solo cuando ambos son false.
     */
    private boolean loadingReservas  = true;
    private boolean loadingSesiones  = true;

    private final SimpleDateFormat sdfMes =
            new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
    private final SimpleDateFormat sdfDia =
            new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));


    /* ── Factoría ────────────────────────────────────────────────────── */

    /**
     * Método de factoría. Pasamos el rol por Bundle igual que el resto de fragments.
     *
     * @param rol Rol del usuario autenticado
     * @return Instancia configurada del fragment
     */
    public static ClienteSessionsFragment newInstance(Rol rol) {
        ClienteSessionsFragment fragment = new ClienteSessionsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROL, rol.name());
        fragment.setArguments(args);
        return fragment;
    }


    /* ── Ciclo de vida ───────────────────────────────────────────────── */

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cliente_sessions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recuperarRol();

        clienteUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        inicializarServicios();
        inicializarVistas(view);
        configurarRecyclers();
        configurarListeners();
        generarCalendario();
        actualizarCabeceraDia();

        if (clienteUid != null) {
            escucharSesiones();
            escucharReservas();
        }
    }

    /**
     * Cancela ambos listeners de Firebase al destruir la vista para evitar
     * fugas de memoria. Equivale a destroy$.next() + destroy$.complete() en Angular.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerReservas != null) {
            bookingService.cancelarListenerReservas(listenerReservas);
        }
        if (listenerSesiones != null) {
            sessionService.cancelarListenerSesiones(listenerSesiones);
        }
    }


    /* ── Inicialización ──────────────────────────────────────────────── */

    /**
     * Recupera el rol del Bundle de argumentos.
     * Si no se encuentra o el valor no es válido usa CLIENTE como fallback.
     */
    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuario = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.CLIENTE.name())
                );
            } catch (IllegalArgumentException e) {
                rolUsuario = Rol.CLIENTE;
            }
        } else {
            rolUsuario = Rol.CLIENTE;
        }
    }

    private void inicializarServicios() {
        bookingService = new BookingService();
        sessionService = new SessionService();
    }

    private void inicializarVistas(View view) {
        layoutCargando      = view.findViewById(R.id.layoutCargandoClienteSessions);
        layoutContenido     = view.findViewById(R.id.layoutContenidoClienteSessions);
        btnMesAnterior      = view.findViewById(R.id.btnMesAnteriorCliente);
        btnMesSiguiente     = view.findViewById(R.id.btnMesSiguienteCliente);
        tvMesActual         = view.findViewById(R.id.tvMesActualCliente);
        gridCalendario      = view.findViewById(R.id.gridCalendarioCliente);
        tvFechaSeleccionada = view.findViewById(R.id.tvFechaSeleccionadaCliente);
        tvReservasDiaVacio  = view.findViewById(R.id.tvReservasDiaVacio);
        recyclerReservasDia = view.findViewById(R.id.recyclerReservasDia);
        tvPendientesVacio   = view.findViewById(R.id.tvReservasPendientesVacio);
        recyclerPendientes  = view.findViewById(R.id.recyclerReservasPendientes);
        tvHistorialVacio    = view.findViewById(R.id.tvHistorialVacioCliente);
        recyclerHistorial   = view.findViewById(R.id.recyclerHistorialCliente);
        btnVolverHome       = view.findViewById(R.id.btnVolverHomeCliente);

        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    private void configurarRecyclers() {
        adapterDia = new ReservasDiaAdapter(reservasDelDia, todasLasSesiones, this);
        recyclerReservasDia.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerReservasDia.setAdapter(adapterDia);

        adapterPendientes = new ReservasPendientesAdapter(reservasPendientes, todasLasSesiones);
        recyclerPendientes.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPendientes.setAdapter(adapterPendientes);

        adapterHistorial = new ReservasHistorialAdapter(reservasHistorial, todasLasSesiones, this);
        recyclerHistorial.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerHistorial.setAdapter(adapterHistorial);
    }

    private void configurarListeners() {
        btnMesAnterior.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, -1);
            fechaSeleccionada = (Calendar) mesActual.clone();
            generarCalendario();
            reconstruirReservasDelDia();
            actualizarCabeceraDia();
        });

        btnMesSiguiente.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, 1);
            fechaSeleccionada = (Calendar) mesActual.clone();
            generarCalendario();
            reconstruirReservasDelDia();
            actualizarCabeceraDia();
        });

        btnVolverHome.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });
    }


    /* ── Listeners Firebase ──────────────────────────────────────────── */

    /**
     * Escucha en tiempo real todas las sesiones.
     * Equivale a escucharSesiones() del componente Angular.
     * Guarda el listener para cancelarlo en onDestroyView.
     */
    private void escucharSesiones() {
        listenerSesiones = sessionService.escucharTodasLasSesiones(new SessionService.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sesiones) {
                if (!isAdded()) return;
                todasLasSesiones.clear();
                todasLasSesiones.addAll(sesiones);
                reconstruirVistas();
                loadingSesiones = false;
                checkLoading();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                loadingSesiones = false;
                checkLoading();
            }
        });
    }

    /**
     * Escucha en tiempo real las reservas del cliente autenticado.
     * Equivale a escucharReservas() del componente Angular.
     * Guarda el listener para cancelarlo en onDestroyView.
     */
    private void escucharReservas() {
        listenerReservas = bookingService.escucharReservasByCliente(clienteUid,
                new BookingService.BookingsCallback() {
                    @Override
                    public void onBookingsObtenidas(List<Booking> bookings) {
                        if (!isAdded()) return;
                        todasLasReservas.clear();
                        todasLasReservas.addAll(bookings);
                        reconstruirVistas();
                        loadingReservas = false;
                        checkLoading();
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (!isAdded()) return;
                        loadingReservas = false;
                        checkLoading();
                    }
                });
    }


    /* ── Reconstrucción de vistas ────────────────────────────────────── */

    /**
     * Punto de entrada central que reconstruye todas las vistas derivadas.
     * Equivale a reconstruirVistas() del componente Angular.
     * Se llama cada vez que Firebase emite nuevos datos de sesiones o reservas.
     */
    private void reconstruirVistas() {
        reconstruirDiasConReserva();
        reconstruirReservasDelDia();
        reconstruirPendientesEHistorial();
        generarCalendario();
    }

    /**
     * Reconstruye el Set de claves YYYY-M-D de días con reserva CONFIRMADA.
     * Equivale a reconstruirDiasConReserva() del componente Angular.
     */
    private void reconstruirDiasConReserva() {
        diasConReserva.clear();
        for (Booking r : todasLasReservas) {
            if (r.getEstado() == EstadoReserva.CONFIRMADA) {
                diasConReserva.add(claveDia(r.getFecha()));
            }
        }
    }

    /**
     * Filtra las reservas CONFIRMADAS del día seleccionado y notifica al adapter.
     * Equivale a reconstruirReservasDelDia() del componente Angular.
     */
    private void reconstruirReservasDelDia() {
        String claveDiaSeleccionado = claveDiaDesdeCalendar(fechaSeleccionada);

        reservasDelDia.clear();
        for (Booking r : todasLasReservas) {
            if (r.getEstado() == EstadoReserva.CONFIRMADA
                    && claveDia(r.getFecha()).equals(claveDiaSeleccionado)) {
                reservasDelDia.add(r);
            }
        }

        /* Orden por horaInicio cruzando con la sesión — equivale al sort de Angular */
        reservasDelDia.sort((a, b) -> {
            String hA = getSesionHoraInicio(a.getSesionId());
            String hB = getSesionHoraInicio(b.getSesionId());
            return hA.compareTo(hB);
        });

        if (isAdded()) {
            adapterDia.notifyDataSetChanged();
            actualizarEstadoVacioReservasDia();
        }
    }

    /**
     * Separa las reservas en pendientes (futuras confirmadas) e historial
     * (pasadas o canceladas). Equivale a reconstruirPendientesEHistorial()
     * del componente Angular, incluyendo la lógica de getFechaFin().
     */
    private void reconstruirPendientesEHistorial() {
        long ahora = System.currentTimeMillis();

        reservasPendientes.clear();
        reservasHistorial.clear();

        for (Booking r : todasLasReservas) {
            Session sesion = getSesionById(r.getSesionId());
            long fechaFin  = getFechaFin(r, sesion);

            if (r.getEstado() == EstadoReserva.CONFIRMADA && fechaFin > ahora) {
                reservasPendientes.add(r);
            } else if (r.getEstado() == EstadoReserva.CANCELADA || fechaFin <= ahora) {
                reservasHistorial.add(r);
            }
        }

        /* Pendientes: orden ascendente por fecha */
        reservasPendientes.sort((a, b) -> Long.compare(a.getFecha(), b.getFecha()));

        /* Historial: orden descendente por fecha */
        reservasHistorial.sort((a, b) -> Long.compare(b.getFecha(), a.getFecha()));

        if (isAdded()) {
            adapterPendientes.notifyDataSetChanged();
            adapterHistorial.notifyDataSetChanged();
            actualizarEstadoVacioPendientes();
            actualizarEstadoVacioHistorial();
        }
    }


    /* ── Acciones del cliente ────────────────────────────────────────── */

    /**
     * Cancela una reserva confirmada con Snackbar de confirmación.
     * Decrementa aforoActual de la sesión de forma atómica.
     * Equivale a cancelarReserva() del componente Angular.
     */
    @Override
    public void onCancelarReservaClick(Booking reserva, Session sesion) {
        if (reserva.getId() == null || sesion == null) return;

        Snackbar snackbar = Snackbar.make(requireView(),
                "¿Confirmas la cancelación? La plaza quedará libre.",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("CANCELAR", v -> {
            int aforoNuevo = Math.max(0, sesion.getAforoActual() - 1);
            bookingService.cancelarReserva(
                    reserva.getId(),
                    reserva.getSesionId(),
                    aforoNuevo,
                    new BookingService.WriteCallback() {
                        @Override
                        public void onExito() {
                            if (!isAdded()) return;
                            showSnackbar("Reserva cancelada correctamente");
                        }

                        @Override
                        public void onError(String mensaje) {
                            if (!isAdded()) return;
                            showSnackbar("Error al cancelar la reserva");
                        }
                    });
        });
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    /**
     * Elimina permanentemente un registro del historial con confirmación.
     * Disponible para CANCELADA y FINALIZADA.
     * Equivale a eliminarReserva() del componente Angular.
     */
    @Override
    public void onEliminarReservaClick(Booking reserva) {
        if (reserva.getId() == null) return;

        Snackbar snackbar = Snackbar.make(requireView(),
                "¿Eliminar este registro? Esta acción no se puede deshacer.",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("ELIMINAR", v ->
                bookingService.eliminarReserva(reserva.getId(),
                        new BookingService.WriteCallback() {
                            @Override
                            public void onExito() {
                                if (!isAdded()) return;
                                showSnackbar("Registro eliminado");
                            }

                            @Override
                            public void onError(String mensaje) {
                                if (!isAdded()) return;
                                showSnackbar("Error al eliminar el registro");
                            }
                        }));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }


    /* ── Calendario ──────────────────────────────────────────────────── */

    /**
     * Genera la cuadrícula del calendario para el mes actual.
     * Equivale a generarCalendario() del componente Angular.
     * Marca visualmente: hoy, seleccionado, pasado y días con reserva.
     */
    private void generarCalendario() {
        if (!isAdded()) return;

        tvMesActual.setText(capitalizar(sdfMes.format(mesActual.getTime())));
        gridCalendario.removeAllViews();

        Calendar primer = (Calendar) mesActual.clone();
        primer.set(Calendar.DAY_OF_MONTH, 1);

        /* Offset lunes=0 … domingo=6, igual que (primerDia.getDay() + 6) % 7 en Angular */
        int offset    = (primer.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int diasEnMes = mesActual.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < offset; i++) {
            gridCalendario.addView(crearCeldaVacia());
        }

        for (int dia = 1; dia <= diasEnMes; dia++) {
            Calendar diaCalendar = (Calendar) mesActual.clone();
            diaCalendar.set(Calendar.DAY_OF_MONTH, dia);
            gridCalendario.addView(crearCeldaDia(diaCalendar));
        }
    }

    private View crearCeldaVacia() {
        TextView tv = new TextView(requireContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width      = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.height     = dpToPx(40);
        tv.setLayoutParams(params);
        return tv;
    }

    private View crearCeldaDia(Calendar dia) {
        TextView tv = new TextView(requireContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width      = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.height     = dpToPx(40);
        params.setMargins(2, 2, 2, 2);
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);
        tv.setText(String.valueOf(dia.get(Calendar.DAY_OF_MONTH)));
        tv.setTextSize(13);

        if (esHoy(dia)) {
            tv.setBackgroundResource(R.drawable.day_today);
            tv.setTextColor(getResources().getColor(R.color.white, null));
        } else if (esSeleccionado(dia)) {
            tv.setBackgroundResource(R.drawable.day_selected);
            tv.setTextColor(getResources().getColor(R.color.white, null));
        } else if (esPasado(dia)) {
            tv.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
        } else {
            tv.setTextColor(getResources().getColor(R.color.black, null));
        }

        /*
         * Indicador de día con reserva — equivale al diasConReserva.has() de Angular.
         * A diferencia del fragment del profesional (que usa punto bajo el número),
         * aquí también usamos dot_sesion para mantener consistencia visual.
         */
        if (diasConReserva.contains(claveDiaDesdeCalendar(dia))) {
            tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.dot_sesion);
            tv.setCompoundDrawablePadding(0);
        }

        /* El cliente puede seleccionar cualquier día incluidos los pasados
         * para consultar su historial — igual que en el componente Angular */
        tv.setOnClickListener(v -> {
            fechaSeleccionada = (Calendar) dia.clone();
            generarCalendario();
            reconstruirReservasDelDia();
            actualizarCabeceraDia();
        });

        return tv;
    }


    /* ── Utilidades ──────────────────────────────────────────────────── */

    /**
     * Calcula el timestamp de fin de sesión.
     * Puerto exacto de getFechaFin() del componente Angular.
     */
    private long getFechaFin(Booking reserva, Session sesion) {
        String horaFin = (sesion != null && sesion.getHoraFin() != null)
                ? sesion.getHoraFin() : "23:59";
        String[] partes = horaFin.split(":");
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(reserva.getFecha());
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(partes[0]));
        cal.set(Calendar.MINUTE,      Integer.parseInt(partes[1]));
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Busca una sesión en la cache local por su id. */
    private Session getSesionById(String sesionId) {
        if (sesionId == null) return null;
        for (Session s : todasLasSesiones) {
            if (sesionId.equals(s.getId())) return s;
        }
        return null;
    }

    /** Devuelve la horaInicio de la sesión vinculada a sesionId, o "" si no existe. */
    private String getSesionHoraInicio(String sesionId) {
        Session s = getSesionById(sesionId);
        return (s != null && s.getHoraInicio() != null) ? s.getHoraInicio() : "";
    }

    /**
     * Clave YYYY-M-D desde timestamp epoch.
     * Equivale a claveDia() del componente Angular.
     */
    private String claveDia(long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH)
                + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /** Clave YYYY-M-D desde Calendar. */
    private String claveDiaDesdeCalendar(Calendar cal) {
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH)
                + "-" + cal.get(Calendar.DAY_OF_MONTH);
    }

    private boolean esHoy(Calendar dia) {
        Calendar hoy = Calendar.getInstance();
        return dia.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)
                && dia.get(Calendar.YEAR) == hoy.get(Calendar.YEAR);
    }

    private boolean esSeleccionado(Calendar dia) {
        return dia.get(Calendar.DAY_OF_YEAR) == fechaSeleccionada.get(Calendar.DAY_OF_YEAR)
                && dia.get(Calendar.YEAR) == fechaSeleccionada.get(Calendar.YEAR);
    }

    private boolean esPasado(Calendar dia) {
        Calendar hoy = Calendar.getInstance();
        hoy.set(Calendar.HOUR_OF_DAY, 0);
        hoy.set(Calendar.MINUTE, 0);
        hoy.set(Calendar.SECOND, 0);
        hoy.set(Calendar.MILLISECOND, 0);
        return dia.before(hoy);
    }

    /**
     * Oculta el spinner cuando ambos listeners han completado su primera emisión.
     * Equivale a checkLoading() del componente Angular.
     */
    private void checkLoading() {
        if (!isAdded()) return;
        if (!loadingReservas && !loadingSesiones) {
            layoutCargando.setVisibility(View.GONE);
            layoutContenido.setVisibility(View.VISIBLE);
        }
    }

    private void actualizarCabeceraDia() {
        if (!isAdded()) return;
        tvFechaSeleccionada.setText(capitalizar(sdfDia.format(fechaSeleccionada.getTime())));
    }

    private void actualizarEstadoVacioReservasDia() {
        if (!isAdded()) return;
        if (reservasDelDia.isEmpty()) {
            recyclerReservasDia.setVisibility(View.GONE);
            tvReservasDiaVacio.setVisibility(View.VISIBLE);
        } else {
            recyclerReservasDia.setVisibility(View.VISIBLE);
            tvReservasDiaVacio.setVisibility(View.GONE);
        }
    }

    private void actualizarEstadoVacioPendientes() {
        if (!isAdded()) return;
        if (reservasPendientes.isEmpty()) {
            recyclerPendientes.setVisibility(View.GONE);
            tvPendientesVacio.setVisibility(View.VISIBLE);
        } else {
            recyclerPendientes.setVisibility(View.VISIBLE);
            tvPendientesVacio.setVisibility(View.GONE);
        }
    }

    private void actualizarEstadoVacioHistorial() {
        if (!isAdded()) return;
        if (reservasHistorial.isEmpty()) {
            recyclerHistorial.setVisibility(View.GONE);
            tvHistorialVacio.setVisibility(View.VISIBLE);
        } else {
            recyclerHistorial.setVisibility(View.VISIBLE);
            tvHistorialVacio.setVisibility(View.GONE);
        }
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void centrarTextoSnackbar(Snackbar snackbar) {
        TextView tv = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }

    private void showSnackbar(String message) {
        if (!isAdded()) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }
}