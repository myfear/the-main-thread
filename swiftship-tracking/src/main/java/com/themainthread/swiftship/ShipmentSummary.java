package com.themainthread.swiftship;

import java.util.Map;

public record ShipmentSummary(long total, Map<ShipmentStatus, Long> byStatus) {
}
