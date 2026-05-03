package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Booking;
import com.example.sportiva_booking_android.v2.models.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter para el RecyclerView de reservas futuras CONFIRMADAS (pendientes).
 *
 * Equivale a la sección reservasPendientes del componente Angular.
 * No tiene botón de cancelar — esa acción solo está disponible en la
 * vista del día donde el cliente puede ver el contexto completo de la sesión.
 * El cruce sesionId → Session se hace en bind() igual que en ReservasDiaAdapter.
 */
public class ReservasPendientesAdapter extends RecyclerView.Adapter<ReservasPendientesAdapter.PendienteViewHolder> {

    private final List<Booking> reservas;
    private final List<Session> sesiones;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("EEE dd MMM yyyy", new Locale("es", "ES"));

    public ReservasPendientesAdapter(List<Booking> reservas, List<Session> sesiones) {
        this.reservas = reservas;
        this.sesiones = sesiones;
    }

    @NonNull
    @Override
    public PendienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reserva_pendiente, parent, false);
        return new PendienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendienteViewHolder holder, int position) {
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

    public class PendienteViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvFecha;
        private final TextView tvHorario;
        private final TextView tvTitulo;
        private final TextView tvTipo;
        private final TextView tvModalidad;
        private final TextView tvAforo;

        public PendienteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha     = itemView.findViewById(R.id.tvPendienteFecha);
            tvHorario   = itemView.findViewById(R.id.tvPendienteHorario);
            tvTitulo    = itemView.findViewById(R.id.tvPendienteTitulo);
            tvTipo      = itemView.findViewById(R.id.tvPendienteTipo);
            tvModalidad = itemView.findViewById(R.id.tvPendienteModalidad);
            tvAforo     = itemView.findViewById(R.id.tvPendienteAforo);
        }

        public void bind(Booking reserva, Session sesion) {
            tvFecha.setText(sdf.format(new Date(reserva.getFecha())));

            if (sesion != null) {
                tvHorario.setText(String.format("%s – %s",
                        sesion.getHoraInicio(), sesion.getHoraFin()));
                tvTitulo.setText(sesion.getTitulo());
                tvTipo.setText(sesion.getTipo() != null ? sesion.getTipo().name() : "");
                tvModalidad.setText(sesion.getModalidad() != null ? sesion.getModalidad().name() : "");
                tvAforo.setText(String.format(Locale.getDefault(),
                        "%d/%d", sesion.getAforoActual(), sesion.getAforoMax()));
            } else {
                tvHorario.setText("—");
                tvTitulo.setText("Sesión no disponible");
                tvTipo.setVisibility(View.GONE);
                tvModalidad.setVisibility(View.GONE);
                tvAforo.setVisibility(View.GONE);
            }
        }
    }
}