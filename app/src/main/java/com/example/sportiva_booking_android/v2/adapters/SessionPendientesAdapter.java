package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionPendientesAdapter extends RecyclerView.Adapter<SessionPendientesAdapter.PendienteViewHolder> {

    private final List<Session> sesiones;

    /* Formato de fecha legible para la columna Fecha de las pendientes */
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("EEE dd MMM yyyy", new Locale("es", "ES"));


    public SessionPendientesAdapter(List<Session> sesiones) {
        this.sesiones = sesiones;
    }


    @NonNull
    @Override
    public PendienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sesion_pendiente, parent, false);
        return new PendienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendienteViewHolder holder, int position) {
        holder.bind(sesiones.get(position));
    }

    @Override
    public int getItemCount() {
        return sesiones.size();
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

        /**
         * Pinta la fila de sesión pendiente con los datos de la sesión futura.
         * No expone acciones de cancelación — esa lógica recae en el slot del día
         * dentro del SlotAdapter, igual que en el componente Angular donde las
         * pendientes son de solo lectura.
         *
         * @param sesion Sesión futura ACTIVA a representar en esta fila
         */
        public void bind(Session sesion) {
            tvFecha.setText(sdf.format(new Date(sesion.getFecha())));
            tvHorario.setText(String.format("%s – %s",
                    sesion.getHoraInicio(), sesion.getHoraFin()));
            tvTitulo.setText(sesion.getTitulo());
            tvTipo.setText(sesion.getTipo() != null ? sesion.getTipo().name() : "");
            tvModalidad.setText(sesion.getModalidad() != null ? sesion.getModalidad().name() : "");
            tvAforo.setText(String.format("%d / %d",
                    sesion.getAforoActual(), sesion.getAforoMax()));
        }
    }
}