package com.example.sportiva_booking_android.v2.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Profesional;

import java.util.List;

/**
 * Adaptador del RecyclerView que muestra la lista de profesionales vinculados
 * al administrador autenticado.
 * Gestiona el spinner de fila individual durante el proceso de eliminación,
 * equivalente al control deletingUid del componente Angular.
 */
public class ProfesionalListAdapter
        extends RecyclerView.Adapter<ProfesionalListAdapter.ProfesionalViewHolder> {

    private static final String TAG = "ProfesionalAdapter";

    /**
     * Interfaz de callback para notificar al Fragment la intención de eliminar un profesional.
     */
    public interface OnEliminarClickListener {
        void onEliminar(String uid);
    }

    /* lista de profesionales que alimenta el adaptador */
    private final List<Profesional> profesionales;

    /* listener que delega la acción de eliminar al Fragment */
    private final OnEliminarClickListener onEliminarClick;

    /* UID del profesional cuya fila está mostrando el spinner de eliminación */
    private String deletingUid = null;

    /**
     * Constructor del adaptador.
     *
     * @param profesionales   Lista de profesionales a mostrar
     * @param onEliminarClick Listener para la acción de eliminar
     */
    public ProfesionalListAdapter(List<Profesional> profesionales,
                                  OnEliminarClickListener onEliminarClick) {
        this.profesionales   = profesionales;
        this.onEliminarClick = onEliminarClick;
    }

    /**
     * Actualiza el UID del profesional que está siendo eliminado.
     * Pasar null desactiva cualquier spinner de fila activo.
     *
     * @param uid UID del profesional en proceso de eliminación, o null para desactivar
     */
    public void setDeletingUid(String uid) {
        this.deletingUid = uid;
    }

    @NonNull
    @Override
    public ProfesionalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profesional, parent, false);
        return new ProfesionalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfesionalViewHolder holder, int position) {
        Profesional profesional = profesionales.get(position);
        /* defendemos null en getId() para evitar NPE cuando deletingUid es null */
        boolean eliminando = deletingUid != null
                && deletingUid.equals(profesional.getId());
        holder.bind(profesional, eliminando, onEliminarClick);
    }

    @Override
    public int getItemCount() {
        return profesionales.size();
    }



    /**
     * ViewHolder que representa una fila de la tabla de profesionales.
     * Controla la visibilidad del spinner individual frente al botón de eliminar,
     * replicando el comportamiento del [disabled] + spinner Angular por fila.
     */
    static class ProfesionalViewHolder extends RecyclerView.ViewHolder {

        /* nombre completo del profesional */
        private final TextView    tvNombre;

        /* especialidad del profesional */
        private final TextView    tvEspecialidad;

        /* años de experiencia */
        private final TextView    tvExperiencia;

        /* botón de eliminar — se oculta mientras se elimina esta fila */
        private final ImageButton btnEliminar;

        /* spinner visible únicamente mientras se elimina esta fila */
        private final ProgressBar pbEliminar;

        /**
         * Constructor del ViewHolder.
         * Incluye logs de diagnóstico para detectar IDs no encontrados en el layout.
         *
         * @param itemView Vista raíz del item inflada por el adaptador
         */
        ProfesionalViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre       = itemView.findViewById(R.id.tvNombreProfItem);
            tvEspecialidad = itemView.findViewById(R.id.tvEspecialidadProfItem);
            tvExperiencia  = itemView.findViewById(R.id.tvExperienciaProfItem);
            btnEliminar    = itemView.findViewById(R.id.btnEliminarProfItem);
            pbEliminar     = itemView.findViewById(R.id.pbEliminarProfItem);


        }

        /**
         * Rellena las vistas con los datos del profesional y gestiona el estado del botón.
         * Todos los campos de cadena se defienden contra null — Firebase puede devolver
         * nodos incompletos si el registro fue creado con una versión anterior del modelo.
         *
         * @param profesional     Modelo con los datos a mostrar
         * @param eliminando      true si este profesional está siendo eliminado ahora mismo
         * @param onEliminarClick Listener a notificar cuando el usuario pulse eliminar
         */
        void bind(Profesional profesional,
                  boolean eliminando,
                  OnEliminarClickListener onEliminarClick) {

            /* defendemos nulls — Firebase puede devolver campos vacíos en registros antiguos */
            String nombre    = profesional.getNombre()       != null ? profesional.getNombre()       : "";
            String apellidos = profesional.getApellidos()    != null ? profesional.getApellidos()    : "";
            String espec     = profesional.getEspecialidad() != null ? profesional.getEspecialidad().toString() : "-";

            tvNombre.setText(String.format("%s %s", nombre, apellidos).trim());
            tvEspecialidad.setText(espec);
            tvExperiencia.setText(String.format("%d años", profesional.getAnnos_experiencia()));

            /* alternamos entre el botón y el spinner según el estado de eliminación */
            if (eliminando) {
                btnEliminar.setVisibility(View.GONE);
                pbEliminar.setVisibility(View.VISIBLE);
            } else {
                pbEliminar.setVisibility(View.GONE);
                btnEliminar.setVisibility(View.VISIBLE);
                btnEliminar.setOnClickListener(v ->
                        onEliminarClick.onEliminar(profesional.getId()));
            }
        }
    }
}