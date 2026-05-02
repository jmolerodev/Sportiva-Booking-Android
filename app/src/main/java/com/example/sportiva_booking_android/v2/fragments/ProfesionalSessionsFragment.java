package com.example.sportiva_booking_android.v2.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.adapters.SessionHistorialAdapter;
import com.example.sportiva_booking_android.v2.adapters.SlotAdapter;
import com.example.sportiva_booking_android.v2.enums.EstadoSesion;
import com.example.sportiva_booking_android.v2.enums.EstadoSlot;
import com.example.sportiva_booking_android.v2.enums.ModalidadSesion;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.enums.TipoSesion;
import com.example.sportiva_booking_android.v2.models.Session;
import com.example.sportiva_booking_android.v2.models.SlotHorario;
import com.example.sportiva_booking_android.v2.models.SportCentre;
import com.example.sportiva_booking_android.v2.services.ProfesionalService;
import com.example.sportiva_booking_android.v2.services.SessionService;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ProfesionalSessionsFragment extends Fragment
        implements SlotAdapter.OnSlotClickListener,
        SlotAdapter.OnCancelarSesionListener,
        SessionHistorialAdapter.OnEliminarSesionListener {

    private static final String ARG_ROL = "ROL";

    /*Vistas*/
    private LinearLayout  layoutCargando;
    private LinearLayout  layoutContenido;
    private TextView      tvSubtituloCentro;
    private ImageButton   btnMesAnterior;
    private ImageButton   btnMesSiguiente;
    private TextView      tvMesActual;
    private GridLayout    gridCalendario;
    private TextView      tvFechaSeleccionada;
    private TextView      tvSlotsVacio;
    private RecyclerView  recyclerSlots;
    private TextView      tvHistorialVacio;
    private RecyclerView  recyclerHistorial;
    private Button        btnVolverHome;

    /*Servicios*/
    private SessionService     sessionService;
    private SportCentreService sportCentreService;
    private ProfesionalService profesionalService;

    /*Atributos de la Clase*/
    private Rol    rolUsuario;
    private String profesionalUid;
    private String especialidad;
    private SportCentre centro;

    private Calendar fechaSeleccionada = Calendar.getInstance();
    private Calendar mesActual         = Calendar.getInstance();

    private final java.util.Set<String> diasConSesion = new java.util.HashSet<>();

    private final List<SlotHorario> slots     = new ArrayList<>();
    private final List<Session>     historial = new ArrayList<>();

    private SlotAdapter             slotAdapter;
    private SessionHistorialAdapter historialAdapter;

    private final SimpleDateFormat sdfDia =
            new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));

    private final SimpleDateFormat sdfMes =
            new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"));

    private boolean loadingCentro       = true;
    private boolean loadingEspecialidad = true;
    private boolean loadingHistorial    = true;

    /**
     * Método de factoría. Pasamos el rol por Bundle igual que el resto de fragments.
     *
     * @param rol Rol del usuario autenticado
     * @return Instancia configurada del fragment
     */
    public static ProfesionalSessionsFragment newInstance(Rol rol) {
        ProfesionalSessionsFragment fragment = new ProfesionalSessionsFragment();
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
        return inflater.inflate(R.layout.fragment_profesional_sessions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recuperarRol();
        inicializarServicios();
        inicializarVistas(view);
        configurarRecyclers();
        configurarListeners();
        cargarDatosIniciales();
    }

    /**
     * Recupera el rol del Bundle de argumentos.
     * Si no se encuentra o el valor no es válido usa PROFESIONAL como fallback.
     */
    private void recuperarRol() {
        if (getArguments() != null) {
            try {
                rolUsuario = Rol.valueOf(
                        getArguments().getString(ARG_ROL, Rol.PROFESIONAL.name())
                );
            } catch (IllegalArgumentException e) {
                rolUsuario = Rol.PROFESIONAL;
            }
        } else {
            rolUsuario = Rol.PROFESIONAL;
        }
    }

    /**
     * Instancia los servicios necesarios para leer datos de Firebase.
     */
    private void inicializarServicios() {
        sessionService     = new SessionService();
        sportCentreService = new SportCentreService();
        profesionalService = new ProfesionalService(requireContext());
    }

    /**
     * Enlaza todas las vistas del layout y arranca con el spinner visible
     * hasta que los tres flujos asíncronos hayan completado su carga.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
     */
    private void inicializarVistas(View view) {
        layoutCargando      = view.findViewById(R.id.layoutCargandoSessions);
        layoutContenido     = view.findViewById(R.id.layoutContenidoSessions);
        tvSubtituloCentro   = view.findViewById(R.id.tvSubtituloCentro);
        btnMesAnterior      = view.findViewById(R.id.btnMesAnterior);
        btnMesSiguiente     = view.findViewById(R.id.btnMesSiguiente);
        tvMesActual         = view.findViewById(R.id.tvMesActual);
        gridCalendario      = view.findViewById(R.id.gridCalendario);
        tvFechaSeleccionada = view.findViewById(R.id.tvFechaSeleccionada);
        tvSlotsVacio        = view.findViewById(R.id.tvSlotsVacio);
        recyclerSlots       = view.findViewById(R.id.recyclerSlots);
        tvHistorialVacio    = view.findViewById(R.id.tvHistorialVacio);
        recyclerHistorial   = view.findViewById(R.id.recyclerHistorial);
        btnVolverHome       = view.findViewById(R.id.btnVolverHome);

        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    /**
     * Configura los RecyclerViews con sus adaptadores y LayoutManagers.
     */
    private void configurarRecyclers() {
        slotAdapter = new SlotAdapter(slots, this, this);
        recyclerSlots.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerSlots.setAdapter(slotAdapter);

        historialAdapter = new SessionHistorialAdapter(historial, this);
        recyclerHistorial.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerHistorial.setAdapter(historialAdapter);
    }

    /**
     * Asigna los listeners de navegación del calendario y el botón de volver.
     */
    private void configurarListeners() {
        btnMesAnterior.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, -1);
            fechaSeleccionada = (Calendar) mesActual.clone();
            generarCalendario();
            cargarSesionesDelDia();
        });

        btnMesSiguiente.setOnClickListener(v -> {
            mesActual.add(Calendar.MONTH, 1);
            fechaSeleccionada = (Calendar) mesActual.clone();
            generarCalendario();
            cargarSesionesDelDia();
        });

        btnVolverHome.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });
    }

    /**
     * Orquesta los tres flujos asíncronos paralelos de carga inicial:
     * especialidad del profesional, centro deportivo vinculado e historial de sesiones.
     * El spinner se oculta solo cuando los tres han completado.
     */
    private void cargarDatosIniciales() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        profesionalUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        cargarEspecialidad();
        cargarCentro();
        cargarHistorial();
    }

    /**
     * Carga la especialidad del profesional desde /Persons/:uid para preseleccionar
     * el tipo de sesión en el diálogo de creación y aplicar las restricciones de modalidad.
     */
    private void cargarEspecialidad() {
        profesionalService.getProfesionalById(profesionalUid, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                if (snapshot.exists()) {
                    com.example.sportiva_booking_android.v2.models.Profesional p =
                            snapshot.getValue(com.example.sportiva_booking_android.v2.models.Profesional.class);
                    if (p != null && p.getEspecialidad() != null) {
                        especialidad = p.getEspecialidad().name();
                    }
                }
                loadingEspecialidad = false;
                checkLoading();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                loadingEspecialidad = false;
                checkLoading();
            }
        });
    }

    /**
     * Carga el centro deportivo vinculado al profesional desde /Sports-Centre
     * para obtener el horario de apertura con el que generar los slots del día.
     */
    private void cargarCentro() {
        sportCentreService.getSportCentreByProfessionalUid(profesionalUid, sportCentre -> {
            if (!isAdded()) return;
            centro = sportCentre;
            if (centro != null) {
                tvSubtituloCentro.setText(
                        String.format("%s — %s", centro.getNombre(), centro.getDireccion()));
                generarCalendario();
                cargarSesionesDelDia();
            }
            loadingCentro = false;
            checkLoading();
        });
    }

    /**
     * Carga todas las sesiones del profesional para construir el historial
     * y marcar los días con sesión activa en el calendario.
     */
    private void cargarHistorial() {
        sessionService.getSessionsByProfesional(profesionalUid, new SessionService.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sesiones) {
                if (!isAdded()) return;
                long ahora = System.currentTimeMillis();

                diasConSesion.clear();
                historial.clear();

                for (Session s : sesiones) {
                    if (s.getEstado() == EstadoSesion.ACTIVA) {
                        diasConSesion.add(claveDia(s.getFecha()));
                    }
                    if (s.getEstado() == EstadoSesion.CANCELADA || esSesionFinalizada(s, ahora)) {
                        historial.add(s);
                    }
                }

                historial.sort((a, b) -> Long.compare(b.getFecha(), a.getFecha()));

                historialAdapter.notifyDataSetChanged();
                actualizarEstadoVacioHistorial();

                loadingHistorial = false;
                checkLoading();
                generarCalendario();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                loadingHistorial = false;
                checkLoading();
            }
        });
    }

    /**
     * Carga las sesiones activas del centro para el día seleccionado y
     * construye la lista de slots horarios con su estado correspondiente.
     */
    private void cargarSesionesDelDia() {
        if (centro == null) return;

        Calendar inicioDia = (Calendar) fechaSeleccionada.clone();
        inicioDia.set(Calendar.HOUR_OF_DAY, 0);
        inicioDia.set(Calendar.MINUTE, 0);
        inicioDia.set(Calendar.SECOND, 0);
        inicioDia.set(Calendar.MILLISECOND, 0);

        Calendar finDia = (Calendar) fechaSeleccionada.clone();
        finDia.set(Calendar.HOUR_OF_DAY, 23);
        finDia.set(Calendar.MINUTE, 59);
        finDia.set(Calendar.SECOND, 59);
        finDia.set(Calendar.MILLISECOND, 999);

        actualizarCabeceraDia();

        sessionService.getSessionsByCentroAndFecha(
                centro.getAdminUid(),
                inicioDia.getTimeInMillis(),
                finDia.getTimeInMillis(),
                new SessionService.SessionsCallback() {
                    @Override
                    public void onSuccess(List<Session> sesiones) {
                        if (!isAdded()) return;
                        slots.clear();
                        slots.addAll(generarSlots(sesiones));
                        slotAdapter.notifyDataSetChanged();
                        actualizarEstadoVacioSlots();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        slots.clear();
                        slotAdapter.notifyDataSetChanged();
                        actualizarEstadoVacioSlots();
                    }
                });
    }

    /**
     * Genera los slots horarios hora a hora a partir del horario del centro
     * para el día seleccionado, asignando el estado de cada franja.
     * Devuelve lista vacía si el día es pasado o el centro está cerrado.
     *
     * @param sesionesDelDia Sesiones ACTIVAS del centro en el día seleccionado
     * @return Lista de slots con hora, estado y sesión asociada si la hay
     */
    private List<SlotHorario> generarSlots(List<Session> sesionesDelDia) {
        List<SlotHorario> resultado = new ArrayList<>();
        if (centro == null || centro.getHorario() == null) return resultado;
        if (esPasado(fechaSeleccionada))                   return resultado;

        String nombreDia = getNombreDia(fechaSeleccionada);
        SportCentre.HorarioDia horarioDia = centro.getHorario().get(nombreDia);
        if (horarioDia == null || !horarioDia.isAbierto()) return resultado;

        int hApertura = Integer.parseInt(horarioDia.getApertura().split(":")[0]);
        int hCierre   = Integer.parseInt(horarioDia.getCierre().split(":")[0]);

        Calendar ahora         = Calendar.getInstance();
        boolean  esHoySelected = esHoy(fechaSeleccionada);

        if (esHoySelected && ahora.get(Calendar.HOUR_OF_DAY) >= hCierre) return resultado;

        for (int h = hApertura; h < hCierre; h++) {
            if (esHoySelected && h <= ahora.get(Calendar.HOUR_OF_DAY)) continue;

            String horaInicio = String.format(Locale.getDefault(), "%02d:00", h);
            String horaFin    = String.format(Locale.getDefault(), "%02d:00", h + 1);

            Session sesionEnSlot = null;
            for (Session s : sesionesDelDia) {
                if (horaInicio.equals(s.getHoraInicio()) && s.getEstado() == EstadoSesion.ACTIVA) {
                    sesionEnSlot = s;
                    break;
                }
            }

            EstadoSlot estado;
            if (sesionEnSlot == null) {
                estado = EstadoSlot.LIBRE;
            } else if (profesionalUid.equals(sesionEnSlot.getProfesionalId())) {
                estado = EstadoSlot.PROPIO;
            } else {
                estado = EstadoSlot.OCUPADO;
            }

            resultado.add(new SlotHorario(horaInicio, horaFin, estado, sesionEnSlot));
        }

        return resultado;
    }

    @Override
    public void onSlotLibreClick(SlotHorario slot) {
        mostrarDialogoCrearSesion(slot);
    }

    @Override
    public void onCancelarSesionClick(SlotHorario slot) {
        if (slot.getSesion() == null || slot.getSesion().getId() == null) return;
        confirmarCancelacion(slot.getSesion());
    }

    @Override
    public void onEliminarSesionClick(Session sesion) {
        if (sesion.getId() == null) return;
        confirmarEliminacion(sesion);
    }

    /**
     * Infla y muestra el diálogo de creación de sesión con los campos del formulario.
     * Valida los campos antes de persistir y aplica las restricciones de especialidad.
     *
     * @param slot Slot horario libre sobre el que se va a crear la sesión
     */
    private void mostrarDialogoCrearSesion(SlotHorario slot) {
        if (!isAdded()) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_crear_sesion, null);

        EditText etTitulo      = dialogView.findViewById(R.id.etDialogTitulo);
        EditText etDescripcion = dialogView.findViewById(R.id.etDialogDescripcion);
        Spinner  spinnerTipo   = dialogView.findViewById(R.id.spinnerDialogTipo);
        Spinner  spModalidad   = dialogView.findViewById(R.id.spinnerDialogModalidad);
        EditText etAforoMax    = dialogView.findViewById(R.id.etDialogAforoMax);
        TextView tvSlotInfo    = dialogView.findViewById(R.id.tvDialogSlotInfo);

        tvSlotInfo.setText(String.format("%s – %s · %s",
                slot.getHoraInicio(), slot.getHoraFin(),
                sdfDia.format(fechaSeleccionada.getTime())));

        List<String> tipos = new ArrayList<>();
        if ("ENTRENADOR".equals(especialidad)) {
            tipos.add(TipoSesion.ENTRENAMIENTO.name());
        } else if ("FISIOTERAPEUTA".equals(especialidad)) {
            tipos.add(TipoSesion.FISIOTERAPIA.name());
        } else {
            tipos.add(TipoSesion.ENTRENAMIENTO.name());
            tipos.add(TipoSesion.FISIOTERAPIA.name());
        }
        spinnerTipo.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, tipos));
        spinnerTipo.setEnabled(especialidad != null);

        List<String> modalidades = new ArrayList<>();
        modalidades.add(ModalidadSesion.INDIVIDUAL.name());
        if (!"FISIOTERAPEUTA".equals(especialidad)) {
            modalidades.add(ModalidadSesion.GRUPAL.name());
        }
        spModalidad.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, modalidades));
        spModalidad.setEnabled(!"FISIOTERAPEUTA".equals(especialidad));

        new AlertDialog.Builder(requireContext())
                .setTitle("Nueva Sesión")
                .setView(dialogView)
                .setPositiveButton("Guardar", (dialog, which) ->
                        guardarSesion(slot, etTitulo, etDescripcion,
                                spinnerTipo, spModalidad, etAforoMax))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Valida el formulario y persiste la nueva sesión en Firebase.
     * Si la modalidad es INDIVIDUAL fija el aforoMax a 1.
     * Recarga los slots del día y el historial tras la escritura exitosa.
     */
    private void guardarSesion(SlotHorario slot,
                               EditText etTitulo,
                               EditText etDescripcion,
                               Spinner  spinnerTipo,
                               Spinner  spModalidad,
                               EditText etAforoMax) {

        String titulo       = etTitulo.getText().toString().trim();
        String descripcion  = etDescripcion.getText().toString().trim();
        String tipoStr      = spinnerTipo.getSelectedItem().toString();
        String modalidadStr = spModalidad.getSelectedItem().toString();

        if (titulo.isEmpty() || descripcion.isEmpty()) {
            showSnackbar("Por favor, completa todos los campos obligatorios");
            return;
        }

        ModalidadSesion modalidad = ModalidadSesion.valueOf(modalidadStr);
        int aforoMax = modalidad == ModalidadSesion.INDIVIDUAL
                ? 1
                : parseAforoMax(etAforoMax.getText().toString().trim());

        Calendar fechaDia = (Calendar) fechaSeleccionada.clone();
        fechaDia.set(Calendar.HOUR_OF_DAY, 0);
        fechaDia.set(Calendar.MINUTE, 0);
        fechaDia.set(Calendar.SECOND, 0);
        fechaDia.set(Calendar.MILLISECOND, 0);

        Session nuevaSesion = new Session();
        nuevaSesion.setCentroId(centro.getAdminUid());
        nuevaSesion.setProfesionalId(profesionalUid);
        nuevaSesion.setTipo(TipoSesion.valueOf(tipoStr));
        nuevaSesion.setFecha(fechaDia.getTimeInMillis());
        nuevaSesion.setHoraInicio(slot.getHoraInicio());
        nuevaSesion.setHoraFin(slot.getHoraFin());
        nuevaSesion.setModalidad(modalidad);
        nuevaSesion.setAforoMax(aforoMax);
        nuevaSesion.setAforoActual(0);
        nuevaSesion.setTitulo(titulo);
        nuevaSesion.setDescripcion(descripcion);
        nuevaSesion.setEstado(EstadoSesion.ACTIVA);

        sessionService.createSession(nuevaSesion, new SessionService.VoidCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                showSnackbar("Sesión creada correctamente");
                cargarSesionesDelDia();
                cargarHistorial();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                showSnackbar("Error al crear la sesión");
            }
        });
    }

    /**
     * Muestra un Snackbar de confirmación antes de cancelar la sesión.
     * Actualiza únicamente el campo estado a CANCELADA sin borrar el nodo
     * para que la sesión caiga al historial.
     * TODO: cancelar en cascada las reservas asociadas con BookingService
     *
     * @param sesion Sesión activa a cancelar
     */
    private void confirmarCancelacion(Session sesion) {
        if (getView() == null) return;
        Snackbar snackbar = Snackbar.make(getView(),
                "¿Cancelar esta sesión? Pasará al historial.",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("CANCELAR", v ->
                sessionService.cancelSession(sesion.getId(), new SessionService.VoidCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        showSnackbar("Sesión cancelada correctamente");
                        cargarSesionesDelDia();
                        cargarHistorial();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        showSnackbar("Error al cancelar la sesión");
                    }
                }));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    /**
     * Muestra un Snackbar de confirmación antes de eliminar físicamente
     * la sesión del historial junto con todas sus reservas asociadas.
     * TODO: eliminar en cascada las reservas asociadas con BookingService
     *
     * @param sesion Sesión del historial a eliminar permanentemente
     */
    private void confirmarEliminacion(Session sesion) {
        if (getView() == null) return;
        Snackbar snackbar = Snackbar.make(getView(),
                "¿Eliminar este registro permanentemente?",
                Snackbar.LENGTH_LONG);
        snackbar.setAction("ELIMINAR", v ->
                sessionService.deleteSession(sesion.getId(), new SessionService.VoidCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        showSnackbar("Registro eliminado correctamente");
                        cargarHistorial();
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) return;
                        showSnackbar("Error al eliminar el registro");
                    }
                }));
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }

    /**
     * Genera la cuadrícula del calendario para el mes actualmente visualizado.
     * Rellena con celdas vacías los huecos anteriores al primer día de la semana.
     * Marca visualmente los días de hoy, seleccionado, pasado y con sesión.
     */
    private void generarCalendario() {
        if (!isAdded()) return;

        tvMesActual.setText(capitalize(sdfMes.format(mesActual.getTime())));
        gridCalendario.removeAllViews();

        Calendar primer = (Calendar) mesActual.clone();
        primer.set(Calendar.DAY_OF_MONTH, 1);

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
     * Crea una celda vacía de relleno para los huecos del calendario.
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
     * Crea la celda visual de un día del calendario aplicando el estilo
     * correspondiente: hoy, seleccionado, pasado o con sesión propia activa.
     *
     * @param dia Calendar con el día a representar
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

        if (diasConSesion.contains(claveDia(dia.getTimeInMillis()))) {
            tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.dot_sesion);
            tv.setCompoundDrawablePadding(0);
        }

        if (!esPasado(dia)) {
            tv.setOnClickListener(v -> {
                fechaSeleccionada = (Calendar) dia.clone();
                generarCalendario();
                cargarSesionesDelDia();
            });
        }

        return tv;
    }

    /**
     * Comprueba si un Calendar corresponde al día de hoy.
     *
     * @param dia Calendar a comprobar
     */
    private boolean esHoy(Calendar dia) {
        Calendar hoy = Calendar.getInstance();
        return dia.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)
                && dia.get(Calendar.YEAR) == hoy.get(Calendar.YEAR);
    }

    /**
     * Comprueba si un Calendar corresponde al día actualmente seleccionado.
     *
     * @param dia Calendar a comprobar
     */
    private boolean esSeleccionado(Calendar dia) {
        return dia.get(Calendar.DAY_OF_YEAR) == fechaSeleccionada.get(Calendar.DAY_OF_YEAR)
                && dia.get(Calendar.YEAR) == fechaSeleccionada.get(Calendar.YEAR);
    }

    /**
     * Comprueba si un Calendar es anterior al día de hoy.
     *
     * @param dia Calendar a comprobar
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
     * Comprueba si una sesión ACTIVA ha finalizado: su hora de fin ya ha pasado.
     *
     * @param sesion Sesión a evaluar
     * @param ahora  Timestamp epoch del momento actual en ms
     */
    private boolean esSesionFinalizada(Session sesion, long ahora) {
        if (sesion.getEstado() != EstadoSesion.ACTIVA || sesion.getHoraFin() == null) return false;
        String[] partes = sesion.getHoraFin().split(":");
        Calendar fechaFin = Calendar.getInstance();
        fechaFin.setTimeInMillis(sesion.getFecha());
        fechaFin.set(Calendar.HOUR_OF_DAY, Integer.parseInt(partes[0]));
        fechaFin.set(Calendar.MINUTE, Integer.parseInt(partes[1]));
        fechaFin.set(Calendar.SECOND, 0);
        return fechaFin.getTimeInMillis() < ahora;
    }

    /**
     * Devuelve el nombre del día de la semana en español para indexar el horario del centro.
     *
     * @param dia Calendar del que extraer el nombre del día
     */
    private String getNombreDia(Calendar dia) {
        String[] dias = {"Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
        return dias[dia.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /**
     * Genera la clave de identificación única de un día en formato YYYY-M-D
     * a partir de un timestamp epoch en ms.
     *
     * @param timestamp Milisegundos epoch del día
     */
    private String claveDia(long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Actualiza la cabecera de la sección de slots con la fecha seleccionada.
     */
    private void actualizarCabeceraDia() {
        if (!isAdded()) return;
        tvFechaSeleccionada.setText(capitalize(sdfDia.format(fechaSeleccionada.getTime())));
    }

    /**
     * Muestra u oculta el estado vacío de la sección de slots según corresponda.
     */
    private void actualizarEstadoVacioSlots() {
        if (!isAdded()) return;
        if (slots.isEmpty()) {
            recyclerSlots.setVisibility(View.GONE);
            tvSlotsVacio.setVisibility(View.VISIBLE);
            if (esPasado(fechaSeleccionada)) {
                tvSlotsVacio.setText("No se pueden gestionar sesiones de días pasados");
            } else if (esHoy(fechaSeleccionada)) {
                tvSlotsVacio.setText("El horario del centro ya ha finalizado para hoy");
            } else {
                tvSlotsVacio.setText("No hay horarios disponibles o el centro está cerrado");
            }
        } else {
            recyclerSlots.setVisibility(View.VISIBLE);
            tvSlotsVacio.setVisibility(View.GONE);
        }
    }

    /**
     * Muestra u oculta el estado vacío del historial según corresponda.
     */
    private void actualizarEstadoVacioHistorial() {
        if (!isAdded()) return;
        if (historial.isEmpty()) {
            recyclerHistorial.setVisibility(View.GONE);
            tvHistorialVacio.setVisibility(View.VISIBLE);
        } else {
            recyclerHistorial.setVisibility(View.VISIBLE);
            tvHistorialVacio.setVisibility(View.GONE);
        }
    }

    /**
     * Oculta el spinner global y revela el contenido solo cuando los tres
     * flujos asíncronos de carga inicial han completado.
     */
    private void checkLoading() {
        if (!isAdded()) return;
        if (!loadingCentro && !loadingEspecialidad && !loadingHistorial) {
            layoutCargando.setVisibility(View.GONE);
            layoutContenido.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Parsea el aforo máximo introducido en el formulario con fallback a 2.
     *
     * @param valor Cadena del campo EditText de aforoMax
     */
    private int parseAforoMax(String valor) {
        try { return Integer.parseInt(valor); } catch (Exception e) { return 2; }
    }

    /**
     * Capitaliza la primera letra de una cadena — usado para formatear el mes del calendario.
     *
     * @param texto Cadena a capitalizar
     */
    private String capitalize(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * Convierte dp a píxeles usando la densidad de pantalla del contexto actual.
     *
     * @param dp Valor en dp a convertir
     */
    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Centra horizontalmente el texto del Snackbar para mantener coherencia visual
     * con el resto de Snackbars de la aplicación.
     *
     * @param snackbar Snackbar al que aplicar el centrado
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
     * Muestra un Snackbar centrado con el mensaje recibido.
     * Comprueba isAdded() antes de actuar para evitar crashes si el fragment ya no está adjunto.
     *
     * @param message Texto a mostrar en el Snackbar
     */
    private void showSnackbar(String message) {
        if (!isAdded()) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        centrarTextoSnackbar(snackbar);
        snackbar.show();
    }
}