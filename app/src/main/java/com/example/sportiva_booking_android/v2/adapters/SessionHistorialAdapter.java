package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.EstadoSesion;
import com.example.sportiva_booking_android.v2.models.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionHistorialAdapter extends RecyclerView.Adapter<SessionHistorialAdapter.HistorialViewHolder> {

    /**
     * Notifica al fragmento que el profesional quiere eliminar físicamente
     * una sesión del historial tras confirmación.
     */
    public interface OnEliminarSesionListener {
        void onEliminarSesionClick(Session sesion);
    }

    private final List<Session> sesiones;
    private final OnEliminarSesionListener onEliminarSesionListener;

    /* Formato de fecha legible para la columna Fecha del historial */
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("EEE dd MMM yyyy", new Locale("es", "ES"));


    public SessionHistorialAdapter(List<Session> sesiones,
                                   OnEliminarSesionListener onEliminarSesionListener) {
        this.sesiones = sesiones;
        this.onEliminarSesionListener = onEliminarSesionListener;
    }


    @NonNull
    @Override
    public HistorialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sesion_historial, parent, false);
        return new HistorialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistorialViewHolder holder, int position) {
        holder.bind(sesiones.get(position));
    }

    @Override
    public int getItemCount() {
        return sesiones.size();
    }


    public class HistorialViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvFecha;
        private final TextView tvHorario;
        private final TextView tvTitulo;
        private final TextView tvTipo;
        private final TextView tvModalidad;
        private final TextView tvEstadoBadge;
        private final ImageButton btnEliminar;

        public HistorialViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tvHistorialFecha);
            tvHorario = itemView.findViewById(R.id.tvHistorialHorario);
            tvTitulo = itemView.findViewById(R.id.tvHistorialTitulo);
            tvTipo = itemView.findViewById(R.id.tvHistorialTipo);
            tvModalidad = itemView.findViewById(R.id.tvHistorialModalidad);
            tvEstadoBadge = itemView.findViewById(R.id.tvHistorialEstadoBadge);
            btnEliminar = itemView.findViewById(R.id.btnHistorialEliminar);
        }

        /**
         * Pinta la fila del historial con los datos de la sesión.
         * El badge de estado muestra FINALIZADA para sesiones ACTIVAS cuya hora
         * de fin ya ha pasado, y CANCELADA para las canceladas explícitamente.
         * Esta distinción la calcula el fragmento antes de pasarlas al adaptador,
         * usando el campo estadoVista que se añade como tag en el modelo.
         *
         * @param sesion Sesión del historial a representar en esta fila
         */
        public void bind(Session sesion) {
            tvFecha.setText(sdf.format(new Date(sesion.getFecha())));
            tvHorario.setText(String.format("%s – %s",
                    sesion.getHoraInicio(), sesion.getHoraFin()));
            tvTitulo.setText(sesion.getTitulo());
            tvTipo.setText(sesion.getTipo() != null ? sesion.getTipo().name() : "");
            tvModalidad.setText(sesion.getModalidad() != null ? sesion.getModalidad().name() : "");

            /* El fragmento marca las sesiones finalizadas sobreescribiendo el estado
             * a FINALIZADA antes de pasarlas al adaptador, igual que estadoVista en Angular */
            if (sesion.getEstado() == EstadoSesion.CANCELADA) {
                tvEstadoBadge.setText("CANCELADA");
            } else {
                tvEstadoBadge.setText("FINALIZADA");
            }

            btnEliminar.setOnClickListener(v -> onEliminarSesionListener.onEliminarSesionClick(sesion));
        }
    }
}
