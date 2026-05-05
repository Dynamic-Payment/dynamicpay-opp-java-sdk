package com.dynamicpay.opp.sdk.autoconfigure;

import com.dynamicpay.opp.sdk.auth.Signer;
import com.dynamicpay.opp.sdk.client.OppClient;
import com.dynamicpay.opp.sdk.config.OppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for OPP SDK.
 *
 * Automatically registers {@link Signer} and {@link OppClient} beans
 * when the application starts. No manual @Bean declaration needed.
 */
@Configuration
@EnableConfigurationProperties(OppProperties.class)
public class OppAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Signer signer(OppProperties properties) {
        properties.validate();
        return new Signer(properties.getPrivateKeyPath());
    }

    @Bean
    @ConditionalOnMissingBean
    public OppClient oppClient(OppProperties properties, Signer signer) {
        return new OppClient(properties, signer);
    }
}
