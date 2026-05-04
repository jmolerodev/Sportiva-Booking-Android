package com.example.sportiva_booking_android.v2.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Mensaje;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador para las burbujas de mensajes del chat de soporte.
 * Alinea a la derecha los mensajes propios y a la izquierda los del otro participante,
 * aplicando el drawable de burbuja correspondiente en cada caso.
 */
public class MensajeAdapter extends RecyclerView.Adapter<MensajeAdapter.MensajeViewHolder> {

    /*Estado interno*/

    private List<Mensaje> mensajes = new ArrayList<>();
    private final String  miUid;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    /*Constructor*/

    public MensajeAdapter(String miUid) {
        this.miUid = miUid;
    }

    public void submitList(List<Mensaje> nuevos) {
        this.mensajes = nuevos != null ? nuevos : new ArrayList<>();
        notifyDataSetChanged();
    }

    /*RecyclerView.Adapter*/

    @NonNull
    @Override
    public MensajeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mensaje, parent, false);
        return new MensajeViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MensajeViewHolder holder, int position) {
        Mensaje msg   = mensajes.get(position);
        boolean esMio = miUid != null && miUid.equals(msg.getEmisorId());

        holder.tvTexto.setText(msg.getTexto());
        holder.tvFecha.setText(sdf.format(new Date(msg.getFecha())));

        /*Márgenes desde RecyclerView.LayoutParams (tipo correcto del itemView raíz)*/
        RecyclerView.LayoutParams params =
                (RecyclerView.LayoutParams) holder.llWrapper.getLayoutParams();

        if (esMio) {
            holder.llWrapper.setGravity(Gravity.END);
            params.setMarginStart(96);
            params.setMarginEnd(0);
            holder.llBurbuja.setBackgroundResource(R.drawable.bg_bubble_mine);
        } else {
            holder.llWrapper.setGravity(Gravity.START);
            params.setMarginStart(0);
            params.setMarginEnd(96);
            holder.llBurbuja.setBackgroundResource(R.drawable.bg_bubble_other);
        }
        holder.llWrapper.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return mensajes.size();
    }

    /*ViewHolder*/

    static class MensajeViewHolder extends RecyclerView.ViewHolder {

        LinearLayout llWrapper;
        LinearLayout llBurbuja;
        TextView     tvTexto;
        TextView     tvFecha;

        MensajeViewHolder(@NonNull View itemView) {
            super(itemView);
            llWrapper = itemView.findViewById(R.id.llMensajeWrapper);
            llBurbuja = itemView.findViewById(R.id.llBurbuja);
            tvTexto   = itemView.findViewById(R.id.tvTextoMensaje);
            tvFecha   = itemView.findViewById(R.id.tvFechaMensaje);
        }
    }
}