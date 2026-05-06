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

    /*Vistas*/
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

    /*Servicios*/
    private BookingService bookingService;
    private SessionService sessionService;

    /*Estado*/
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
    private final List<Booking> reservasDelDia    = new ArrayList<>();
    private final List<Booking> reservasPendientes = new ArrayList<>();
    private final List<Booking> reservasHistorial  = new ArrayList<>();

    /*
     * Set de claves YYYY-M-D de días con al menos una reserva CONFIRMADA.
     * Equivale a diasConReserva del componente Angular.
     */
    private final Set<String> diasConReserva = new HashSet<>();

    /*Adapters*/
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
    private boolean loadingReservas = true;
    private boolean loadingSesiones = true;

    private final SimpleDateFormat sdfMes =
            new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));
    private final SimpleDateFormat sdfDia =
            new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));


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
        return inflater.inflate(R.layout.fragment_cliente_sessions, container, false);
    }

    /**
     * Punto de entrada principal del fragment una vez que la vista está lista.
     * Recupera el rol y el UID del cliente, inicializa servicios, vistas, adapters
     * y listeners de UI, genera el calendario del mes actual y arranca ambos
     * listeners de Firebase si hay sesión activa.
     *
     * @param view               Vista raíz devuelta por {@link #onCreateView}
     * @param savedInstanceState Estado previo del fragment, si existe
     */
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

    /**
     * Instancia los servicios necesarios para consultar reservas y sesiones en Firebase.
     */
    private void inicializarServicios() {
        bookingService = new BookingService();
        sessionService = new SessionService();
    }

    /**
     * Enlaza todas las vistas del layout con sus variables y establece
     * el estado inicial de visibilidad: pantalla de carga activa y contenido oculto.
     *
     * @param view Vista raíz del fragment desde la que se resuelven los IDs
     */
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

    /**
     * Configura los tres RecyclerView del fragment:
     * <ul>
     *   <li>{@code recyclerReservasDia} — reservas confirmadas del día seleccionado.</li>
     *   <li>{@code recyclerPendientes} — reservas confirmadas futuras del cliente.</li>
     *   <li>{@code recyclerHistorial} — reservas pasadas o canceladas.</li>
     * </ul>
     * Todos los adapters reciben las listas de sesiones para enriquecer cada reserva
     * cruzando por {@code sesionId} en el momento del bind.
     */
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

    /**
     * Asocia los listeners de los botones de navegación del calendario y del botón
     * de volver al Home.
     * <p>
     * Al cambiar de mes también se actualiza la fecha seleccionada al primer día
     * del nuevo mes y se reconstruyen las reservas del día para reflejar el cambio.
     */
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

    /**
     * Crea una celda vacía de relleno para alinear el primer día del mes
     * con su columna correcta dentro del GridLayout.
     *
     * @return TextView vacío con las dimensiones estándar de celda
     */
    private View crearCeldaVacia() {
        TextView tv = new TextView(requireContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width      = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.height     = dpToPx(40);
        tv.setLayoutParams(params);
        return tv;
    }

    /**
     * Crea la celda visual de un día concreto del calendario.
     * Aplica los estilos correspondientes según su estado: hoy, seleccionado o pasado.
     * Si el día tiene al menos una reserva CONFIRMADA añade el indicador {@code dot_sesion}
     * bajo el número. Al pulsarlo actualiza la fecha seleccionada y reconstruye las
     * reservas del día.
     *
     * @param dia Calendar con el día a representar
     * @return Vista de la celda lista para añadir al GridLayout
     */
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


    /**
     * Calcula el timestamp de fin de sesión.
     * Puerto exacto de getFechaFin() del componente Angular.
     *
     * @param reserva Reserva de la que se toma la fecha base (timestamp epoch)
     * @param sesion  Sesión vinculada de la que se extrae {@code horaFin}; si es
     *                {@code null} se usa "23:59" como fallback
     * @return Timestamp en milisegundos del momento de fin de la sesión
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

    /**
     * Busca una sesión en la caché local por su ID.
     *
     * @param sesionId ID de la sesión a buscar
     * @return La {@link Session} encontrada, o {@code null} si no existe en la caché
     */
    private Session getSesionById(String sesionId) {
        if (sesionId == null) return null;
        for (Session s : todasLasSesiones) {
            if (sesionId.equals(s.getId())) return s;
        }
        return null;
    }

    /**
     * Devuelve la horaInicio de la sesión vinculada a {@code sesionId}.
     * Si la sesión no existe en la caché o no tiene horaInicio definida devuelve
     * cadena vacía para que el comparador de orden funcione sin NPE.
     *
     * @param sesionId ID de la sesión cuya hora de inicio se quiere obtener
     * @return Cadena con la hora de inicio en formato HH:mm, o "" si no está disponible
     */
    private String getSesionHoraInicio(String sesionId) {
        Session s = getSesionById(sesionId);
        return (s != null && s.getHoraInicio() != null) ? s.getHoraInicio() : "";
    }

    /**
     * Clave YYYY-M-D desde timestamp epoch.
     * Equivale a claveDia() del componente Angular.
     *
     * @param timestamp Fecha en milisegundos desde epoch
     * @return Cadena con formato "año-mes-día" (mes sin padding, 0-indexed igual que en Angular)
     */
    private String claveDia(long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH)
                + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Clave YYYY-M-D desde un objeto {@link Calendar}.
     * Versión complementaria de {@link #claveDia(long)} para usarla directamente
     * con las fechas del calendario sin pasar por timestamp.
     *
     * @param cal Instancia de Calendar con la fecha a convertir
     * @return Cadena con formato "año-mes-día" (mes sin padding, 0-indexed)
     */
    private String claveDiaDesdeCalendar(Calendar cal) {
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH)
                + "-" + cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Comprueba si el día proporcionado coincide con la fecha de hoy.
     *
     * @param dia Calendar con el día a evaluar
     * @return {@code true} si el día es hoy; {@code false} en caso contrario
     */
    private boolean esHoy(Calendar dia) {
        Calendar hoy = Calendar.getInstance();
        return dia.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)
                && dia.get(Calendar.YEAR) == hoy.get(Calendar.YEAR);
    }

    /**
     * Comprueba si el día proporcionado coincide con la fecha actualmente seleccionada.
     *
     * @param dia Calendar con el día a evaluar
     * @return {@code true} si el día está seleccionado; {@code false} en caso contrario
     */
    private boolean esSeleccionado(Calendar dia) {
        return dia.get(Calendar.DAY_OF_YEAR) == fechaSeleccionada.get(Calendar.DAY_OF_YEAR)
                && dia.get(Calendar.YEAR) == fechaSeleccionada.get(Calendar.YEAR);
    }

    /**
     * Comprueba si el día proporcionado es anterior a hoy (ignorando la hora).
     *
     * @param dia Calendar con el día a evaluar
     * @return {@code true} si el día ya ha pasado; {@code false} en caso contrario
     */
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

    /**
     * Actualiza el TextView de cabecera con el nombre del día actualmente seleccionado
     * formateado en español (ej: "lunes 5 de mayo").
     */
    private void actualizarCabeceraDia() {
        if (!isAdded()) return;
        tvFechaSeleccionada.setText(capitalizar(sdfDia.format(fechaSeleccionada.getTime())));
    }

    /**
     * Alterna la visibilidad del RecyclerView y el texto vacío de reservas del día
     * según si la lista {@code reservasDelDia} tiene o no elementos.
     */
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

    /**
     * Alterna la visibilidad del RecyclerView y el texto vacío de reservas pendientes
     * según si la lista {@code reservasPendientes} tiene o no elementos.
     */
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

    /**
     * Alterna la visibilidad del RecyclerView y el texto vacío del historial
     * según si la lista {@code reservasHistorial} tiene o no elementos.
     */
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

    /**
     * Convierte la primera letra de un texto a mayúscula.
     * Se usa para formatear los nombres de mes y día devueltos por {@link SimpleDateFormat}
     * en minúsculas por la configuración regional española.
     *
     * @param texto Cadena a capitalizar
     * @return Cadena con la primera letra en mayúscula, o el mismo texto si es nulo o vacío
     */
    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * Convierte una medida en dp a píxeles usando la densidad de pantalla del dispositivo.
     *
     * @param dp Valor en density-independent pixels a convertir
     * @return Valor equivalente en píxeles redondeado al entero más cercano
     */
    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Aplica alineación centrada al texto del {@link Snackbar} proporcionado.
     * Extraído como método auxiliar para reutilizarlo tanto en los Snackbars simples
     * como en los de confirmación con acción.
     *
     * @param snackbar Snackbar cuyo texto se va a centrar
     */
    private void centrarTextoSnackbar(Snackbar snackbar) {
        TextView tv = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }

    /**
     * Muestra un {@link Snackbar} con el texto centrado horizontalmente.
     * Incluye comprobación de {@code isAdded()} para evitar llamadas cuando
     * el fragment ya no está adjunto a su actividad.
     *
     * @param message Mensaje a mostrar al usuario
     */
    private void showSnackbar(String message) {
        if (!isAdded()) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }
}