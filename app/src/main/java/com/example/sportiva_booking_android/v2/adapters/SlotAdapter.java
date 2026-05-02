package com.example.sportiva_booking_android.v2.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.EstadoSlot;
import com.example.sportiva_booking_android.v2.models.SlotHorario;

import java.util.List;

public class SlotAdapter extends RecyclerView.Adapter<SlotAdapter.SlotViewHolder> {


    /**
     * Notifica al fragmento que el profesional ha pulsado un slot LIBRE
     * para abrir el diálogo de creación de sesión.
     */
    public interface OnSlotClickListener {
        void onSlotLibreClick(SlotHorario slot);
    }

    /**
     * Notifica al fragmento que el profesional quiere cancelar
     * la sesión asociada a un slot PROPIO.
     */
    public interface OnCancelarSesionListener {
        void onCancelarSesionClick(SlotHorario slot);
    }


    private final List<SlotHorario> slots;
    private final OnSlotClickListener onSlotClickListener;
    private final OnCancelarSesionListener onCancelarSesionListener;


    public SlotAdapter(List<SlotHorario> slots,
                       OnSlotClickListener onSlotClickListener,
                       OnCancelarSesionListener onCancelarSesionListener) {
        this.slots = slots;
        this.onSlotClickListener = onSlotClickListener;
        this.onCancelarSesionListener = onCancelarSesionListener;
    }


    @NonNull
    @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_slot, parent, false);
        return new SlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        holder.bind(slots.get(position));
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }


    public class SlotViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvHorario;
        private final TextView tvEstadoBadge;
        private final TextView tvTituloSesion;
        private final TextView tvTipo;
        private final TextView tvModalidad;
        private final TextView tvAforo;
        private final ImageButton btnCancelar;

        public SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHorario = itemView.findViewById(R.id.tvSlotHorario);
            tvEstadoBadge = itemView.findViewById(R.id.tvSlotEstadoBadge);
            tvTituloSesion = itemView.findViewById(R.id.tvSlotTituloSesion);
            tvTipo = itemView.findViewById(R.id.tvSlotTipo);
            tvModalidad = itemView.findViewById(R.id.tvSlotModalidad);
            tvAforo = itemView.findViewById(R.id.tvSlotAforo);
            btnCancelar = itemView.findViewById(R.id.btnSlotCancelar);
        }

        /**
         * Pinta el slot según su estado: LIBRE, PROPIO u OCUPADO.
         * Solo los slots LIBRE disparan el listener de creación al pulsar el item.
         * Solo los slots PROPIO muestran el botón de cancelar.
         *
         * @param slot Slot horario a representar en esta celda
         */
        public void bind(SlotHorario slot) {
            tvHorario.setText(String.format("%s – %s", slot.getHoraInicio(), slot.getHoraFin()));

            if (slot.getEstado() == EstadoSlot.LIBRE) {
                bindLibre(slot);
            } else if (slot.getEstado() == EstadoSlot.PROPIO) {
                bindPropio(slot);
            } else {
                bindOcupado(slot);
            }
        }

        /**
         * Configura el item para un slot libre: muestra el badge "Disponible"
         * y habilita el click en el item para abrir el diálogo de creación.
         */
        private void bindLibre(SlotHorario slot) {
            tvEstadoBadge.setText("Disponible");
            tvEstadoBadge.setVisibility(View.VISIBLE);
            tvTituloSesion.setVisibility(View.GONE);
            tvTipo.setVisibility(View.GONE);
            tvModalidad.setVisibility(View.GONE);
            tvAforo.setVisibility(View.GONE);
            btnCancelar.setVisibility(View.GONE);

            itemView.setOnClickListener(v -> onSlotClickListener.onSlotLibreClick(slot));
        }

        /**
         * Configura el item para un slot con sesión propia: muestra título, tipo,
         * modalidad y aforo, y habilita el botón de cancelar.
         */
        @SuppressLint("DefaultLocale")
        private void bindPropio(SlotHorario slot) {
            tvEstadoBadge.setVisibility(View.GONE);
            tvTituloSesion.setText(slot.getSesion().getTitulo());
            tvTituloSesion.setVisibility(View.VISIBLE);
            tvTipo.setText(slot.getSesion().getTipo() != null ? slot.getSesion().getTipo().name() : "");
            tvTipo.setVisibility(View.VISIBLE);
            tvModalidad.setText(slot.getSesion().getModalidad() != null ? slot.getSesion().getModalidad().name() : "");
            tvModalidad.setVisibility(View.VISIBLE);
            tvAforo.setText(String.format("%d/%d", slot.getSesion().getAforoActual(), slot.getSesion().getAforoMax()));
            tvAforo.setVisibility(View.VISIBLE);
            btnCancelar.setVisibility(View.VISIBLE);

            itemView.setOnClickListener(null);
            btnCancelar.setOnClickListener(v -> onCancelarSesionListener.onCancelarSesionClick(slot));
        }

        /**
         * Configura el item para un slot ocupado por otro profesional:
         * muestra el título en modo muted y el badge "Ocupado" sin acciones.
         */
        private void bindOcupado(SlotHorario slot) {
            tvEstadoBadge.setText("Ocupado");
            tvEstadoBadge.setVisibility(View.VISIBLE);
            tvTituloSesion.setText(slot.getSesion().getTitulo());
            tvTituloSesion.setVisibility(View.VISIBLE);
            tvTipo.setVisibility(View.GONE);
            tvModalidad.setVisibility(View.GONE);
            tvAforo.setVisibility(View.GONE);
            btnCancelar.setVisibility(View.GONE);

            itemView.setOnClickListener(null);
        }
    }
}
