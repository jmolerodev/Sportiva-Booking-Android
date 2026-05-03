package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Cliente;

import java.util.List;

public class ClienteListAdapter extends RecyclerView.Adapter<ClienteListAdapter.ClienteViewHolder> {

    /*Lista de clientes que vamos a mostrar en el RecyclerView*/
    private final List<Cliente> clientes;

    /*Listener que se dispara cuando el administrador pulsa el botón de eliminar en una fila*/
    private final OnEliminarClienteListener onEliminarListener;

    /*Interfaz que usaremos para comunicar el evento de eliminación al fragment*/
    public interface OnEliminarClienteListener {
        void onEliminar(String clienteUid);
    }

    /*Constructor del adaptador*/
    public ClienteListAdapter(List<Cliente> clientes, OnEliminarClienteListener onEliminarListener) {
        this.clientes           = clientes;
        this.onEliminarListener = onEliminarListener;
    }

    @NonNull
    @Override
    public ClienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cliente, parent, false);
        return new ClienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClienteViewHolder holder, int position) {
        Cliente cliente = clientes.get(position);

        /*Rellenamos cada columna con los datos del cliente*/
        holder.tvNombre.setText(cliente.getNombre() != null ? cliente.getNombre() : "-");
        holder.tvApellidos.setText(cliente.getApellidos() != null ? cliente.getApellidos() : "-");
        holder.tvDni.setText(cliente.getDni() != null ? cliente.getDni() : "-");

        /*Al pulsar el botón de eliminar notificamos al fragment con el UID del cliente*/
        holder.btnEliminar.setOnClickListener(v -> {
            if (cliente.getId() != null) {
                onEliminarListener.onEliminar(cliente.getId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return clientes.size();
    }

    /**
     * ViewHolder que contiene las referencias a las vistas de cada fila del listado
     */
    public static class ClienteViewHolder extends RecyclerView.ViewHolder {

        TextView tvNombre;
        TextView tvApellidos;
        TextView tvDni;
        CardView btnEliminar;

        public ClienteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre    = itemView.findViewById(R.id.tvNombreCliente);
            tvApellidos = itemView.findViewById(R.id.tvApellidosCliente);
            tvDni       = itemView.findViewById(R.id.tvDniCliente);
            btnEliminar = itemView.findViewById(R.id.btnEliminarCliente);
        }
    }
}