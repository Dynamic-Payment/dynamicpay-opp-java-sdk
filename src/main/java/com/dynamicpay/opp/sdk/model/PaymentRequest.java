package com.dynamicpay.opp.sdk.model;

import java.util.List;

/**
 * Payment link creation request parameters.
 */
public class PaymentRequest {

    /** Merchant order number, must be globally unique, max 32 characters. Required. */
    private String merchantOrderNum;

    /**
     * Payment amount in the smallest currency unit (cents).
     * e.g. 100 = 1.00 USD. Required.
     */
    private long amount;

    /** Currency code, ISO 4217. e.g. USD, CNY, SGD. Required. */
    private String currency;

    /**
     * Payment method. Optional. Allowed values (mapped to server-side {@code PaymentTypeEnum}):
     * {@code wechat} / {@code alipay} / {@code unionpay} / {@code vmpay}.
     *
     * If omitted, the payment page shows all available methods for the buyer to choose.
     *
     * Note: Mastercard Click to Pay is NOT a {@code paymentType} value — it is triggered
     * automatically when the selected merchant is configured with {@code isClickToPay=true}
     * on the server. Pass no {@code paymentType} (or one of the four above) for Click to Pay
     * to surface as a payment option on the page.
     */
    private String paymentType;

    /** Order description shown on the payment page. Optional. */
    private String description;

    /** Server-to-server async payment notification URL. Optional. */
    private String notifyUrl;

    /** Business extension codes, comma-separated. e.g. "installment,discount". Max 256 chars. Optional. */
    private String extraTradeCode;

    /** Business data corresponding to each extraTradeCode key, in JSON format. Max 1024 chars. Optional.
     *  e.g. {"installment":{"periods":12}} */
    private String extraTradeContent;

    /**
     * Acquirer merchant code. Optional in general; required when {@code extraTradeCode} is {@code delegated}.
     *
     * Accepts two forms:
     *   - Single value (e.g. {@code "960105331000001"}): single-merchant payment page,
     *     server resolves payMode / paymentType from this exact merchant.
     *   - Comma-separated multi-value (e.g. {@code "960105331000001,960105331000002"}):
     *     multi-merchant payment page restricted to this whitelist — buyer picks one
     *     at the payment page. **All listed codes must be valid for the company and
     *     currency** (server rejects with {@code code: 1058 "Invalid merchantCode: <code>"}
     *     if any code is invalid).
     *
     * Whitespace around commas is tolerated; duplicate codes are de-duplicated server-side
     * preserving first-seen order. Max 256 characters total.
     */
    private String merchantCode;

    /** Pass-through field returned as-is in the payment notification callback. Optional. */
    private String attach;

    /**
     * Cardholder email address. Optional, for Click to Pay only. Max 254 characters.
     * When provided together with {@code mobile}, the identity page is skipped and the user
     * proceeds directly to the Click to Pay card selection screen.
     */
    private String email;

    /**
     * Cardholder mobile number. Optional, for Click to Pay only. Max 20 characters.
     * Recommended format: E.164 (e.g. +85212345678).
     * When provided together with {@code email}, the identity page is skipped.
     */
    private String mobile;

    /** Cardholder first name. Optional. Used to pre-fill cardholder name in Click to Pay. Max 100 characters. */
    private String firstName;

    /** Cardholder last name. Optional. Used to pre-fill cardholder name in Click to Pay. Max 100 characters. */
    private String lastName;

    /**
     * Mobile phone country dialing code. Optional. Max 8 characters.
     * e.g. "852" (Hong Kong), "61" (Australia), "1" (US/Canada).
     * Used as mobileNumber.countryCode in Click to Pay SDK calls.
     */
    private String mobileCountryCode;

    /**
     * Sub-merchant amount split, pass-through field forwarded to UnionPay as {@code sub_mer_amount}.
     * Optional. Max 20 entries (enforced server-side). Only takes effect when the acquirer merchant
     * is configured with {@code subMerchantSupport=Y} on the server; otherwise ignored.
     *
     * NOTE — signature compatibility: do not replace {@link SubMerItemDTO} with a different class,
     * rename its fields, or reorder them without re-verifying against the server's Lombok-generated
     * {@code toString()} output. See {@link SubMerItemDTO} javadoc for why.
     */
    private List<SubMerItemDTO> subMerAmount;

    /**
     * Service access type. Optional.
     * Allowed values: "opp" (default behavior) / "billpay".
     * - "opp"     — default OPP channel; server performs permissionOppCode check
     *               and uses serviceAccess bitmask for merchant permission.
     * - "billpay" — BillPay channel; server skips permissionOppCode check
     *               and uses billpayServiceAccess bitmask for merchant permission.
     *
     * Normalization timing (important for signature compatibility):
     *   - Server does NOT normalize this field before signature verification — null / empty
     *     stays null / empty in the signed content. This preserves backward compatibility
     *     for legacy callers that don't know about this field.
     *   - Server normalizes null / empty → "opp" only AFTER signature verification passes.
     *   - SDK callers: leave null to use default OPP behavior; pass exactly "opp" or "billpay"
     *     explicitly when needed.
     */
    private String applyServiceAccessType;

    /**
     * Company / organization display name.
     * REQUIRED when applyServiceAccessType is "billpay" — used as orgName for the order
     * (server returns error 1037 "companyName is required" if missing in billpay mode).
     * Ignored for the default OPP channel, where orgName is taken from the server-side
     * organization record via companyId lookup.
     */
    private String companyName;

    private String companyId; // Optional field for backward compatibility. if not present, companyId is always taken from SDK config.

    /**
     * Optional. Inline RSA private key content (PEM string) used **only for signing this request**.
     * When set and non-blank, the SDK ignores its configured default Signer for this call and signs
     * with this key instead — useful when one application needs to sign for multiple companyIds
     * (each with its own key) without rebuilding the SDK client.
     *
     * SECURITY:
     *   - This field is NEVER added to the signed content or to the outbound HTTP body.
     *   - Caller is responsible for keeping the key material secure in memory.
     *   - Recommend wiping the reference after the call.
     *
     * Accepts both standard PEM (with -----BEGIN/END----- headers) and raw Base64 PKCS8 content.
     */
    private String privateKey;

    /**
     * Whether to include additional 3DS data on this transaction.
     * 1 = include additional 3DS data, 0 / null = standard (default). Optional.
     */
    private Integer isAdditional3DSData;

    /** URL to redirect when the user clicks Back or Cancel on the payment page. Optional. */
    private String redirectCallerUrl;

    /** URL to redirect after successful payment. Optional. */
    private String redirectSuccessUrl;

    /** URL to redirect after failed payment. Optional. */
    private String redirectErrorUrl;

    public String getMerchantOrderNum() { return merchantOrderNum; }
    public void setMerchantOrderNum(String merchantOrderNum) { this.merchantOrderNum = merchantOrderNum; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

    public String getExtraTradeCode() { return extraTradeCode; }
    public void setExtraTradeCode(String extraTradeCode) { this.extraTradeCode = extraTradeCode; }

    public String getExtraTradeContent() { return extraTradeContent; }
    public void setExtraTradeContent(String extraTradeContent) { this.extraTradeContent = extraTradeContent; }

    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }

    public String getAttach() { return attach; }
    public void setAttach(String attach) { this.attach = attach; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getMobileCountryCode() { return mobileCountryCode; }
    public void setMobileCountryCode(String mobileCountryCode) { this.mobileCountryCode = mobileCountryCode; }

    public List<SubMerItemDTO> getSubMerAmount() { return subMerAmount; }
    public void setSubMerAmount(List<SubMerItemDTO> subMerAmount) { this.subMerAmount = subMerAmount; }

    public String getApplyServiceAccessType() { return applyServiceAccessType; }
    public void setApplyServiceAccessType(String applyServiceAccessType) { this.applyServiceAccessType = applyServiceAccessType; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public Integer getIsAdditional3DSData() { return isAdditional3DSData; }
    public void setIsAdditional3DSData(Integer isAdditional3DSData) { this.isAdditional3DSData = isAdditional3DSData; }

    public String getRedirectCallerUrl() { return redirectCallerUrl; }
    public void setRedirectCallerUrl(String redirectCallerUrl) { this.redirectCallerUrl = redirectCallerUrl; }

    public String getRedirectSuccessUrl() { return redirectSuccessUrl; }
    public void setRedirectSuccessUrl(String redirectSuccessUrl) { this.redirectSuccessUrl = redirectSuccessUrl; }

    public String getRedirectErrorUrl() { return redirectErrorUrl; }
    public void setRedirectErrorUrl(String redirectErrorUrl) { this.redirectErrorUrl = redirectErrorUrl; }

}
