package com.dynamicpay.opp.sdk.model;

/**
 * Order revoke (cancellation) request parameters.
 *
 * Maps to OPP endpoint: PATCH /api/payment/order/{orderNum}/revoke
 *
 * Business rule (enforced server-side): an order can be revoked only when
 *   status = 0          (unpaid)              AND
 *   is_dispatched = 0   (not yet dispatched downstream)
 *
 * Already-revoked orders are idempotent — repeated revoke calls return
 * { code: 0, message: "Already revoked" }.
 */
public class RevokeRequest {

    /**
     * Company / merchant ID. Optional — when omitted the SDK falls back to OppProperties.companyId.
     * Maps to AccesskeyDto.companyId server-side; the order must belong to this company,
     * otherwise the server rejects with code 1056 "Order does not belong to this company".
     *
     * Pass an explicit empty string ({@code ""}) to omit companyId entirely for this call
     * (bypassing the SDK-config fallback) — needed for server-registered callers that use a
     * server-side org_id override and must NOT send companyId at all. See {@code OppProperties}
     * javadoc for when this applies. Leave {@code null} for normal usage.
     */
    private String companyId;

    /**
     * Company display name. Optional in general; <b>REQUIRED when applyServiceAccessType is "billpay"</b>.
     * Same semantics as PaymentRequest.companyName — server uses it as orgName for signature verification
     * under the BillPay channel (where opp_organization table is bypassed). Missing in billpay mode
     * returns code 1037 "companyName is required when applyServiceAccessType is billpay".
     */
    private String companyName;

    /**
     * Order number to revoke (assigned by the platform when the access key was created). Required.
     * Echoed both in the URL path and in the body — server cross-checks them.
     */
    private String orderNum;

    /**
     * Service access type. Optional.
     *
     * Determines which public key the server uses to verify the signature. "opp" (default) and
     * "billpay" are the two channels available to ordinary integrations; additional values may be
     * registered server-side by DynamicPay operations for specific internal integrations — consult
     * DynamicPay technical staff if you were told to use a value other than "opp" or "billpay".
     */
    private String applyServiceAccessType;

    /**
     * Acquirer merchant code. Optional for ordinary integrations ("opp" / "billpay"); <b>REQUIRED</b>
     * for server-registered callers whose merchant scope is declared per-request rather than derived
     * from an org binding (server-side: {@code CALLER_DECLARED} scope) — for those, the server cannot
     * otherwise tell which merchant this order belongs to (its {@code companyId}/org_id is a shared
     * label across all of that caller's traffic, not merchant-specific), and omitting it returns
     * code 1076 "merCode is required". Ordinary "opp"/"billpay" integrations can leave this null;
     * the server ignores it for those channels.
     */
    private String merCode;

    /**
     * Free-form revoke reason, max 256 characters. Optional.
     * Persisted in opp_order.revoke_reason for audit / reconciliation.
     */
    private String revokeReason;

    /**
     * Optional. Inline RSA private key content (PEM string) used only for signing this request.
     * Same semantics as PaymentRequest.privateKey — useful when one application signs for
     * multiple companyIds (each with its own key) without rebuilding the SDK client.
     *
     * SECURITY:
     *   - This field is NEVER added to the signed content or to the outbound HTTP body.
     *   - Caller is responsible for keeping the key material secure in memory.
     *
     * Accepts both standard PEM (with -----BEGIN/END----- headers) and raw Base64 PKCS8 content.
     */
    private String privateKey;

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getOrderNum() { return orderNum; }
    public void setOrderNum(String orderNum) { this.orderNum = orderNum; }

    public String getApplyServiceAccessType() { return applyServiceAccessType; }
    public void setApplyServiceAccessType(String applyServiceAccessType) { this.applyServiceAccessType = applyServiceAccessType; }

    public String getMerCode() { return merCode; }
    public void setMerCode(String merCode) { this.merCode = merCode; }

    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
}
