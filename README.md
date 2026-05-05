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
        <groupId>com.github.dynamicpay</groupId>
        <artifactId>dynamicpay-opp-java-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### Option 2: Clone and Install Locally

```bash
git clone https://github.com/dynamicpay/dynamicpay-opp-java-sdk.git
cd dynamicpay-opp-java-sdk

# Compile and install to local Maven repository
mvn clean install -DskipTests
```

Then add to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.dynamicpay.opp</groupId>
    <artifactId>dynamicpay-opp-java-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Configuration

### Step 1: Download Your Private Key

Log in to the DynamicPay merchant portal, go to **Organization > Config**, and download your private key file (`private_key_pkcs8.pem`). Store it in a secure location on your server, e.g.:

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
        // Supported values: alipay / wechat / unionpay / vmpay / clicktopay
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
        // https://sandbox.api.dynamicpay.com/payment
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
        String status   = (String) body.get("status");   // SUCCESS or FAILED
        Object amount   = body.get("amount");
        String sign     = (String) body.get("sign");

        // 1. Verify signature using the platform public key
        // 2. Validate orderNum exists and amount matches
        // 3. Handle idempotency (the same order may be notified multiple times)
        // 4. Update order status in your database

        if ("SUCCESS".equals(status)) {
            // Mark order as paid
        }

        // Return "success" to acknowledge receipt.
        // If not returned, the platform will retry at intervals.
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

## PaymentRequest Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `merchantOrderNum` | String | Yes | Your unique order ID, max 32 chars |
| `amount` | long | Yes | Amount in smallest currency unit (cents) |
| `currency` | String | Yes | ISO 4217 currency code, e.g. `USD` |
| `paymentType` | String | No | `alipay` / `wechat` / `unionpay` / `vmpay` / `clicktopay`. Omit to show all options |
| `description` | String | No | Order description |
| `notifyUrl` | String | No | Server-to-server async notification URL |
| `redirectSuccessUrl` | String | No | Redirect URL after successful payment |
| `redirectErrorUrl` | String | No | Redirect URL after failed payment |
| `redirectCallerUrl` | String | No | Redirect URL when user clicks Back / Cancel |
| `extraTradeCode` | String | No | Comma-separated business codes, e.g. `installment`, max 256 chars |
| `extraTradeContent` | String | No | JSON map matching `extraTradeCode` keys, max 1024 chars |
| `attach` | String | No | Custom pass-through data, returned as-is in notification callback |

---

## Environments

| Environment | Server URL |
|---|---|
| Sandbox | `https://sandbox.api.dynamicpay.com` |
| Production | `https://api.dynamicpay.com` |

Switch via `opp.environment: sandbox` or `opp.environment: prod` in `application.yml`.

---

## License

MIT
