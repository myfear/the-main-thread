package com.themainthread.carrierwebhooks.transform;

import com.themainthread.carrierwebhooks.model.NormalizedShipment;

public record TransformationResult(TransformerDefinition definition, NormalizedShipment shipment) {
}
