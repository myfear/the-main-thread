package com.themainthread.carrierwebhooks.transform;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;

@ScriptInterface
public interface CarrierWebhookTransformer {

    String normalize(String webhookJson);
}
