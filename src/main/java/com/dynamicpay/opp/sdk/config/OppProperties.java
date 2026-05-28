package com.dynamicpay.opp.sdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OPP SDK configuration properties.
 *
 * Minimal application.yml example:
 *   opp:
 *     company-id: YOUR_COMPANY_ID
 *     private-key-path: /etc/opp/private_key_pkcs8.pem
 *     environment: sandbox   # sandbox or prod; defaults to prod when omitted
 *
 * Optional URL overrides (take precedence over the built-in defaults):
 *   opp:
 *     sandbox-url: http://192.168.1.10:8085
 *     prod-url: https://my-custom-prod.example.com
 *     sandbox-page-url: http://192.168.1.10:3000
 *     prod-page-url: https://my-custom-opp-page.example.com
 */
@ConfigurationProperties(prefix = "opp")
public class OppProperties {

    /** Company ID assigned by DynamicPay */
    private String companyId;

    /** Absolute path to the RSA private key file (PKCS8 format, downloaded from merchant portal) */
    private String privateKeyPath;

    /** Runtime environment: sandbox or prod. Defaults to prod when not configured. */
    private String environment = "prod";

    /**
     * Custom sandbox server URL (optional).
     * When set, overrides the built-in sandbox default.
     */
    private String sandboxUrl;

    /**
     * Custom prod server URL (optional).
     * When set, overrides the built-in prod default.
     */
    private String prodUrl;

    /**
     * Custom sandbox OPP page URL (optional).
     * When set, overrides the built-in sandbox page URL default.
     */
    private String sandboxPageUrl;

    /**
     * Custom prod OPP page URL (optional).
     * When set, overrides the built-in prod page URL default.
     */
    private String prodPageUrl;

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getSandboxUrl() { return sandboxUrl; }
    public void setSandboxUrl(String sandboxUrl) { this.sandboxUrl = sandboxUrl; }

    public String getProdUrl() { return prodUrl; }
    public void setProdUrl(String prodUrl) { this.prodUrl = prodUrl; }

    public String getSandboxPageUrl() { return sandboxPageUrl; }
    public void setSandboxPageUrl(String sandboxPageUrl) { this.sandboxPageUrl = sandboxPageUrl; }

    public String getProdPageUrl() { return prodPageUrl; }
    public void setProdPageUrl(String prodPageUrl) { this.prodPageUrl = prodPageUrl; }

    public String resolvePageUrl() {
        if ("prod".equalsIgnoreCase(environment)) {
            return (prodPageUrl != null && !prodPageUrl.trim().isEmpty()) ? prodPageUrl : OppEnvironment.PROD.getPageUrl();
        }
        return (sandboxPageUrl != null && !sandboxPageUrl.trim().isEmpty()) ? sandboxPageUrl : OppEnvironment.SANDBOX.getPageUrl();
    }

    public String resolveServerUrl() {
        if ("prod".equalsIgnoreCase(environment)) {
            return (prodUrl != null && !prodUrl.trim().isEmpty()) ? prodUrl : OppEnvironment.PROD.getUrl();
        }
        return (sandboxUrl != null && !sandboxUrl.trim().isEmpty()) ? sandboxUrl : OppEnvironment.SANDBOX.getUrl();
    }

    public void validate() {
        if (companyId == null || companyId.trim().isEmpty()) {
            throw new IllegalStateException("[OPP SDK] opp.company-id must not be blank");
        }
        if (privateKeyPath == null || privateKeyPath.trim().isEmpty()) {
            throw new IllegalStateException("[OPP SDK] opp.private-key-path must not be blank");
        }
        if (!"sandbox".equalsIgnoreCase(environment) && !"prod".equalsIgnoreCase(environment)) {
            throw new IllegalStateException("[OPP SDK] opp.environment must be 'sandbox' or 'prod'");
        }
    }
}
