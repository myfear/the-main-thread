package com.themainthread.carrierwebhooks.api;

import com.themainthread.carrierwebhooks.model.NormalizedShipment;

public record WebhookReceipt(
        String result,
        String transformerVersion,
        String transformerSha256,
        NormalizedShipment shipment) {
}
