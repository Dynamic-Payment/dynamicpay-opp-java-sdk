package com.dynamicpay.opp.sdk.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dynamicpay.opp.sdk.auth.Signer;
import com.dynamicpay.opp.sdk.config.OppProperties;
import com.dynamicpay.opp.sdk.model.PaymentRequest;
import com.dynamicpay.opp.sdk.model.PaymentResponse;
import com.dynamicpay.opp.sdk.model.RevokeRequest;
import com.dynamicpay.opp.sdk.model.RevokeResponse;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the OPP SDK.
 *
 * Auto-configured by Spring Boot. Inject directly into your service:
 *   {@code @Autowired private OppClient oppClient; }
 *
 * HTTP client : OkHttp 4.x — supports multi-IP fallback when DNS returns
 *               several A records (avoids the JDK HttpClient limitation of
 *               only ever attempting the first resolved address).
 * JSON library: Gson (Google, ~250KB, internationally trusted)
 */
public class OppClient {

    private static final Logger logger = LoggerFactory.getLogger(OppClient.class);

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final Gson GSON = new Gson();

    /** 响应 body 打 log 时的最大长度，避免超长 HTML 错误页刷爆日志 */
    private static final int MAX_LOGGED_BODY = 2000;

    private final OppProperties properties;
    private final Signer signer;

    /**
     * OkHttp client. Thread-safe and reusable; shares connection pool across requests.
     * Multi-IP fallback: when DNS returns multiple A records OkHttp's RouteSelector
     * tries each address until one succeeds — fixes the JDK HttpClient quirk where
     * only the first resolved IP is used.
     */
    private final OkHttpClient httpClient;

    public OppClient(OppProperties properties, Signer signer) {
        this.properties = properties;
        this.signer = signer;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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
        // 入口 log：关键业务字段（不含 sign / privateKey）。inlineKey 仅标记是否用了请求级私钥，不打内容。
        logger.info("[OPP SDK] createPaymentUrl | companyId={} merchantOrderNum={} amount={} currency={} paymentType={} applyServiceAccessType={} inlineKey={}",
                request.getCompanyId() != null ? request.getCompanyId() : properties.getCompanyId(),
                request.getMerchantOrderNum(), request.getAmount(), request.getCurrency(),
                request.getPaymentType(), request.getApplyServiceAccessType(),
                (request.getPrivateKey() != null && !request.getPrivateKey().trim().isEmpty()));

        // Build request parameters for signing
        Map<String, Object> params = new HashMap<>();
        if (request.getCompanyId() != null){
            params.put("companyId", request.getCompanyId());
        }else{
            params.put("companyId", properties.getCompanyId());
        };
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
        if (request.getSubMerAmount() != null)      params.put("subMerAmount",      request.getSubMerAmount());
        if (request.getApplyServiceAccessType() != null) params.put("applyServiceAccessType", request.getApplyServiceAccessType());
        if (request.getCompanyName() != null)            params.put("companyName",            request.getCompanyName());
        if (request.getIsAdditional3DSData() != null)    params.put("isAdditional3DSData",    request.getIsAdditional3DSData());

        // 选择签名所用的 Signer：
        //   1) request 携带了 privateKey（PEM 字符串）→ 临时用该私钥构建 Signer，仅本次调用使用；
        //   2) 否则使用注入的默认 signer（绑定 opp.private-key-path）。
        // 注意：privateKey 字段不进入 params，也就不会被签名 / 不会发送到服务端。
        Signer effectiveSigner = (request.getPrivateKey() != null && !request.getPrivateKey().trim().isEmpty())
                ? Signer.fromPemContent(request.getPrivateKey())
                : signer;
        String sign = effectiveSigner.sign(params);
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
     * Revoke an unpaid order. Idempotent: repeated calls on the same already-revoked order
     * return {@code code = 0, message = "Already revoked"}.
     *
     * Eligibility (enforced server-side): {@code status=0 (unpaid)} AND
     * {@code is_dispatched=0 (not yet dispatched to downstream payment channel)}.
     * Paid or dispatched orders cannot be revoked — use refund flow instead.
     *
     * @param request Revoke request, see {@link RevokeRequest} for field semantics.
     * @return {@link RevokeResponse} with code/message/revokeTime. Never null.
     *         The caller is expected to check {@code response.isSuccess()} or {@code response.getCode()}.
     */
    public RevokeResponse revokeOrder(RevokeRequest request) {
        if (request == null || request.getOrderNum() == null || request.getOrderNum().trim().isEmpty()) {
            logger.error("[OPP SDK] revokeOrder rejected locally: orderNum must not be blank");
            throw new IllegalArgumentException("[OPP SDK] revokeOrder: orderNum must not be blank");
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        // 入口 log：不含 sign / privateKey。
        logger.info("[OPP SDK] revokeOrder | companyId={} orderNum={} applyServiceAccessType={} inlineKey={}",
                request.getCompanyId() != null ? request.getCompanyId() : properties.getCompanyId(),
                request.getOrderNum(), request.getApplyServiceAccessType(),
                (request.getPrivateKey() != null && !request.getPrivateKey().trim().isEmpty()));

        // companyId: 用户传则用，否则 fallback 到 SDK 配置（与 createPaymentUrl 行为一致）
        String companyId = (request.getCompanyId() != null && !request.getCompanyId().trim().isEmpty())
                ? request.getCompanyId()
                : properties.getCompanyId();

        // 构造签名参数。注意：privateKey 字段绝不进 params（不进签名内容、不进 HTTP body）。
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", companyId);
        params.put("orderNum", request.getOrderNum());
        params.put("timestamp", timestamp);
        if (request.getApplyServiceAccessType() != null) {
            params.put("applyServiceAccessType", request.getApplyServiceAccessType());
        }
        // companyName：billpay 通道必填（服务端 SignDataUtils 验签时强制要求），
        // OPP 通道可空（服务端从 DB 取 orgName）。
        if (request.getCompanyName() != null) {
            params.put("companyName", request.getCompanyName());
        }
        if (request.getRevokeReason() != null) {
            params.put("revokeReason", request.getRevokeReason());
        }

        // 选 Signer：request 带 privateKey → 临时 Signer；否则用注入的默认 signer
        Signer effectiveSigner = (request.getPrivateKey() != null && !request.getPrivateKey().trim().isEmpty())
                ? Signer.fromPemContent(request.getPrivateKey())
                : signer;
        String sign = effectiveSigner.sign(params);
        params.put("sign", sign);

        String url = properties.resolveServerUrl() + "/api/payment/order/" + request.getOrderNum() + "/revoke";
        JsonObject responseJson = sendJson("PATCH", url, params);

        int code = responseJson.has("code") ? responseJson.get("code").getAsInt() : -1;
        String message = responseJson.has("message") && !responseJson.get("message").isJsonNull()
                ? responseJson.get("message").getAsString()
                : null;
        String revokeTime = responseJson.has("revokeTime") && !responseJson.get("revokeTime").isJsonNull()
                ? responseJson.get("revokeTime").getAsString()
                : null;
        return new RevokeResponse(code, message, revokeTime);
    }

    /**
     * Send a POST request with a JSON body.
     */
    private JsonObject post(String url, Map<String, Object> params) {
        return sendJson("POST", url, params);
    }

    /**
     * Generic JSON HTTP send (POST / PATCH / etc).
     * Strips null and empty-string values so the server never receives phantom fields.
     */
    private JsonObject sendJson(String method, String url, Map<String, Object> params) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() != null && !e.getValue().toString().isEmpty()) {
                clean.put(e.getKey(), e.getValue());
            }
        }
        String bodyJson = GSON.toJson(clean);

        // DEBUG：打脱敏后的请求体（sign 是签名结果非密钥，保留；params 里本就不含 privateKey）。
        if (logger.isDebugEnabled()) {
            logger.debug("[OPP SDK] -> {} {} body={}", method, url, bodyJson);
        }

        RequestBody body = RequestBody.create(bodyJson, MediaType.parse(CONTENT_TYPE));
        Request httpRequest = new Request.Builder()
                .url(url)
                .method(method, body)
                .build();

        long start = System.currentTimeMillis();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            long cost = System.currentTimeMillis() - start;
            int status = response.code();
            ResponseBody rb = response.body();
            String respBody = rb != null ? rb.string() : "";
            if (status != 200) {
                // 关键：非 200 时把 response body 也打出来 —— 据此区分 403 是 nginx / 网关 / WAF / OPP 哪一层返回。
                //   {"code":1030,...}        → OPP 应用
                //   <html>403 Forbidden</html> → nginx
                //   AWS/网关 JSON            → API 网关 / ALB
                logger.error("[OPP SDK] <- {} {} status={} cost={}ms responseBody={}",
                        method, url, status, cost, truncate(respBody));
                throw new RuntimeException("[OPP SDK] HTTP error: " + status
                        + ", body: " + truncate(respBody));
            }
            logger.info("[OPP SDK] <- {} {} status={} cost={}ms", method, url, status, cost);
            if (logger.isDebugEnabled()) {
                logger.debug("[OPP SDK] <- {} {} responseBody={}", method, url, truncate(respBody));
            }
            return JsonParser.parseString(respBody).getAsJsonObject();
        } catch (IOException e) {
            // 网络层错误（连接拒绝 / 超时 / DNS / TLS）—— 区别于上面的 HTTP 非 200。
            // OkHttp 把所有连接 / 读写超时 / DNS 失败统一抛 IOException。
            long cost = System.currentTimeMillis() - start;
            logger.error("[OPP SDK] network error | {} {} cost={}ms err={}: {}",
                    method, url, cost, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("[OPP SDK] Network error: " + e.getMessage(), e);
        }
    }

    /** 截断超长 body，避免错误 HTML 页 / 大响应刷爆日志。 */
    private static String truncate(String s) {
        if (s == null) return "(null)";
        return s.length() <= MAX_LOGGED_BODY ? s : s.substring(0, MAX_LOGGED_BODY) + "...(truncated, total " + s.length() + ")";
    }
}
