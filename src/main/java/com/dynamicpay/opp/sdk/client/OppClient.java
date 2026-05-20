package com.dynamicpay.opp.sdk.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dynamicpay.opp.sdk.auth.Signer;
import com.dynamicpay.opp.sdk.config.OppProperties;
import com.dynamicpay.opp.sdk.model.PaymentRequest;
import com.dynamicpay.opp.sdk.model.PaymentResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for the OPP SDK.
 *
 * Auto-configured by Spring Boot. Inject directly into your service:
 *   {@code @Autowired private OppClient oppClient; }
 *
 * HTTP client : JDK 11 built-in java.net.http.HttpClient (no third-party dependency)
 * JSON library: Gson (Google, ~250KB, internationally trusted)
 */
public class OppClient {

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final Gson GSON = new Gson();

    private final OppProperties properties;
    private final Signer signer;

    /**
     * JDK 11 built-in HttpClient. Thread-safe and reusable.
     * Connection timeout: 10 seconds.
     */
    private final HttpClient httpClient;

    public OppClient(OppProperties properties, Signer signer) {
        this.properties = properties;
        this.signer = signer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Create a payment URL.
     *
     * @param request Payment request. Amount must be in the smallest currency unit (cents).
     * @return PaymentResponse containing the complete payUrl. Redirect the user to this URL.
     */
    public PaymentResponse createPaymentUrl(PaymentRequest request) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // Build request parameters for signing
        Map<String, Object> params = new HashMap<>();
        params.put("companyId",        properties.getCompanyId());
        params.put("merchantOrderNum", request.getMerchantOrderNum());
        params.put("amount",           request.getAmount());
        params.put("currency",         request.getCurrency());
        params.put("timestamp",        timestamp);

        // Optional fields — only include if present to keep the signed content clean
        if (request.getPaymentType() != null)       params.put("paymentType",       request.getPaymentType());
        if (request.getDescription() != null)       params.put("description",       request.getDescription());
        if (request.getNotifyUrl() != null)         params.put("notifyUrl",         request.getNotifyUrl());
        if (request.getExtraTradeCode() != null)    params.put("extraTradeCode",    request.getExtraTradeCode());
        if (request.getExtraTradeContent() != null) params.put("extraTradeContent", request.getExtraTradeContent());
        if (request.getMerchantCode() != null)      params.put("merchantCode",      request.getMerchantCode());
        if (request.getAttach() != null)            params.put("attach",            request.getAttach());
        if (request.getEmail() != null)             params.put("email",             request.getEmail());
        if (request.getMobile() != null)            params.put("mobile",            request.getMobile());
        if (request.getFirstName() != null)         params.put("firstName",         request.getFirstName());
        if (request.getLastName() != null)          params.put("lastName",          request.getLastName());
        if (request.getMobileCountryCode() != null) params.put("mobileCountryCode", request.getMobileCountryCode());
        if (request.getApplyServiceAccessType() != null) params.put("applyServiceAccessType", request.getApplyServiceAccessType());
        if (request.getCompanyName() != null)            params.put("companyName",            request.getCompanyName());
        if (request.getIsAdditional3DSData() != null)    params.put("isAdditional3DSData",    request.getIsAdditional3DSData());

        // Sign and attach to params
        String sign = signer.sign(params);
        params.put("sign", sign);

        // Call OPP service to obtain orderNum + accessKey
        String url = properties.resolveServerUrl() + "/api/auth/accesskey";
        JsonObject responseJson = post(url, params);

        // Check error code — non-zero means failure
        int code = responseJson.has("code") ? responseJson.get("code").getAsInt() : -1;
        if (code != 0) {
            String message = responseJson.has("message") ? responseJson.get("message").getAsString() : "unknown error";
            throw new RuntimeException("[OPP SDK] createPaymentUrl failed: " + message);
        }

        // Response structure: { "code":0, "data": { "orderNum":"...", "accessKey":"..." } }
        if (!responseJson.has("data") || responseJson.get("data").isJsonNull()) {
            throw new RuntimeException("[OPP SDK] Invalid response, missing data field: " + responseJson);
        }
        JsonObject data = responseJson.getAsJsonObject("data");

        if (!data.has("orderNum") || !data.has("accessKey")) {
            throw new RuntimeException("[OPP SDK] Invalid response, missing orderNum or accessKey: " + responseJson);
        }

        String orderNum  = data.get("orderNum").getAsString();
        String accessKey = data.get("accessKey").getAsString();

        // Build the complete payment page URL including all redirect parameters
        StringBuilder payUrl = new StringBuilder();
        payUrl.append(properties.resolvePageUrl())
              .append("/payment?orderNum=").append(orderNum)
              .append("&accessKey=").append(accessKey)
              .append("&timestamp=").append(timestamp);
        if (request.getRedirectCallerUrl() != null)
            payUrl.append("&redirectCallerUrl=").append(request.getRedirectCallerUrl());
        if (request.getRedirectSuccessUrl() != null)
            payUrl.append("&redirectSuccessUrl=").append(request.getRedirectSuccessUrl());
        if (request.getRedirectErrorUrl() != null)
            payUrl.append("&redirectErrorUrl=").append(request.getRedirectErrorUrl());

        return new PaymentResponse(orderNum, accessKey, payUrl.toString());
    }

    /**
     * Send a POST request with a JSON body.
     * Uses JDK 11 built-in HttpClient. Read timeout: 30 seconds.
     */
    private JsonObject post(String url, Map<String, Object> params) {
        // Remove null and empty-string values so the server never receives phantom fields
        Map<String, Object> clean = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() != null && !e.getValue().toString().isEmpty()) {
                clean.put(e.getKey(), e.getValue());
            }
        }
        String bodyJson = GSON.toJson(clean);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("[OPP SDK] HTTP error: " + response.statusCode());
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[OPP SDK] Request interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("[OPP SDK] Network error: " + e.getMessage(), e);
        }
    }
}
