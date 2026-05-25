package com.dynamicpay.opp.sdk.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * RSA request signer (SHA256WithRSA).
 *
 * Loads the private key from a PEM file and auto-detects PKCS8 / PKCS1 format.
 *
 * Signing rules:
 *   1. Sort parameters alphabetically by key
 *   2. Join as key1=value1&key2=value2 (skip entries with null or empty values)
 *   3. Sign with SHA256WithRSA and Base64-encode the result
 */
public class Signer {

    private final PrivateKey privateKey;

    /** 从 PEM 文件路径加载私钥（默认 / 配置态使用）。 */
    public Signer(String privateKeyPath) {
        this.privateKey = loadFromFile(privateKeyPath);
    }

    /** 内部用：直接绑定已解析的 PrivateKey（由 fromPemContent 调用）。 */
    private Signer(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * 从 PEM 字符串内容构建 Signer（动态 / 请求态使用）。
     * 适用于 {@code PaymentRequest.privateKey} 携带的临时私钥签名场景。
     * 支持标准 PEM（含 -----BEGIN/END-----）和裸 Base64 字符串。
     */
    public static Signer fromPemContent(String pemContent) {
        if (pemContent == null || pemContent.trim().isEmpty()) {
            throw new IllegalArgumentException("[OPP SDK] privateKey content must not be blank");
        }
        try {
            return new Signer(parsePem(pemContent));
        } catch (Exception e) {
            throw new RuntimeException("[OPP SDK] Failed to parse private key from inline content", e);
        }
    }

    public String sign(Map<String, Object> params) {
        try {
            String content = buildSignContent(params);
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initSign(privateKey);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new RuntimeException("[OPP SDK] RSA sign failed", e);
        }
    }

    private PrivateKey loadFromFile(String path) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            return parsePem(content);
        } catch (Exception e) {
            throw new RuntimeException("[OPP SDK] Failed to load private key from: " + path, e);
        }
    }

    /** 共享的 PEM 解析逻辑：剥离 header / 空白，base64 decode，自动识别 PKCS8 / PKCS1。 */
    private static PrivateKey parsePem(String content) throws Exception {
        String cleaned = content
                .replaceAll("-----.*?-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(cleaned);
        return isPkcs8(der) ? loadPkcs8(der) : loadPkcs1(der);
    }

    /**
     * Detect DER format by inspecting ASN.1 structure:
     *   Skip outer SEQUENCE + version, then check the next tag:
     *   0x30 = SEQUENCE (AlgorithmIdentifier) → PKCS8
     *   0x02 = INTEGER (modulus)              → PKCS1
     */
    private static boolean isPkcs8(byte[] der) {
        if (der.length < 8) return false;
        int offset = 1;
        int lenByte = der[offset] & 0xFF;
        if (lenByte == 0x82) {
            offset += 3;
        } else if (lenByte == 0x81) {
            offset += 2;
        } else {
            offset += 1;
        }
        offset += 3; // skip version: 02 01 00
        return (der[offset] & 0xFF) == 0x30;
    }

    private static PrivateKey loadPkcs8(byte[] der) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static PrivateKey loadPkcs1(byte[] der) throws Exception {
        // Wrap PKCS1 DER as PKCS8 to load without BouncyCastle
        byte[] pkcs8 = wrapPkcs1ToPkcs8(der);
        return loadPkcs8(pkcs8);
    }

    /**
     * Wrap a PKCS1 DER byte array into PKCS8 DER format without any external dependencies.
     * PKCS8 = SEQUENCE { version(0), AlgorithmIdentifier(RSA OID), OCTET STRING { pkcs1 } }
     */
    private static byte[] wrapPkcs1ToPkcs8(byte[] pkcs1Der) {
        // RSA OID: 1.2.840.113549.1.1.1
        byte[] algId = new byte[]{
            0x30, 0x0D,
            0x06, 0x09, 0x2A, (byte)0x86, 0x48, (byte)0x86, (byte)0xF7, 0x0D, 0x01, 0x01, 0x01,
            0x05, 0x00
        };
        byte[] octetString = buildTlv(0x04, pkcs1Der);
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] inner = concat(version, algId, octetString);
        return buildTlv(0x30, inner);
    }

    private static byte[] buildTlv(int tag, byte[] value) {
        byte[] length = encodeLength(value.length);
        byte[] result = new byte[1 + length.length + value.length];
        result[0] = (byte) tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(value, 0, result, 1 + length.length, value.length);
        return result;
    }

    private static byte[] encodeLength(int len) {
        if (len < 0x80) return new byte[]{(byte) len};
        if (len < 0x100) return new byte[]{(byte) 0x81, (byte) len};
        return new byte[]{(byte) 0x82, (byte)(len >> 8), (byte)(len & 0xFF)};
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private String buildSignContent(Map<String, Object> params) {
        TreeMap<String, Object> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            Object val = entry.getValue();
            boolean skip = (val == null || val.toString().isEmpty());
            if (skip) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(val);
        }
        return sb.toString();
    }
}
