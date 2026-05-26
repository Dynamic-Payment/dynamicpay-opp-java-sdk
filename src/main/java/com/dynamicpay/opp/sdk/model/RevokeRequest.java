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
     * Allowed values: "opp" (default) / "billpay".
     * Same semantics as PaymentRequest.applyServiceAccessType — determines which public key
     * the server uses to verify the signature.
     */
    private String applyServiceAccessType;

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

    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
}
