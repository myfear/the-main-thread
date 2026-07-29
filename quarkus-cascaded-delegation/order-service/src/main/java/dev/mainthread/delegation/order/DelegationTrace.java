package dev.mainthread.delegation.order;

import java.util.List;

public record DelegationTrace(String orderId, String status, List<ClaimSnapshot> hops) {

    public DelegationTrace {
        hops = List.copyOf(hops);
    }
}
