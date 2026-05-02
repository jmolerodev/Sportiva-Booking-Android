package com.example.sportiva_booking_android.v2.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.models.Media;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador para el RecyclerView de vídeos del profesional.
 * Cada fila muestra el título, la fecha de subida, la descripción
 * y los botones de reproducir y eliminar.
 */
public class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /* Callback que se dispara cuando el usuario pulsa reproducir */
    public interface OnReproducirListener {
        void onReproducir(Media media);
    }

    /* Callback que se dispara cuando el usuario pulsa eliminar */
    public interface OnEliminarListener {
        void onEliminar(String url);
    }

    private final List<Media>          lista;
    private final OnReproducirListener reproducirListener;
    private final OnEliminarListener   eliminarListener;

    public MediaAdapter(List<Media> lista,
                        OnReproducirListener reproducirListener,
                        OnEliminarListener eliminarListener) {
        this.lista              = lista;
        this.reproducirListener = reproducirListener;
        this.eliminarListener   = eliminarListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new RecyclerView.ViewHolder(view) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Media media = lista.get(position);
        View  v     = holder.itemView;

        TextView tvNombre      = v.findViewById(R.id.tvMediaNombre);
        TextView tvFecha       = v.findViewById(R.id.tvMediaFecha);
        TextView tvDescripcion = v.findViewById(R.id.tvMediaDescripcion);
        Button   btnReproducir = v.findViewById(R.id.btnReproducirMedia);
        Button   btnEliminar   = v.findViewById(R.id.btnEliminarMedia);

        tvNombre.setText(media.getNombre());
        tvDescripcion.setText(media.getDescripcion());

        /* Formateamos el timestamp Unix a dd/MM/yyyy · HH:mm */
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault());
        tvFecha.setText(sdf.format(new Date(media.getFecha_subida())));

        btnReproducir.setOnClickListener(v2 -> {
            if (reproducirListener != null) reproducirListener.onReproducir(media);
        });

        btnEliminar.setOnClickListener(v2 -> {
            if (eliminarListener != null) eliminarListener.onEliminar(media.getUrl());
        });
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }
}