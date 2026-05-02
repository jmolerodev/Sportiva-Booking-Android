package com.example.sportiva_booking_android.v2.services;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.models.Media;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsula todas las operaciones contra Firebase para el módulo multimedia.
 */
public class MediaService {

    /* Nodo raíz en la base de datos */
    private static final String COLLECTION_NAME = "Media";

    /* Carpeta en Storage donde se guardan los vídeos */
    private static final String STORAGE_PATH = "Profesional-Media";

    private final DatabaseReference databaseRef;
    private final FirebaseStorage   storage;

    public MediaService() {
        this.databaseRef = FirebaseDatabase.getInstance().getReference();
        this.storage     = FirebaseStorage.getInstance();
    }



    public interface MediaListCallback {
        void onSuccess(List<Media> mediaList);
        void onError(String errorMessage);
    }

    public interface MediaUploadCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public interface MediaDeleteCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    /**
     * Obtiene en tiempo real todos los vídeos subidos por un profesional concreto.
     *
     * @param profesionalId UID del profesional
     * @param callback      Resultado con la lista o el error
     */
    public void getMediaByProfesional(String profesionalId, MediaListCallback callback) {
        Query query = databaseRef
                .child(COLLECTION_NAME)
                .orderByChild("profesionalId")
                .equalTo(profesionalId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Media> lista = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Media media = child.getValue(Media.class);
                    if (media != null) {
                        media.setId(child.getKey());
                        lista.add(media);
                    }
                }
                callback.onSuccess(lista);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Obtiene en tiempo real todos los vídeos vinculados a un centro deportivo.
     * Útil para mostrar la galería del centro independientemente del profesional que los subió.
     *
     * @param centroId UID del centro deportivo
     * @param callback Resultado con la lista o el error
     */
    public void getMediaByCentro(String centroId, MediaListCallback callback) {
        Query query = databaseRef
                .child(COLLECTION_NAME)
                .orderByChild("centroId")
                .equalTo(centroId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Media> lista = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Media media = child.getValue(Media.class);
                    if (media != null) {
                        media.setId(child.getKey());
                        lista.add(media);
                    }
                }
                callback.onSuccess(lista);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Sube el archivo a Storage y registra sus metadatos en la base de datos.
     * El orden es: push() reserva el nodo, sube el archivo, obtiene la URL y guarda el registro.
     *
     * @param fileUri   URI local del archivo de vídeo seleccionado
     * @param fileName  Nombre original del archivo
     * @param media     Objeto Media con los metadatos (la url se rellena aquí tras la subida)
     * @param callback  Resultado de éxito o error
     */
    public void uploadAndSaveMedia(Uri fileUri, String fileName, Media media, MediaUploadCallback callback) {

        /* push() reserva un nodo en la base de datos y nos da su clave única */
        DatabaseReference newNodeRef  = databaseRef.child(COLLECTION_NAME).push();
        String            firebaseUid = newNodeRef.getKey();

        if (firebaseUid == null) {
            callback.onError("Error generando el identificador del nodo");
            return;
        }

        /* Usamos la clave del nodo como prefijo del archivo para que ambos estén vinculados */
        String           filePath         = STORAGE_PATH + "/" + firebaseUid + "_" + fileName;
        StorageReference storageReference = storage.getReference().child(filePath);

        storageReference.putFile(fileUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return storageReference.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    media.setUrl(downloadUri.toString());
                    media.setFecha_subida(System.currentTimeMillis());

                    newNodeRef.setValue(media)
                            .addOnSuccessListener(aVoid -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Elimina el vídeo en dos pasos: primero el archivo en Storage, después el registro
     * en la base de datos. Si el archivo de Storage ya no existiese, continúa igualmente
     * con el borrado del registro para no dejar datos huérfanos.
     *
     * @param url      URL de descarga del vídeo en Firebase Storage
     * @param callback Resultado de éxito o error
     */
    public void deleteMediaByUrl(String url, MediaDeleteCallback callback) {
        Query query = databaseRef
                .child(COLLECTION_NAME)
                .orderByChild("url")
                .equalTo(url);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onError("No se encontró el registro multimedia");
                    return;
                }

                String nodeKey = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    nodeKey = child.getKey();
                    break;
                }
                final String finalNodeKey = nodeKey;

                String storagePath = extraerRutaStorage(url);

                if (storagePath != null) {
                    storage.getReference().child(storagePath).delete()
                            .addOnCompleteListener(task -> eliminarNodoBaseDatos(finalNodeKey, callback));
                } else {
                    eliminarNodoBaseDatos(finalNodeKey, callback);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Extrae la ruta del archivo en Storage a partir de su URL de descarga.
     * Las URLs de Firebase tienen el formato: .../o/RUTA_ENCODED?token=...
     */
    private String extraerRutaStorage(String downloadUrl) {
        try {
            String[] partes = downloadUrl.split("/o/");
            if (partes.length < 2) return null;
            return Uri.decode(partes[1].split("\\?")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Elimina el nodo de la base de datos una vez borrado el archivo de Storage.
     */
    private void eliminarNodoBaseDatos(String nodeKey, MediaDeleteCallback callback) {
        if (nodeKey == null) {
            callback.onError("Clave de nodo nula, no se puede eliminar");
            return;
        }
        databaseRef.child(COLLECTION_NAME).child(nodeKey)
                .removeValue()
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}