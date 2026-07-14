package com.dynamicpay.opp.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single sub-merchant amount split entry within {@link PaymentRequest#getSubMerAmount()}.
 *
 * IMPORTANT — signature compatibility:
 * The server does not sign a JSON-canonicalized form of {@code subMerAmount}; it reflects the
 * raw deserialized {@code List<SubMerItemDTO>} field into the sign map and calls
 * {@code String.valueOf(list)} on it (see server-side {@code RsaUtils.getSignData}), which in
 * turn calls this class's {@code toString()} for every element (via {@code AbstractCollection}'s
 * default list toString: {@code [e1, e2, ...]}).
 *
 * The server's own {@code SubMerItemDTO} is a Lombok {@code @Data} STATIC NESTED CLASS inside
 * {@code AccesskeyDto} — empirically verified (compiled + ran both sides side by side) that
 * Lombok's generated toString() for a nested class includes the OUTER class name as a prefix:
 *
 *   {@code AccesskeyDto.SubMerItemDTO(subMid=M001, type=A, amount=100)}
 *
 * NOT just {@code SubMerItemDTO(...)}. This class lives at the top level in the SDK (not nested
 * inside anything called AccesskeyDto), so Lombok's auto-generated toString() would produce the
 * wrong (unprefixed) string and silently break signature verification. {@code @Data} still
 * generates getters/setters/equals/hashCode below, but toString() is hand-overridden to
 * reproduce the server's exact prefixed format — Lombok detects the existing method and skips
 * generating its own.
 *
 * Do not "clean up" this override — if it's removed, or if server-side field order/names change,
 * verify against the server's actual generated output before touching this again.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubMerItemDTO {

    /** Sub-merchant ID. Required. Max 32 characters (enforced server-side). */
    private String subMid;

    /** Split type. Required. Single character, "A" or "D" (enforced server-side). */
    private String type;

    /** Split amount in the smallest currency unit (cents). Required, must be greater than 0. */
    private Long amount;

    @Override
    public String toString() {
        return "AccesskeyDto.SubMerItemDTO(subMid=" + subMid + ", type=" + type + ", amount=" + amount + ")";
    }
}
