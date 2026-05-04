package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.EstadoChat;
import com.example.sportiva_booking_android.v2.models.SoporteChat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptador para la lista de chats de soporte en el panel del administrador.
 * Muestra el nombre resuelto del cliente, el estado con badge de color
 * y la fecha del último mensaje.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    /*Interfaz de callback*/

    public interface OnChatClickListener {
        void onChatClick(SoporteChat chat);
    }

    /*Estado interno*/

    private List<SoporteChat>    chats           = new ArrayList<>();
    private Map<String, String>  nombresClientes;
    private final OnChatClickListener listener;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault());

    /*Constructor*/

    public ChatAdapter(OnChatClickListener listener) {
        this.listener = listener;
    }

    /*Métodos públicos*/

    public void submitList(List<SoporteChat> nuevos) {
        this.chats = nuevos != null ? nuevos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setNombresClientes(Map<String, String> nombres) {
        this.nombresClientes = nombres;
        notifyDataSetChanged();
    }

    /*RecyclerView.Adapter*/

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        SoporteChat chat = chats.get(position);

        /*Nombre del cliente — "Cargando..." hasta que llegue la resolución*/
        String nombre = (nombresClientes != null && nombresClientes.containsKey(chat.getClienteId()))
                ? nombresClientes.get(chat.getClienteId())
                : "Cargando...";
        holder.tvNombreCliente.setText(nombre);

        /* Badge de estado con tint según el valor del enum */
        EstadoChat estado = chat.getEstado();
        holder.tvEstado.setText(estado != null ? estado.name() : "");
        int tint;
        if (estado == EstadoChat.PENDIENTE)      tint = 0xFFFFA000;  // amber
        else if (estado == EstadoChat.ACTIVO)    tint = 0xFF388E3C;  // green
        else                                      tint = 0xFF757575;  // grey
        if (holder.tvEstado.getBackground() != null)
            holder.tvEstado.getBackground().setTint(tint);

        /*Fecha último mensaje*/
        holder.tvFecha.setText(sdf.format(new Date(chat.getFechaUltimoMensaje())));

        holder.itemView.setOnClickListener(v -> listener.onChatClick(chat));
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    /*ViewHolder*/

    static class ChatViewHolder extends RecyclerView.ViewHolder {

        TextView tvNombreCliente;
        TextView tvEstado;
        TextView tvFecha;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombreCliente = itemView.findViewById(R.id.tvNombreCliente);
            tvEstado        = itemView.findViewById(R.id.tvEstadoChat);
            tvFecha         = itemView.findViewById(R.id.tvFechaUltimoMensaje);
        }
    }
}