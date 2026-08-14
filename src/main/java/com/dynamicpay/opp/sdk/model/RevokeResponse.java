package com.dynamicpay.opp.sdk.model;

/**
 * Order revoke response from PATCH /api/payment/order/{orderNum}/revoke.
 *
 * The {@code code} field follows the standard OPP error code scheme:
 *   0    — Success or "Already revoked" (idempotent)
 *   1020 — timestamp out of allowed window
 *   1022 — companyId missing
 *   1023 — verify sign failed
 *   1024 — merchant not authorized
 *   1054 — orderNum mismatch between path and body
 *   1055 — order not found
 *   1056 — order does not belong to this company
 *   1057 — order not in a revocable state (paid or already dispatched)
 *   1071 — caller suspended (server-registered caller integrations only)
 *   1072 — server-side signing key not usable (server configuration issue, not a caller error)
 *   1073 — companyId conflicts with this caller's configured org scope
 *   1076 — merCode is required for this caller (see {@link RevokeRequest#getMerCode()})
 *
 * {@code revokeTime} is server-side timestamp at the moment of revoke, useful for
 * merchant reconciliation (do not trust caller's local clock).
 */
public class RevokeResponse {

    /** Error code; 0 = success or idempotent already-revoked. */
    private int code;

    /** Human-readable message. */
    private String message;

    /**
     * Server-side revoke timestamp (ISO-8601 LocalDateTime, no zone — assumed UTC).
     * Populated only when code == 0 and the call actually triggered a state change.
     */
    private String revokeTime;

    public RevokeResponse(int code, String message, String revokeTime) {
        this.code = code;
        this.message = message;
        this.revokeTime = revokeTime;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public String getRevokeTime() { return revokeTime; }

    public boolean isSuccess() { return code == 0; }
}
