package io.mainthread.catalogboard;

import jakarta.validation.constraints.Min;

public record StockAdjustment(@Min(-1000) int delta) {
}
