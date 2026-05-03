package com.example.sportiva_booking_android.v2.services;



import androidx.annotation.NonNull;

import com.example.sportiva_booking_android.v2.enums.EstadoChat;
import com.example.sportiva_booking_android.v2.models.Mensaje;
import com.example.sportiva_booking_android.v2.models.SoporteChat;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio que centraliza todas las operaciones de lectura y escritura
 * sobre el nodo Supports de Firebase RTDB.
 * Actúa como única fuente de verdad para los datos de soporte,
 * aislando los fragments de la lógica de acceso a Firebase.
 */
public class SoporteService {

    /* Nombre del nodo raíz de soporte en Firebase RTDB */
    private static final String COLLECTION = "Supports";

    private final DatabaseReference dbRef;

    public SoporteService() {
        this.dbRef = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Contrato de retorno para operaciones que devuelven una lista de chats.
     */
    public interface ChatsCallback {
        void onChatsObtenidos(List<SoporteChat> chats);
        void onError(String mensaje);
    }

    /**
     * Contrato de retorno para operaciones que devuelven una lista de mensajes.
     */
    public interface MensajesCallback {
        void onMensajesObtenidos(List<Mensaje> mensajes);
        void onError(String mensaje);
    }

    /**
     * Contrato de retorno para operaciones de escritura simples (sin datos de vuelta).
     */
    public interface WriteCallback {
        void onExito();
        void onError(String mensaje);
    }


    /**
     * Escucha en tiempo real todos los chats de soporte de un centro deportivo.
     * Filtra por centroId en cliente para mantener la coherencia con la lógica Angular,
     * ya que Firebase RTDB en Android no soporta orderByChild + equalTo con listVal.
     * Devuelve el ValueEventListener activo para que el fragment pueda cancelarlo
     * en onDestroyView y evitar fugas de memoria.
     *
     * @param centroId UID del centro deportivo administrado
     * @param callback Retorno con la lista de chats o el error producido
     * @return ValueEventListener activo — el llamador debe cancelarlo con removeEventListener
     */
    public ValueEventListener escucharChatsByCentro(String centroId, ChatsCallback callback) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SoporteChat> lista = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SoporteChat chat = child.getValue(SoporteChat.class);
                    if (chat != null && centroId.equals(chat.getCentroId())) {
                        chat.setId(child.getKey());
                        lista.add(chat);
                    }
                }
                callback.onChatsObtenidos(lista);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };

        dbRef.child(COLLECTION).addValueEventListener(listener);
        return listener;
    }

    /**
     * Escucha en tiempo real todos los chats de soporte de un cliente concreto.
     * Filtra por clienteId en cliente siguiendo el mismo patrón que escucharChatsByCentro.
     * Devuelve el ValueEventListener activo para que el fragment pueda cancelarlo
     * en onDestroyView y evitar fugas de memoria.
     *
     * @param clienteId UID del cliente autenticado
     * @param callback  Retorno con la lista de chats o el error producido
     * @return ValueEventListener activo — el llamador debe cancelarlo con removeEventListener
     */
    public ValueEventListener escucharChatsByCliente(String clienteId, ChatsCallback callback) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SoporteChat> lista = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SoporteChat chat = child.getValue(SoporteChat.class);
                    if (chat != null && clienteId.equals(chat.getClienteId())) {
                        chat.setId(child.getKey());
                        lista.add(chat);
                    }
                }
                callback.onChatsObtenidos(lista);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };

        dbRef.child(COLLECTION).addValueEventListener(listener);
        return listener;
    }

    /**
     * Escucha en tiempo real los mensajes de un chat concreto usando ChildEventListener
     * para reaccionar al instante ante cada mensaje nuevo sin recargar la lista entera.
     * Devuelve el ChildEventListener activo para que el fragment pueda cancelarlo
     * en onDestroyView y evitar fugas de memoria.
     *
     * @param chatId   UID del nodo SoporteChat en Firebase
     * @param callback Retorno con la lista acumulada de mensajes o el error producido
     * @return ChildEventListener activo — el llamador debe cancelarlo con removeEventListener
     */
    public ChildEventListener escucharMensajesByChat(String chatId, MensajesCallback callback) {
        List<Mensaje> acumulados = new ArrayList<>();

        ChildEventListener listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                Mensaje mensaje = snapshot.getValue(Mensaje.class);
                if (mensaje != null) {
                    mensaje.setId(snapshot.getKey());
                    acumulados.add(mensaje);
                    callback.onMensajesObtenidos(new ArrayList<>(acumulados));
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };

        dbRef.child(COLLECTION).child(chatId).child("mensajes")
                .addChildEventListener(listener);
        return listener;
    }

    /**
     * Cancela el listener de mensajes de un chat concreto.
     * Se llama desde el fragment al cambiar de chat o en onDestroyView
     * para liberar el listener de Firebase y evitar fugas de memoria.
     *
     * @param chatId   UID del chat cuyo listener se quiere cancelar
     * @param listener ChildEventListener activo devuelto por escucharMensajesByChat
     */
    public void cancelarListenerMensajes(String chatId, ChildEventListener listener) {
        if (listener == null) return;
        dbRef.child(COLLECTION).child(chatId).child("mensajes")
                .removeEventListener(listener);
    }

    /**
     * Cancela el listener de chats registrado sobre el nodo raíz Supports.
     * Se llama desde el fragment en onDestroyView para liberar el listener de Firebase.
     *
     * @param listener ValueEventListener activo devuelto por escucharChatsByCentro
     *                 o escucharChatsByCliente
     */
    public void cancelarListenerChats(ValueEventListener listener) {
        if (listener == null) return;
        dbRef.child(COLLECTION).removeEventListener(listener);
    }



    /**
     * Crea una nueva solicitud de chat de soporte inicializada en estado PENDIENTE.
     * La escritura se realiza en dos operaciones separadas para evitar el conflicto
     * de rutas ancestro/descendiente que Firebase no permite en un único update:
     * primero se persiste la cabecera del chat y después el primer mensaje como subárbol.
     *
     * @param centroId      UID del centro deportivo destinatario
     * @param clienteId     UID del cliente que solicita el soporte
     * @param adminId       UID del administrador del centro
     * @param primerMensaje Texto del mensaje inicial de la solicitud
     * @param callback      Retorno de éxito o error
     */
    public void solicitarChat(String centroId, String clienteId, String adminId,
                              String primerMensaje, WriteCallback callback) {
        DatabaseReference chatRef = dbRef.child(COLLECTION).push();
        String chatId = chatRef.getKey();
        long   ahora  = System.currentTimeMillis();

        SoporteChat chat = new SoporteChat();
        chat.setCentroId(centroId);
        chat.setClienteId(clienteId);
        chat.setAdminId(adminId);
        chat.setEstado(EstadoChat.PENDIENTE);
        chat.setFechaCreacion(ahora);
        chat.setFechaUltimoMensaje(ahora);

        /* 1.- Escribimos la cabecera del chat */
        chatRef.setValue(chat).addOnSuccessListener(unused -> {

            /* 2.- Escribimos el primer mensaje como subárbol ya existente */
            DatabaseReference mensajeRef = dbRef.child(COLLECTION)
                    .child(chatId).child("mensajes").push();

            Mensaje mensaje = new Mensaje();
            mensaje.setEmisorId(clienteId);
            mensaje.setTexto(primerMensaje);
            mensaje.setFecha(ahora);

            mensajeRef.setValue(mensaje)
                    .addOnSuccessListener(v -> callback.onExito())
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));

        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Envía un mensaje de texto dentro de un chat de soporte ya existente.
     * Actualiza simultáneamente el subárbol de mensajes y el timestamp
     * del nodo raíz para facilitar ordenaciones por actividad reciente.
     *
     * @param chatId   UID del chat de soporte
     * @param emisorId UID del usuario que envía el mensaje (cliente o admin)
     * @param texto    Contenido textual del mensaje
     * @param callback Retorno de éxito o error
     */
    public void enviarMensaje(String chatId, String emisorId,
                              String texto, WriteCallback callback) {
        DatabaseReference mensajeRef = dbRef.child(COLLECTION)
                .child(chatId).child("mensajes").push();
        long ahora = System.currentTimeMillis();

        Mensaje mensaje = new Mensaje();
        mensaje.setEmisorId(emisorId);
        mensaje.setTexto(texto);
        mensaje.setFecha(ahora);

        /* Escritura atómica: nuevo mensaje + timestamp del chat actualizado */
        mensajeRef.setValue(mensaje).addOnSuccessListener(unused ->
                dbRef.child(COLLECTION).child(chatId)
                        .child("fechaUltimoMensaje").setValue(ahora)
                        .addOnSuccessListener(v -> callback.onExito())
                        .addOnFailureListener(e -> callback.onError(e.getMessage()))
        ).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Acepta una solicitud de chat pendiente cambiando su estado a ACTIVO.
     * A partir de este momento ambos participantes pueden intercambiar mensajes.
     *
     * @param chatId   UID del chat de soporte a activar
     * @param callback Retorno de éxito o error
     */
    public void aceptarChat(String chatId, WriteCallback callback) {
        dbRef.child(COLLECTION).child(chatId).child("estado")
                .setValue(EstadoChat.ACTIVO.name())
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Rechaza una solicitud de chat pendiente marcándola como CERRADO.
     *
     * @param chatId   UID del chat de soporte a rechazar
     * @param callback Retorno de éxito o error
     */
    public void rechazarChat(String chatId, WriteCallback callback) {
        dbRef.child(COLLECTION).child(chatId).child("estado")
                .setValue(EstadoChat.CERRADO.name())
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cierra un chat activo una vez que el asunto ha sido resuelto.
     *
     * @param chatId   UID del chat de soporte a cerrar
     * @param callback Retorno de éxito o error
     */
    public void cerrarChat(String chatId, WriteCallback callback) {
        dbRef.child(COLLECTION).child(chatId).child("estado")
                .setValue(EstadoChat.CERRADO.name())
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Elimina permanentemente un nodo de chat de soporte y todos sus mensajes.
     * Solo debe invocarse sobre chats en estado CERRADO para evitar pérdidas
     * accidentales de conversaciones activas o pendientes de resolución.
     *
     * @param chatId   UID del nodo SoporteChat a eliminar en Firebase
     * @param callback Retorno de éxito o error
     */
    public void eliminarChat(String chatId, WriteCallback callback) {
        dbRef.child(COLLECTION).child(chatId).removeValue()
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}