package com.dynamicpay.opp.sdk.config;

public enum OppEnvironment {

    SANDBOX("https://uat-opp-api.dynamicg.com", "https://uat-opp.dynamicg.com"),
    PROD("https://opp-api.dynamicg.com", "https://opp.dynamicg.com");

    private final String apiUrl;
    private final String pageUrl;

    OppEnvironment(String apiUrl, String pageUrl) {
        this.apiUrl = apiUrl;
        this.pageUrl = pageUrl;
    }

    /** @deprecated Use {@link #getApiUrl()} instead. */
    @Deprecated
    public String getUrl() { return apiUrl; }

    public String getApiUrl() { return apiUrl; }

    public String getPageUrl() { return pageUrl; }
}
