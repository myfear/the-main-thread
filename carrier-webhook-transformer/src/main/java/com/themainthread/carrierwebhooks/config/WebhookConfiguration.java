package com.themainthread.carrierwebhooks.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "carrier.webhooks")
public interface WebhookConfiguration {

    String sharedSecret();
}
