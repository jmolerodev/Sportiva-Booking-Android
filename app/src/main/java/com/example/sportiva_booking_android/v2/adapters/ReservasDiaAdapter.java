package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Booking;
import com.example.sportiva_booking_android.v2.models.Session;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter para el RecyclerView de reservas CONFIRMADAS del día seleccionado.
 *
 * Recibe dos listas paralelas (reservas + sesiones) y cruza por sesionId
 * en cada bind(), igual que el método enriquecer() del componente Angular.
 * El flag yaFinalizada se calcula aquí mismo comparando la hora de fin de
 * la sesión con el momento actual, para ocultar el botón cancelar cuando
 * la sesión ya ha concluido aunque la reserva siga CONFIRMADA.
 */
public class ReservasDiaAdapter extends RecyclerView.Adapter<ReservasDiaAdapter.ReservaDiaViewHolder> {

    /**
     * Notifica al fragment que el cliente quiere cancelar una reserva del día.
     */
    public interface OnCancelarReservaListener {
        void onCancelarReservaClick(Booking reserva, Session sesion);
    }

    private final List<Booking> reservas;
    private final List<Session> sesiones;
    private final OnCancelarReservaListener listener;

    private final SimpleDateFormat sdfHora =
            new SimpleDateFormat("HH:mm", new Locale("es", "ES"));

    public ReservasDiaAdapter(List<Booking> reservas,
                              List<Session> sesiones,
                              OnCancelarReservaListener listener) {
        this.reservas = reservas;
        this.sesiones = sesiones;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReservaDiaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reserva_dia, parent, false);
        return new ReservaDiaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReservaDiaViewHolder holder, int position) {
        Booking reserva = reservas.get(position);

        /* Cruce por sesionId — equivale a enriquecer() en Angular */
        Session sesion = null;
        for (Session s : sesiones) {
            if (s.getId() != null && s.getId().equals(reserva.getSesionId())) {
                sesion = s;
                break;
            }
        }

        holder.bind(reserva, sesion);
    }

    @Override
    public int getItemCount() {
        return reservas.size();
    }

    public class ReservaDiaViewHolder extends RecyclerView.ViewHolder {

        private final TextView    tvHorario;
        private final TextView    tvTitulo;
        private final TextView    tvTipo;
        private final TextView    tvModalidad;
        private final TextView    tvAforo;
        private final ImageButton btnCancelar;

        public ReservaDiaViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHorario   = itemView.findViewById(R.id.tvReservaDiaHorario);
            tvTitulo    = itemView.findViewById(R.id.tvReservaDiaTitulo);
            tvTipo      = itemView.findViewById(R.id.tvReservaDiaTipo);
            tvModalidad = itemView.findViewById(R.id.tvReservaDiaModalidad);
            tvAforo     = itemView.findViewById(R.id.tvReservaDiaAforo);
            btnCancelar = itemView.findViewById(R.id.btnReservaDiaCancelar);
        }

        public void bind(Booking reserva, Session sesion) {

            if (sesion != null) {
                tvHorario.setText(String.format("%s – %s",
                        sesion.getHoraInicio(), sesion.getHoraFin()));
                tvTitulo.setText(sesion.getTitulo());
                tvTipo.setText(sesion.getTipo() != null ? sesion.getTipo().name() : "");
                tvModalidad.setText(sesion.getModalidad() != null ? sesion.getModalidad().name() : "");
                tvAforo.setText(String.format(Locale.getDefault(),
                        "%d/%d", sesion.getAforoActual(), sesion.getAforoMax()));

                /*
                 * yaFinalizada — equivale al flag del componente Angular.
                 * Reconstruimos la fecha de fin real de la sesión usando la
                 * fecha epoch de la reserva + la horaFin de la sesión viva,
                 * igual que getFechaFin() en el componente Angular.
                 */
                boolean yaFinalizada = getFechaFin(reserva, sesion) <= System.currentTimeMillis();

                if (yaFinalizada) {
                    btnCancelar.setVisibility(View.GONE);
                } else {
                    btnCancelar.setVisibility(View.VISIBLE);
                    btnCancelar.setOnClickListener(v -> listener.onCancelarReservaClick(reserva, sesion));
                }

            } else {
                /* Sesión eliminada de Firebase — mostramos lo que podemos */
                tvHorario.setText("—");
                tvTitulo.setText("Sesión no disponible");
                tvTipo.setVisibility(View.GONE);
                tvModalidad.setVisibility(View.GONE);
                tvAforo.setVisibility(View.GONE);
                btnCancelar.setVisibility(View.GONE);
            }
        }

        /**
         * Calcula el timestamp real de fin de sesión.
         * Equivale exactamente a getFechaFin() del componente Angular:
         * toma la fecha epoch de la reserva y le aplica la horaFin de la sesión.
         * Si la sesión no existe usa 23:59 como fallback conservador.
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
    }
}