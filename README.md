# DynamicPay OPP Java SDK

Java SDK for DynamicPay Online Payment Page (OPP). Supports Spring Boot auto-configuration.

**Requirements:** JDK 11+, Spring Boot 2.x / 3.x

---

## Installation

### Option 1: Maven via JitPack

Add the JitPack repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.Dynamic-Payment</groupId>
        <artifactId>dynamicpay-opp-java-sdk</artifactId>
        <version>1.0.2</version>
    </dependency>
</dependencies>
```

### Option 2: Clone and Install Locally

```bash
git clone https://github.com/Dynamic-Payment/dynamicpay-opp-java-sdk.git
cd dynamicpay-opp-java-sdk

# Compile and install to local Maven repository
mvn clean install -DskipTests
```

Then add to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.dynamicpay.opp</groupId>
    <artifactId>dynamicpay-opp-java-sdk</artifactId>
    <version>1.0.2</version>
</dependency>
```

---

## Configuration

### Step 1: Download Your Private Key

Log in to the DynamicPay merchant portal, go to **Account Settings → API Keys**, and download your private key file (`private_key_pkcs8.pem`). Store it in a secure location on your server, e.g.:

```
/etc/opp/private_key_pkcs8.pem
```

### Step 2: Add Configuration to `application.yml`

```yaml
opp:
  company-id: YOUR_COMPANY_ID          # Your company/merchant ID
  private-key-path: /etc/opp/private_key_pkcs8.pem  # Absolute path to private key file
  environment: sandbox                  # sandbox or prod
```

The SDK auto-configures itself via Spring Boot — no extra `@Bean` setup needed.

> **⚠ Breaking change in 1.0.2**: When `opp.environment` is omitted, the SDK now defaults to `prod` (previously `sandbox`). If your application relied on the previous `sandbox` default, you must set `opp.environment: sandbox` explicitly to keep using sandbox.

---

## Quick Start

Inject `OppClient` directly into your service:

```java
import com.dynamicpay.opp.sdk.client.OppClient;

@Service
public class OrderService {

    private final OppClient oppClient;

    public OrderService(OppClient oppClient) {
        this.oppClient = oppClient;
    }
}
```

---

## Usage Examples

### Example 1: Basic Payment (Platform Selects Payment Method)

The payment page displays all available payment methods for the user to choose.

```java
public String createOrder(String orderId, long amountCents) {
    var request = new PaymentRequest();
    request.setMerchantOrderNum(orderId);       // Unique order ID, max 32 chars
    request.setAmount(amountCents);              // Amount in smallest unit (cents), e.g. 5000 = $50.00
    request.setCurrency("USD");
    request.setDescription("Order " + orderId);
    request.setNotifyUrl("https://your-domain.com/payment/notify");

    request.setRedirectSuccessUrl("https://your-domain.com/order/" + orderId + "/success");
    request.setRedirectErrorUrl("https://your-domain.com/order/" + orderId + "/failed");
    request.setRedirectCallerUrl("https://your-domain.com/cart");   // Back button destination

    var response = oppClient.createPaymentUrl(request);
    return response.getPayUrl();   // Redirect the user to this URL
}
```

---

### Example 2: Alipay (Full Flow)

Specify `paymentType` to skip the payment method selection screen and go directly to Alipay.

**Service layer:**

```java
import com.dynamicpay.opp.sdk.client.OppClient;
import com.dynamicpay.opp.sdk.model.PaymentRequest;
import com.dynamicpay.opp.sdk.model.PaymentResponse;

@Service
public class OrderService {

    private final OppClient oppClient;

    public OrderService(OppClient oppClient) {
        this.oppClient = oppClient;
    }

    public String createAlipayOrder(String orderId, long amountCents) {
        var request = new PaymentRequest();

        // Required fields
        request.setMerchantOrderNum(orderId);
        request.setAmount(amountCents);          // e.g. 12800 = $128.00
        request.setCurrency("USD");

        // Specify Alipay directly
        // Supported values: alipay / wechat / unionpay / vmpay
        request.setPaymentType("alipay");

        request.setDescription("Order " + orderId);

        // Async server-to-server notification (does not depend on browser redirect)
        request.setNotifyUrl("https://your-domain.com/payment/notify");

        // Page redirect URLs after payment completes
        request.setRedirectSuccessUrl("https://your-domain.com/order/" + orderId + "/success");
        request.setRedirectErrorUrl("https://your-domain.com/order/" + orderId + "/failed");
        request.setRedirectCallerUrl("https://your-domain.com/cart");

        PaymentResponse response = oppClient.createPaymentUrl(request);

        // Generated payUrl example:
        // https://uat-opp.dynamicg.com/payment
        //   ?orderNum=DP2024001
        //   &accessKey=eyJ...
        //   &redirectCallerUrl=https://your-domain.com/cart
        //   &redirectSuccessUrl=https://your-domain.com/order/ORD001/success
        //   &redirectErrorUrl=https://your-domain.com/order/ORD001/failed
        return response.getPayUrl();
    }
}
```

**Controller layer:**

```java
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/pay")
    public ResponseEntity<Map<String, String>> pay(@RequestBody PayOrderRequest req) {
        String payUrl = orderService.createAlipayOrder(req.getOrderId(), req.getAmount());
        // Return payUrl to the frontend, then do: window.location.href = payUrl
        return ResponseEntity.ok(Map.of("payUrl", payUrl));
    }
}
```

**Async notification receiver (`notifyUrl` endpoint):**

```java
@RestController
@RequestMapping("/payment")
public class NotifyController {

    @PostMapping("/notify")
    public ResponseEntity<String> handleNotify(@RequestBody Map<String, Object> body) {
        String orderNum = (String) body.get("orderNum");

        // The webhook is NOT signed — do not trust the body directly.
        // Always call queryBySign with orderNum to confirm status before
        // marking the order paid. The webhook fires at most once; there is no retry.
        return ResponseEntity.ok("success");
    }
}
```

**Alipay payment flow:**

```
Your Backend                    DynamicPay Platform             User Browser
     │                                  │                             │
     │── createPaymentUrl(request) ──→  │                             │
     │←── payUrl ──────────────────────│                             │
     │                                  │                             │
     │── return payUrl to frontend ─────────────────────────────────→│
     │                                  │  User selects Alipay        │
     │                                  │←── submit payment ──────────│
     │                                  │──→ return Alipay HTML Form ─│
     │                                  │                    │ redirect to Alipay
     │                                  │                    │ payment complete
     │←── POST /payment/notify ─────── │                             │
     │    (async, server-to-server)     │                             │
     │                                  │──→ redirect to successUrl ─→│
```

---

### Example 3: Advanced — Installment with Attach

```java
public String createInstallmentOrder(String orderId, long amountCents, String userId) {
    var request = new PaymentRequest();
    request.setMerchantOrderNum(orderId);
    request.setAmount(amountCents);              // e.g. 360000 = $3600.00
    request.setCurrency("USD");
    request.setPaymentType("unionpay");
    request.setDescription("MacBook Pro 14 inch");
    request.setNotifyUrl("https://your-domain.com/payment/notify");

    request.setRedirectSuccessUrl("https://your-domain.com/order/" + orderId + "/success");
    request.setRedirectErrorUrl("https://your-domain.com/order/" + orderId + "/failed");
    request.setRedirectCallerUrl("https://your-domain.com/cart");

    // Business extension code, comma-separated, max 256 chars
    request.setExtraTradeCode("installment");

    // Business data corresponding to each extraTradeCode, JSON format, max 1024 chars
    request.setExtraTradeContent("{\"installment\":{\"periods\":12}}");

    // Pass-through field: returned as-is in the payment notification callback
    request.setAttach("{\"userId\":\"" + userId + "\",\"channel\":\"app\"}");

    var response = oppClient.createPaymentUrl(request);
    return response.getPayUrl();
}
```

---

### Example 4: Multi-Merchant Whitelist (Buyer Picks From a Subset)

Pass a comma-separated list of `merCode`s to restrict the payment page to a specific subset.
The buyer chooses one merchant from this whitelist. All listed codes must be valid for the
company and currency — any invalid code returns `code: 1058`.

```java
public String createMultiMerchantOrder(String orderId, long amountCents) {
    var request = new PaymentRequest();
    request.setMerchantOrderNum(orderId);
    request.setAmount(amountCents);
    request.setCurrency("HKD");
    request.setMerchantCode("960105331000001,960105331000002");   // ← comma-separated whitelist
    return oppClient.createPaymentUrl(request).getPayUrl();
}
```

---

## Revoking an Order

Merchant-initiated cancellation of an **unpaid, not-yet-dispatched** order:

```java
import com.dynamicpay.opp.sdk.client.OppClient;
import com.dynamicpay.opp.sdk.model.RevokeRequest;
import com.dynamicpay.opp.sdk.model.RevokeResponse;

@Service
public class OrderCancelService {

    @Autowired
    private OppClient oppClient;

    public void cancelOrder(String orderNum) {
        RevokeRequest request = new RevokeRequest();
        request.setOrderNum(orderNum);
        request.setRevokeReason("Customer requested cancellation");
        // companyId / applyServiceAccessType optional — defaults to SDK config

        RevokeResponse response = oppClient.revokeOrder(request);

        if (response.isSuccess()) {
            System.out.println("Revoked at: " + response.getRevokeTime());
        } else {
            System.err.println("Revoke failed: code=" + response.getCode()
                    + " message=" + response.getMessage());
        }
    }
}
```

### Eligibility

The server **only** revokes orders satisfying **both** conditions:

| Field | Required value | Meaning |
|---|---|---|
| `status` | `0` | Order is unpaid |
| `is_dispatched` | `0` | Order has not been forwarded to a downstream payment channel |

Paid orders or orders already dispatched cannot be revoked — use the refund flow instead.

### Idempotency

Calling `revokeOrder` twice on the same already-revoked order returns:

```json
{ "code": 0, "message": "Already revoked" }
```

Safe to retry on network errors.

### RevokeRequest Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `orderNum` | String | Yes | Platform-issued order number to revoke. Echoed in the URL path and the body — server cross-checks. |
| `companyId` | String | No | Defaults to `opp.company-id` from SDK config. Override per-call when the SDK serves multiple merchants. |
| `applyServiceAccessType` | String | No | `opp` (default) or `billpay`. Determines server-side verification key source. |
| `companyName` | String | Conditional | Required when `applyServiceAccessType` is `billpay`. Used by the OPP server to locate the billpay signing key. Ignored for the default `opp` channel. |
| `revokeReason` | String | No | Free-form audit note, max 256 chars. Persisted in `opp_order.revoke_reason`. |
| `privateKey` | String | No | Inline PEM private key for this call only. Same semantics as `PaymentRequest.privateKey` — useful for multi-merchant signing. Never sent in the HTTP body or signed content. |

### RevokeResponse Fields

| Field | Type | Description |
|---|---|---|
| `code` | int | `0` on success or idempotent already-revoked; see error code table below |
| `message` | String | Human-readable result |
| `revokeTime` | String | Server-side revoke timestamp (ISO-8601 LocalDateTime, UTC). Populated when an actual state change occurred. |

### Error Codes

| Code | Meaning |
|---|---|
| `0` | Success or already revoked (idempotent) |
| `1020` | Timestamp outside allowed window |
| `1022` | `companyId` missing |
| `1023` | Signature verification failed |
| `1024` | Merchant not authorized |
| `1054` | `orderNum` mismatch between URL path and body (SDK guards against this internally) |
| `1055` | Order not found |
| `1056` | Order does not belong to this company |
| `1057` | Order not in a revocable state (already paid or already dispatched) |

### Side Effects

A successful revoke also triggers (server-side):

1. **Redis access key cleanup** — invalidates any in-flight payment page session for this order.
2. **JWT blacklist entry** — any already-issued JWT for this order is rejected by the OPP interceptor for the remainder of its 5-minute lifetime.

So even if a buyer holds an active JWT, **they cannot complete payment after revoke**.

---

## PaymentRequest Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `merchantOrderNum` | String | Yes | Your unique order ID, max 32 chars |
| `amount` | long | Yes | Amount in smallest currency unit (cents) |
| `currency` | String | Yes | ISO 4217 currency code, e.g. `USD` |
| `paymentType` | String | No | `alipay` / `wechat` / `unionpay` / `vmpay`. Omit to show all options. Mastercard Click to Pay is triggered automatically by merchant configuration — no value needed. |
| `merchantCode` | String | No | Acquirer merchant code. Single value (`"960105331000001"`) → single-merchant payment page. Comma-separated multi-value (`"960105331000001,960105331000002"`) → multi-merchant payment page restricted to this whitelist; buyer picks one. **All listed codes must be valid** (any invalid code → `code: 1058`). Whitespace around commas tolerated, duplicates de-duplicated. Max 256 chars. Required when `extraTradeCode` is `delegated`. |
| `description` | String | No | Order description |
| `notifyUrl` | String | No | Server-to-server async notification URL |
| `redirectSuccessUrl` | String | No | Browser redirect URL after successful payment (appended to `payUrl` as query parameter) |
| `redirectErrorUrl` | String | No | Browser redirect URL after failed payment (appended to `payUrl` as query parameter) |
| `redirectCallerUrl` | String | No | Browser redirect URL when user clicks Back / Cancel (appended to `payUrl` as query parameter) |
| `extraTradeCode` | String | No | Comma-separated business codes, e.g. `installment`, max 256 chars |
| `extraTradeContent` | String | No | JSON map matching `extraTradeCode` keys, max 1024 chars |
| `attach` | String | No | Custom pass-through data, returned as-is in notification callback |
| `email` | String | No | Cardholder email. Click to Pay only. When provided with `mobile`, skips the identity page. |
| `mobile` | String | No | Cardholder mobile number in E.164 format (e.g. `+85212345678`). Click to Pay only. |
| `mobileCountryCode` | String | No | Mobile country dialing code, e.g. `852` (HK), `61` (AU), `1` (US/CA). Click to Pay only. |
| `subMerAmount` | String | No | Sub-merchant amount, pass-through forwarded to UnionPay as `sub_mer_amount`. Max 1024 chars. Only takes effect when the acquirer merchant is configured with `subMerchantSupport=Y`; otherwise ignored. |
| `firstName` | String | No | Cardholder first name. Optional, used to pre-fill name in Click to Pay. Max 100 chars. |
| `lastName` | String | No | Cardholder last name. Optional, used to pre-fill name in Click to Pay. Max 100 chars. |
| `isAdditional3DSData` | Integer | No | Enable additional 3DS data on this transaction. `1` = enable, `0` / omit = standard. |
| `applyServiceAccessType` | String | No | `opp` (default) or `billpay`. Determines server-side verification key source. |
| `companyName` | String | Conditional | Required when `applyServiceAccessType` is `billpay`. Used by the OPP server to locate the billpay signing key. Ignored for the default `opp` channel. |
| `privateKey` | String | No | Inline PEM private key for this call only. Overrides the SDK's configured `opp.private-key-path` — useful when one SDK instance serves multiple merchants with different keys. Never sent in the HTTP body or signed content. |

---

## Environments

| Environment | API host | Hosted page host |
|---|---|---|
| Sandbox | `https://uat-opp-api.dynamicg.com` | `https://uat-opp.dynamicg.com` |
| Production | `https://opp-api.dynamicg.com` | `https://opp.dynamicg.com` |

Switch via `opp.environment: sandbox` or `opp.environment: prod` in `application.yml`. **When `opp.environment` is omitted, the SDK defaults to `prod`** (changed in 1.0.2) — always set this property explicitly to avoid accidentally hitting production. The URLs above are built-in defaults — **no URL configuration is required** for standard deployments.

For special cases (e.g. pointing at a local or staging server), you can override the defaults. **Do not set these properties unless explicitly instructed by DynamicPay technical staff.**

```yaml
opp:
  environment: sandbox
  sandbox-url: https://staging-api.example.com         # overrides sandbox API host
  sandbox-page-url: https://staging-page.example.com   # overrides sandbox page host
  prod-url: https://my-custom-prod.example.com
  prod-page-url: https://my-custom-opp-page.example.com
```

---

## License

MIT
