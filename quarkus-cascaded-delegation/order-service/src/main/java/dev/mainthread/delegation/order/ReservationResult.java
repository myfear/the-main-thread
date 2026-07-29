package dev.mainthread.delegation.order;

import java.util.List;

public record ReservationResult(String orderId, String status, List<ClaimSnapshot> hops) {

    public ReservationResult {
        hops = List.copyOf(hops);
    }
}
