package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Cliente;

import java.util.List;

public class ClienteListAdapter extends RecyclerView.Adapter<ClienteListAdapter.ClienteViewHolder> {

    /*Interfaz que usaremos para comunicar el evento de eliminación al fragment*/
    public interface OnEliminarClienteListener {
        void onEliminar(String clienteUid);
    }

    /*Lista de clientes que vamos a mostrar en el RecyclerView*/
    private final List<Cliente> clientes;

    /*Listener que se dispara cuando el administrador pulsa el botón de eliminar en una fila*/
    private final OnEliminarClienteListener onEliminarListener;

    /*UID del cliente cuya fila está mostrando el spinner ahora mismo*/
    private String deletingUid = null;

    /*Constructor del adaptador*/
    public ClienteListAdapter(List<Cliente> clientes, OnEliminarClienteListener onEliminarListener) {
        this.clientes            = clientes;
        this.onEliminarListener  = onEliminarListener;
    }

    /**
     * Establece el UID del cliente que está siendo eliminado en este momento.
     * La fila correspondiente ocultará el botón y mostrará un ProgressBar mientras dura la operación.
     * Pasar null limpia el estado.
     * @param uid UID del cliente en proceso de borrado, o null para limpiar
     */
    public void setDeletingUid(String uid) {
        this.deletingUid = uid;
        notifyDataSetChanged();
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
        holder.bind(clientes.get(position), onEliminarListener, deletingUid);
    }

    @Override
    public int getItemCount() {
        return clientes.size();
    }

    /**
     * ViewHolder que contiene las referencias a las vistas de cada fila del listado
     */
    static class ClienteViewHolder extends RecyclerView.ViewHolder {

        private final TextView    tvNombre;
        private final TextView    tvApellidos;
        private final TextView    tvDni;
        private final ImageButton btnEliminar;
        private final ProgressBar progressEliminar;

        ClienteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre          = itemView.findViewById(R.id.tvNombreCliente);
            tvApellidos       = itemView.findViewById(R.id.tvApellidosCliente);
            tvDni             = itemView.findViewById(R.id.tvDniCliente);
            btnEliminar       = itemView.findViewById(R.id.btnEliminarCliente);
            progressEliminar  = itemView.findViewById(R.id.progressEliminarCliente);
        }

        /**
         * Vincula los datos del cliente a las vistas de la fila,
         * gestionando el estado del spinner individual durante el borrado.
         * @param cliente    Cliente a representar
         * @param listener   Callback de eliminación
         * @param deletingUid UID del cliente en proceso de borrado
         */
        void bind(Cliente cliente, OnEliminarClienteListener listener, String deletingUid) {

            /*Rellenamos cada columna con los datos del cliente*/
            tvNombre.setText(cliente.getNombre()    != null ? cliente.getNombre()    : "-");
            tvApellidos.setText(cliente.getApellidos() != null ? cliente.getApellidos() : "-");
            tvDni.setText(cliente.getDni()          != null ? cliente.getDni()          : "-");

            /*Mostramos el spinner o el botón según si esta fila está siendo procesada*/
            boolean isDeleting = cliente.getId() != null && cliente.getId().equals(deletingUid);
            btnEliminar.setVisibility(isDeleting ? View.GONE    : View.VISIBLE);
            progressEliminar.setVisibility(isDeleting ? View.VISIBLE : View.GONE);

            /*Al pulsar el botón de eliminar notificamos al fragment con el UID del cliente*/
            btnEliminar.setOnClickListener(v -> {
                if (listener != null && cliente.getId() != null) {
                    listener.onEliminar(cliente.getId());
                }
            });
        }
    }
}