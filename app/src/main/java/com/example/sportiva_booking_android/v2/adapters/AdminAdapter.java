package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Administrador;

import java.util.List;

public class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.AdminViewHolder> {

    /*Interfaz de callback para la acción de eliminar*/
    public interface OnDeleteClickListener {
        void onDeleteClick(Administrador administrador);
    }

    /*Lista de administradores a mostrar*/
    private final List<Administrador> administradores;

    /*Listener que se dispara al pulsar el botón eliminar*/
    private final OnDeleteClickListener deleteListener;

    /*UID del administrador cuya fila está mostrando el spinner ahora mismo*/
    private String deletingUid = null;

    public AdminAdapter(List<Administrador> administradores,
                        OnDeleteClickListener deleteListener) {
        this.administradores = administradores;
        this.deleteListener  = deleteListener;
    }

    /**
     * Establece el UID del administrador que está siendo eliminado en este momento.
     * La fila correspondiente ocultará el botón y mostrará un ProgressBar mientras dura la operación.
     * Pasar null limpia el estado.
     * @param uid UID del administrador en proceso de borrado, o null para limpiar
     */
    public void setDeletingUid(String uid) {
        this.deletingUid = uid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AdminViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin, parent, false);
        return new AdminViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull AdminViewHolder holder, int position) {
        holder.bind(administradores.get(position), deleteListener, deletingUid);
    }

    @Override
    public int getItemCount() {
        return administradores.size();
    }

    static class AdminViewHolder extends RecyclerView.ViewHolder {

        private final TextView    tvNombre;
        private final TextView    tvApellidos;
        private final ImageButton btnEliminar;
        private final ProgressBar progressEliminar;

        AdminViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre         = itemView.findViewById(R.id.tvNombre);
            tvApellidos      = itemView.findViewById(R.id.tvApellidos);

            btnEliminar      = itemView.findViewById(R.id.btnEliminar);
            progressEliminar = itemView.findViewById(R.id.progressEliminar);
        }

        /**
         * Vincula los datos del administrador a las vistas de la fila,
         * gestionando el estado del spinner individual durante el borrado.
         * @param admin      Administrador a representar
         * @param listener   Callback de eliminación
         * @param deletingUid UID del administrador en proceso de borrado
         */
        void bind(Administrador admin, OnDeleteClickListener listener, String deletingUid) {

            tvNombre.setText(admin.getNombre());
            tvApellidos.setText(admin.getApellidos());

            boolean isDeleting = admin.getId().equals(deletingUid);
            btnEliminar.setVisibility(isDeleting ? View.GONE    : View.VISIBLE);
            progressEliminar.setVisibility(isDeleting ? View.VISIBLE : View.GONE);

            btnEliminar.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(admin);
            });
        }
    }
}