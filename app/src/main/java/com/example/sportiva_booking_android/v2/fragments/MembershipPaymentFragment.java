package com.example.sportiva_booking_android.v2.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.EstadoMembresia;
import com.example.sportiva_booking_android.v2.enums.TipoMembresia;
import com.example.sportiva_booking_android.v2.models.Membership;
import com.example.sportiva_booking_android.v2.models.SportCentre;
import com.example.sportiva_booking_android.v2.services.MembershipService;
import com.example.sportiva_booking_android.v2.services.SportCentreService;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

public class MembershipPaymentFragment extends Fragment {

    private static final String ARG_CENTRO_ID = "centroId";

    /*Client ID de PayPal Sandbox.
     * Sustituir por el Client ID de producción antes del despliegue definitivo*/
    private static final String PAYPAL_CLIENT_ID =
            "AWRw3Prgzs2OQxc-rhGZEcKeXUKt6H27e86SitjqKk03fWt12uMOV73Ev0GRGfC_uDo-E7bhoWWeu8v8";

    /*Client Secrey de Paypal Developer*/
    private static final String PAYPAL_CLIENT_SECRET = "EG01IKKJ-5UFCikbOCuDI3agmOUhOP_zPI38jjOSCZu0ZwETcojH476zivTg5V6RBUx2gY8wU4Tlij-t";

    /*URLs de retorno deep link que PayPal usará al aprobar o cancelar el pago.
     * Deben coincidir exactamente con el intent-filter declarado en el Manifest*/
    private static final String RETURN_URL = "sportiva://payment/success";
    private static final String CANCEL_URL = "sportiva://payment/cancel";

    /*Precios en euros para cada tipo de membresía.
     * Centralizados aquí para facilitar actualizaciones futuras de tarifas*/
    private static final double PRECIO_MENSUAL   = 29.99;
    private static final double PRECIO_SEMESTRAL = 149.99;
    private static final double PRECIO_ANUAL     = 249.99;

    /*Argumentos*/
    private String centroId;
    private String clienteUid;

    /*Modelos en memoria*/
    private SportCentre   centro           = null;
    private TipoMembresia tipoSeleccionado = null;

    /*Token OAuth de PayPal obtenido en el paso previo a crear la orden.
     * Se guarda en campo para reutilizarlo en la captura tras el deep link*/
    private String paypalAccessToken = null;

    /*Flags de control de estado*/
    private boolean pagoCompletado = false;
    private boolean guardando      = false;

    /*Servicios*/
    private SportCentreService sportCentreService;
    private MembershipService  membershipService;

    /*Vistas — pantalla de carga*/
    private View layoutCargando;
    private View layoutContenido;

    /*Vistas — estado pago completado*/
    private LinearLayout layoutPagoCompletado;

    /*Vistas — selección de tipo*/
    private LinearLayout layoutSeleccionTipo;
    private LinearLayout cardMensual;
    private LinearLayout cardSemestral;
    private LinearLayout cardAnual;

    /*Vistas — panel de pago*/
    private CardView     cardPanelPago;
    private TextView     tvResumenPago;
    private LinearLayout layoutGuardando;
    private Button       btnPagarPaypal;
    private Button       btnCancelarPago;

    /*Vistas — header*/
    private TextView tvNombreCentro;

    /**
     * Crea una nueva instancia del fragment pasando el centroId por Bundle.
     *
     * @param centroId UID del centro deportivo para el que se contrata la membresía
     * @return Instancia configurada del fragment
     */
    public static MembershipPaymentFragment newInstance(String centroId) {
        MembershipPaymentFragment fragment = new MembershipPaymentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CENTRO_ID, centroId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Recuperamos el centroId del Bundle*/
        if (getArguments() != null) {
            centroId = getArguments().getString(ARG_CENTRO_ID);
        }

        /*UID del cliente autenticado*/
        clienteUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        /*Instanciamos los servicios*/
        sportCentreService = new SportCentreService();
        membershipService  = new MembershipService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_membership_payment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        inicializarVistas(view);
        configurarListeners();
        cargarCentro();
    }

    /**
     * Enlaza todas las vistas del layout con sus referencias Java.
     *
     * @param view Vista raíz del fragment inflada en onCreateView
     */
    private void inicializarVistas(View view) {
        layoutCargando       = view.findViewById(R.id.layoutCargandoPago);
        layoutContenido      = view.findViewById(R.id.layoutContenidoPago);
        tvNombreCentro       = view.findViewById(R.id.tvNombreCentroPago);
        layoutPagoCompletado = view.findViewById(R.id.layoutPagoCompletado);
        layoutSeleccionTipo  = view.findViewById(R.id.layoutSeleccionTipo);
        cardMensual          = view.findViewById(R.id.cardMensual);
        cardSemestral        = view.findViewById(R.id.cardSemestral);
        cardAnual            = view.findViewById(R.id.cardAnual);
        cardPanelPago        = view.findViewById(R.id.cardPanelPago);
        tvResumenPago        = view.findViewById(R.id.tvResumenPago);
        layoutGuardando      = view.findViewById(R.id.layoutGuardando);
        btnPagarPaypal       = view.findViewById(R.id.btnPagarPaypal);
        btnCancelarPago      = view.findViewById(R.id.btnCancelarPago);

        /*Arranque con pantalla de carga activa*/
        layoutCargando.setVisibility(View.VISIBLE);
        layoutContenido.setVisibility(View.GONE);
    }

    /**
     * Asigna los listeners a todos los botones e items del fragment.
     */
    private void configurarListeners() {
        requireView().findViewById(R.id.btnVolverPago)
                .setOnClickListener(v -> requireActivity()
                        .getSupportFragmentManager().popBackStack());

        requireView().findViewById(R.id.btnVolverAlCentro)
                .setOnClickListener(v -> volverAlCentro());

        /*Selección del tipo de membresía*/
        cardMensual.setOnClickListener(v   -> seleccionarTipo(TipoMembresia.MENSUAL));
        cardSemestral.setOnClickListener(v -> seleccionarTipo(TipoMembresia.SEMESTRAL));
        cardAnual.setOnClickListener(v     -> seleccionarTipo(TipoMembresia.ANUAL));

        /*Acciones del panel de pago*/
        btnPagarPaypal.setOnClickListener(v  -> abrirPayPal());
        btnCancelarPago.setOnClickListener(v -> cancelarSeleccion());
    }

    /**
     * Recupera los datos del centro deportivo desde Firebase y pinta el header.
     * Una vez finalizada oculta el spinner y revela el contenido.
     */
    private void cargarCentro() {
        if (centroId == null) return;

        sportCentreService.getSportCentreByUid(centroId, centro -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                this.centro = centro;
                if (centro != null) tvNombreCentro.setText(centro.getNombre());
                layoutCargando.setVisibility(View.GONE);
                layoutContenido.setVisibility(View.VISIBLE);
            });
        });
    }

    /**
     * Gestiona la selección del tipo de membresía.
     * Resalta visualmente la opción elegida y muestra el panel de pago
     * con el resumen del importe correspondiente al tipo seleccionado.
     *
     * @param tipo Tipo de membresía seleccionado por el cliente
     */
    private void seleccionarTipo(TipoMembresia tipo) {
        tipoSeleccionado = tipo;

        /*Restauramos el fondo de todas las opciones antes de resaltar la elegida*/
        cardMensual.setBackgroundResource(R.drawable.bg_slot_horario);
        cardSemestral.setBackgroundResource(R.drawable.bg_slot_horario);
        cardAnual.setBackgroundResource(R.drawable.bg_slot_horario);

        cambiarTextoColorEnCard(cardMensual,   false);
        cambiarTextoColorEnCard(cardSemestral, false);
        cambiarTextoColorEnCard(cardAnual,     false);

        /*Resaltamos la opción seleccionada con el color del fondo de la app*/
        switch (tipo) {
            case MENSUAL:
                cardMensual.setBackgroundColor(
                        getResources().getColor(R.color.background, null));
                cambiarTextoColorEnCard(cardMensual, true);
                tvResumenPago.setText("Plan Mensual — 29,99 €");
                break;
            case SEMESTRAL:
                cardSemestral.setBackgroundColor(
                        getResources().getColor(R.color.background, null));
                cambiarTextoColorEnCard(cardSemestral, true);
                tvResumenPago.setText("Plan Semestral — 149,99 €");
                break;
            case ANUAL:
                cardAnual.setBackgroundColor(
                        getResources().getColor(R.color.background, null));
                cambiarTextoColorEnCard(cardAnual, true);
                tvResumenPago.setText("Plan Anual — 249,99 €");
                break;
        }

        /*Mostramos el panel de pago con el resumen del tipo elegido*/
        cardPanelPago.setVisibility(View.VISIBLE);
    }

    /**
     * Cambia el color del texto de todos los TextView de una card de membresía
     * según si está seleccionada o no, para mantener la legibilidad sobre
     * el fondo de color de la app cuando la opción está activa.
     *
     * @param card         LinearLayout de la opción de membresía
     * @param seleccionada true para texto blanco (sobre fondo oscuro), false para negro
     */
    private void cambiarTextoColorEnCard(LinearLayout card, boolean seleccionada) {
        int color = seleccionada
                ? getResources().getColor(R.color.white, null)
                : getResources().getColor(R.color.black, null);

        for (int i = 0; i < card.getChildCount(); i++) {
            View hijo = card.getChildAt(i);
            if (hijo instanceof LinearLayout) {
                LinearLayout subLayout = (LinearLayout) hijo;
                for (int j = 0; j < subLayout.getChildCount(); j++) {
                    View nieto = subLayout.getChildAt(j);
                    if (nieto instanceof TextView) {
                        ((TextView) nieto).setTextColor(color);
                    }
                }
            } else if (hijo instanceof TextView) {
                ((TextView) hijo).setTextColor(color);
            }
        }
    }

    /**
     * Cancela la selección activa y oculta el panel de pago.
     * Restaura el estado visual de todas las opciones al color neutro.
     */
    private void cancelarSeleccion() {
        tipoSeleccionado = null;
        cardPanelPago.setVisibility(View.GONE);

        cardMensual.setBackgroundResource(R.drawable.bg_slot_horario);
        cardSemestral.setBackgroundResource(R.drawable.bg_slot_horario);
        cardAnual.setBackgroundResource(R.drawable.bg_slot_horario);

        cambiarTextoColorEnCard(cardMensual,   false);
        cambiarTextoColorEnCard(cardSemestral, false);
        cambiarTextoColorEnCard(cardAnual,     false);
    }

    /**
     * Punto de entrada del flujo de pago con Chrome Custom Tab.
     * Obtiene primero el token OAuth de PayPal y encadena la creación de la orden.
     * Sustituye al WebView anterior que causaba bucles de autenticación.
     */
    private void abrirPayPal() {
        if (tipoSeleccionado == null || guardando) return;
        android.util.Log.d("PAYPAL", "▶ abrirPayPal() — tipo: " + tipoSeleccionado);
        btnPagarPaypal.setEnabled(false);
        layoutGuardando.setVisibility(View.VISIBLE);

        /*Primer paso del flujo: obtenemos el token OAuth para poder llamar a la API*/
        obtenerAccessToken(token -> {
            paypalAccessToken = token;
            android.util.Log.d("PAYPAL", "✅ Token OAuth obtenido: " + token.substring(0, 20) + "...");
            crearOrden(token);
        });
    }

    /**
     * Obtiene el token de acceso OAuth 2.0 de PayPal mediante client_credentials.
     * Codifica las credenciales en Base64 siguiendo el estándar de la API de PayPal.
     * La llamada se ejecuta en un hilo secundario para no bloquear la UI.
     *
     * @param callback Recibe el access_token como String cuando la llamada tiene éxito
     */
    private void obtenerAccessToken(java.util.function.Consumer<String> callback) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api-m.sandbox.paypal.com/v1/oauth2/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                /*Credenciales en Base64: clientId:clientSecret según spec OAuth2*/
                String credentials = PAYPAL_CLIENT_ID + ":" + PAYPAL_CLIENT_SECRET;
                String encoded = android.util.Base64.encodeToString(
                        credentials.getBytes(), android.util.Base64.NO_WRAP);
                conn.setRequestProperty("Authorization", "Basic " + encoded);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.getOutputStream().write("grant_type=client_credentials".getBytes());

                InputStream is = conn.getInputStream();
                String response = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    response = new String(is.readAllBytes());
                }
                JSONObject json = new JSONObject(response);
                String accessToken = json.getString("access_token");

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> callback.accept(accessToken));

            } catch (Exception e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    layoutGuardando.setVisibility(View.GONE);
                    btnPagarPaypal.setEnabled(true);
                    showSnackbar("Error al conectar con PayPal. Inténtalo de nuevo.");
                });
            }
        }).start();
    }

    /**
     * Crea la orden en la Orders API v2 de PayPal con el importe del tipo seleccionado.
     * Extrae el approve link de los links de la respuesta y abre Chrome Custom Tab.
     * La estructura de la orden es equivalente a la que usa el componente Angular.
     *
     * @param accessToken Token OAuth obtenido previamente en obtenerAccessToken
     */
    private void crearOrden(String accessToken) {
        double importe    = getPrecio(tipoSeleccionado);
        String importeStr = String.format(java.util.Locale.US, "%.2f", importe);
        String nombreItem = "Membresía " + tipoSeleccionado.name()
                + " - " + (centro != null ? centro.getNombre() : "Centro Deportivo");

        android.util.Log.d("PAYPAL", "▶ crearOrden() — importe: " + importeStr);

        new Thread(() -> {
            try {
                URL url = new URL("https://api-m.sandbox.paypal.com/v2/checkout/orders");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Content-Type", "application/json");

                /*Cuerpo de la orden: misma estructura que usa createOrderOnClient en Angular*/
                String body = "{"
                        + "\"intent\":\"CAPTURE\","
                        + "\"purchase_units\":[{"
                        + "  \"amount\":{"
                        + "    \"currency_code\":\"EUR\","
                        + "    \"value\":\"" + importeStr + "\","
                        + "    \"breakdown\":{\"item_total\":{\"currency_code\":\"EUR\",\"value\":\"" + importeStr + "\"}}"
                        + "  },"
                        + "  \"items\":[{"
                        + "    \"name\":\"" + nombreItem.replace("\"", "\\\"") + "\","
                        + "    \"quantity\":\"1\","
                        + "    \"category\":\"DIGITAL_GOODS\","
                        + "    \"unit_amount\":{\"currency_code\":\"EUR\",\"value\":\"" + importeStr + "\"}"
                        + "  }]"
                        + "}],"
                        + "\"application_context\":{"
                        + "  \"return_url\":\"" + RETURN_URL + "\","
                        + "  \"cancel_url\":\"" + CANCEL_URL + "\","
                        + "  \"user_action\":\"PAY_NOW\","
                        + "  \"shipping_preference\":\"NO_SHIPPING\""
                        + "}"
                        + "}";

                conn.getOutputStream().write(body.getBytes());

                InputStream is = conn.getInputStream();
                String response = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    response = new String(is.readAllBytes());
                }

                android.util.Log.d("PAYPAL", "📦 Respuesta PayPal: " + response);

                JSONObject json = new JSONObject(response);

                /*Extraemos el approve link del array links de la respuesta*/
                JSONArray links = json.getJSONArray("links");
                String approveUrl = null;
                for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.getJSONObject(i);
                    if ("approve".equals(link.getString("rel"))) {
                        approveUrl = link.getString("href");
                        break;
                    }
                }

                if (approveUrl == null) throw new Exception("No se encontró approve link en la respuesta de PayPal");

                final String finalApproveUrl = approveUrl;

                android.util.Log.d("PAYPAL", "🔗 ApproveUrl: " + finalApproveUrl);

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    layoutGuardando.setVisibility(View.GONE);
                    btnPagarPaypal.setEnabled(true);
                    abrirChromeCustomTab(finalApproveUrl);
                });

            } catch (Exception e) {
                android.util.Log.e("PAYPAL", "❌ Error en crearOrden: " + e.getMessage(), e);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    layoutGuardando.setVisibility(View.GONE);
                    btnPagarPaypal.setEnabled(true);
                    showSnackbar("Error al crear la orden. Inténtalo de nuevo.");
                });
            }
        }).start();
    }

    /**
     * Abre la URL de aprobación de PayPal en Chrome Custom Tab.
     * A diferencia del WebView, Chrome Custom Tab usa un navegador real con
     * cookies propias, eliminando los bucles de autenticación de PayPal.
     * Cuando el usuario aprueba o cancela, PayPal redirige al deep link
     * declarado en el Manifest y la Activity lo captura con onNewIntent.
     *
     * @param approveUrl URL de aprobación extraída del approve link de la orden
     */
    private void abrirChromeCustomTab(String approveUrl) {
        android.util.Log.d("PAYPAL", "▶ abrirChromeCustomTab()");
        CustomTabsIntent customTab = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build();
        customTab.launchUrl(requireContext(), Uri.parse(approveUrl));
    }

    /**
     * Llamado desde la Activity cuando Android recibe el deep link de retorno.
     * Si es aprobación extrae el token y lanza la captura del pago.
     * Si es cancelación muestra el mensaje correspondiente al usuario.
     *
     * @param uri URI recibida en el deep link (sportiva://payment/success?token=XXX)
     */
    public void onDeepLinkRecibido(Uri uri) {
        android.util.Log.d("PAYPAL", "▶ onDeepLinkRecibido() — uri: " + uri);
        if (uri == null) return;

        /*El host es "payment", el path es "/success" o "/cancel"*/
        String path = uri.getPath(); // devuelve "/success" o "/cancel"
        android.util.Log.d("PAYPAL", "🔑 path: " + path + " | accessToken null: " + (paypalAccessToken == null));

        if ("/success".equals(path)) {
            String orderId = uri.getQueryParameter("token");
            if (orderId != null) {
                btnPagarPaypal.setEnabled(false);
                layoutGuardando.setVisibility(View.VISIBLE);
                capturarPago(orderId);
            }
        } else if ("/cancel".equals(path)) {
            showSnackbar("Has cancelado el proceso de pago");
        }
    }

    /**
     * Captura el pago en PayPal una vez el usuario lo ha aprobado en Chrome Custom Tab.
     * Llama al endpoint capture de la Orders API v2 con el token OAuth guardado.
     * Si la captura devuelve COMPLETED persiste la membresía en Firebase.
     *
     * @param orderId ID de la orden aprobada por el usuario en PayPal
     */
    private void capturarPago(String orderId) {
        new Thread(() -> {
            try {
                URL url = new URL(
                        "https://api-m.sandbox.paypal.com/v2/checkout/orders/"
                                + orderId + "/capture");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + paypalAccessToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Content-Length", "0");
                conn.getOutputStream().write(new byte[0]);

                InputStream is = conn.getInputStream();
                String response = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    response = new String(is.readAllBytes());
                }
                JSONObject json = new JSONObject(response);
                String status    = json.getString("status");
                String captureId = json.getString("id");

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    layoutGuardando.setVisibility(View.GONE);
                    if ("COMPLETED".equals(status)) {
                        /*Pago confirmado por PayPal: persistimos la membresía en Firebase*/
                        persistirMembresia(captureId);
                    } else {
                        btnPagarPaypal.setEnabled(true);
                        showSnackbar("El pago no se completó. Inténtalo de nuevo.");
                    }
                });

            } catch (Exception e) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    layoutGuardando.setVisibility(View.GONE);
                    btnPagarPaypal.setEnabled(true);
                    showSnackbar("Error al confirmar el pago. Contacta con soporte.");
                });
            }
        }).start();
    }

    /**
     * Persiste la membresía en Firebase tras la confirmación del pago por PayPal.
     * Calcula automáticamente la fechaFin a partir del tipo seleccionado.
     * Muestra el spinner de guardado mientras la operación está en curso.
     *
     * @param transactionId ID de la transacción devuelto por PayPal
     */
    private void persistirMembresia(String transactionId) {
        if (tipoSeleccionado == null || clienteUid == null || centroId == null) return;

        guardando = true;
        btnPagarPaypal.setEnabled(false);
        layoutGuardando.setVisibility(View.VISIBLE);

        long fechaInicio = System.currentTimeMillis();
        long fechaFin    = calcularFechaFin(fechaInicio, tipoSeleccionado);

        Membership membresia = new Membership();
        membresia.setClienteId(clienteUid);
        membresia.setCentroId(centroId);
        membresia.setTipo(tipoSeleccionado.name());
        membresia.setFechaInicio(fechaInicio);
        membresia.setFechaFin(fechaFin);
        membresia.setEstado(EstadoMembresia.ACTIVA.toString());
        membresia.setTransactionId(transactionId);
        membresia.setImporte(getPrecio(tipoSeleccionado));

        membershipService.saveMembresia(membresia, new MembershipService.WriteCallback() {
            @Override
            public void onExito() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    guardando      = false;
                    pagoCompletado = true;
                    layoutGuardando.setVisibility(View.GONE);
                    mostrarPagoCompletado();
                    showSnackbar("¡Membresía activada correctamente!");
                });
            }

            @Override
            public void onError(String mensaje) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    guardando = false;
                    btnPagarPaypal.setEnabled(true);
                    layoutGuardando.setVisibility(View.GONE);
                    showSnackbar("El pago se procesó pero hubo un error al activar la membresía. Contacta con soporte.");
                });
            }
        });
    }

    /**
     * Muestra la pantalla de confirmación de pago completado
     * y oculta el selector de tipo para que el cliente no pueda pagar dos veces.
     */
    private void mostrarPagoCompletado() {
        layoutSeleccionTipo.setVisibility(View.GONE);
        layoutPagoCompletado.setVisibility(View.VISIBLE);
    }

    /**
     * Navega de vuelta al detalle del centro usando popBackStack
     * para preservar el estado de la pila de navegación.
     */
    private void volverAlCentro() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    /**
     * Calcula la fecha de fin de la membresía en milisegundos epoch
     * a partir de la fecha de inicio y el tipo de membresía contratado.
     *
     * @param fechaInicio Timestamp epoch de inicio de la membresía
     * @param tipo        Tipo de membresía contratado
     * @return Timestamp epoch de la fecha de finalización
     */
    private long calcularFechaFin(long fechaInicio, TipoMembresia tipo) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fechaInicio);
        switch (tipo) {
            case MENSUAL:   cal.add(Calendar.MONTH, 1); break;
            case SEMESTRAL: cal.add(Calendar.MONTH, 6); break;
            case ANUAL:     cal.add(Calendar.YEAR,  1); break;
        }
        return cal.getTimeInMillis();
    }

    /**
     * Devuelve el precio en euros correspondiente al tipo de membresía.
     *
     * @param tipo Tipo de membresía a consultar
     * @return Precio en euros como double
     */
    private double getPrecio(TipoMembresia tipo) {
        switch (tipo) {
            case MENSUAL:   return PRECIO_MENSUAL;
            case SEMESTRAL: return PRECIO_SEMESTRAL;
            case ANUAL:     return PRECIO_ANUAL;
            default:        return 0.0;
        }
    }

    /**
     * Muestra un Snackbar centrado con el mensaje recibido.
     * Comprueba isAdded() antes de actuar para evitar crashes si el fragment
     * ya no está adjunto a la Activity.
     *
     * @param message Texto a mostrar en el Snackbar
     */
    private void showSnackbar(String message) {
        if (!isAdded() || getView() == null) return;
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        TextView tv = snackbar.getView()
                .findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        snackbar.show();
    }
}