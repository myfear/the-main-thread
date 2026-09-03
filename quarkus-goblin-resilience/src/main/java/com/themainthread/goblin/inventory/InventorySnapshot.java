package com.themainthread.goblin.inventory;

public record InventorySnapshot(String sku, int available, boolean expressEligible, String source) {
}
