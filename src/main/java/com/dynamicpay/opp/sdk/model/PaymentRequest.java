package com.dynamicpay.opp.sdk.model;

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

    /** Payment method. Optional: wechat / alipay / unionpay / vmpay / mastercard.
     *  If omitted, the payment page displays all available methods for the user to choose.
     *  Note: Mastercard Click to Pay is triggered automatically when the selected merchant
     *  is configured with isClickToPay=true — no special paymentType value is needed. */
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

    /** Merchant code. Required when extraTradeCode is "delegated". Optional otherwise. */
    private String merchantCode;

    /** Pass-through field returned as-is in the payment notification callback. Optional. */
    private String attach;

    /**
     * Cardholder email address. Optional, for Click to Pay only.
     * When provided together with {@code mobile}, the identity page is skipped and the user
     * proceeds directly to the Click to Pay card selection screen.
     */
    private String email;

    /**
     * Cardholder mobile number. Optional, for Click to Pay only.
     * Recommended format: E.164 (e.g. +85212345678).
     * When provided together with {@code email}, the identity page is skipped.
     */
    private String mobile;

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

    public String getRedirectCallerUrl() { return redirectCallerUrl; }
    public void setRedirectCallerUrl(String redirectCallerUrl) { this.redirectCallerUrl = redirectCallerUrl; }

    public String getRedirectSuccessUrl() { return redirectSuccessUrl; }
    public void setRedirectSuccessUrl(String redirectSuccessUrl) { this.redirectSuccessUrl = redirectSuccessUrl; }

    public String getRedirectErrorUrl() { return redirectErrorUrl; }
    public void setRedirectErrorUrl(String redirectErrorUrl) { this.redirectErrorUrl = redirectErrorUrl; }
}
