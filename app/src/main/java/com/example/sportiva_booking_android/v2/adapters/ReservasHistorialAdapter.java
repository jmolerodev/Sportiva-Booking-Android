package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.EstadoReserva;
import com.example.sportiva_booking_android.v2.models.Booking;
import com.example.sportiva_booking_android.v2.models.Session;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter para el RecyclerView del historial de reservas.
 *
 * Muestra tanto reservas CANCELADA como las que el fragment ha marcado
 * como FINALIZADA (confirmadas cuya sesión ya ha concluido), igual que
 * reservasHistorial en el componente Angular.
 *
 * El badge de estado (CANCELADA / FINALIZADA) se determina aquí mismo
 * cruzando el estado de la reserva con la hora de fin de la sesión,
 * igual que reconstruirPendientesEHistorial() en Angular.
 *
 * El botón eliminar dispara onEliminarReservaClick para que el fragment
 * muestre el Snackbar de confirmación antes de llamar a BookingService.
 */
public class ReservasHistorialAdapter extends RecyclerView.Adapter<ReservasHistorialAdapter.HistorialViewHolder> {

    /**
     * Notifica al fragment que el cliente quiere eliminar un registro del historial.
     */
    public interface OnEliminarReservaListener {
        void onEliminarReservaClick(Booking reserva);
    }

    private final List<Booking> reservas;
    private final List<Session> sesiones;
    private final OnEliminarReservaListener listener;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("EEE dd MMM yyyy", new Locale("es", "ES"));

    public ReservasHistorialAdapter(List<Booking> reservas,
                                    List<Session> sesiones,
                                    OnEliminarReservaListener listener) {
        this.reservas = reservas;
        this.sesiones = sesiones;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistorialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reserva_historial, parent, false);
        return new HistorialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistorialViewHolder holder, int position) {
        Booking reserva = reservas.get(position);

        /* Cruce por sesionId */
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

    public class HistorialViewHolder extends RecyclerView.ViewHolder {

        private final TextView    tvFecha;
        private final TextView    tvHorario;
        private final TextView    tvTitulo;
        private final TextView    tvTipo;
        private final TextView    tvModalidad;
        private final TextView    tvEstadoBadge;
        private final ImageButton btnEliminar;

        public HistorialViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha       = itemView.findViewById(R.id.tvHistorialReservaFecha);
            tvHorario     = itemView.findViewById(R.id.tvHistorialReservaHorario);
            tvTitulo      = itemView.findViewById(R.id.tvHistorialReservaTitulo);
            tvTipo        = itemView.findViewById(R.id.tvHistorialReservaTipo);
            tvModalidad   = itemView.findViewById(R.id.tvHistorialReservaModalidad);
            tvEstadoBadge = itemView.findViewById(R.id.tvHistorialReservaEstadoBadge);
            btnEliminar   = itemView.findViewById(R.id.btnHistorialReservaEliminar);
        }

        public void bind(Booking reserva, Session sesion) {
            tvFecha.setText(sdf.format(new Date(reserva.getFecha())));

            if (sesion != null) {
                tvHorario.setText(String.format("%s – %s",
                        sesion.getHoraInicio(), sesion.getHoraFin()));
                tvTitulo.setText(sesion.getTitulo());
                tvTipo.setText(sesion.getTipo() != null ? sesion.getTipo().name() : "");
                tvTipo.setVisibility(View.VISIBLE);
                tvModalidad.setText(sesion.getModalidad() != null ? sesion.getModalidad().name() : "");
                tvModalidad.setVisibility(View.VISIBLE);
            } else {
                tvHorario.setText("—");
                tvTitulo.setText("Sesión no disponible");
                tvTipo.setVisibility(View.GONE);
                tvModalidad.setVisibility(View.GONE);
            }

            /*
             * Badge de estado: CANCELADA si el cliente la canceló explícitamente,
             * FINALIZADA si la sesión ya concluyó sin cancelación.
             * Equivale a la lógica de reconstruirPendientesEHistorial() en Angular.
             */
            if (reserva.getEstado() == EstadoReserva.CANCELADA) {
                tvEstadoBadge.setText("CANCELADA");
            } else {
                tvEstadoBadge.setText("FINALIZADA");
            }

            btnEliminar.setOnClickListener(v -> listener.onEliminarReservaClick(reserva));
        }
    }
}