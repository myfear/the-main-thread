package com.mainthread.tidalmesh;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.SessionScoped;

@SessionScoped
public class OrderSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Integer> checkIns = new HashMap<>();

    public int record(String orderId) {
        return checkIns.merge(orderId, 1, Integer::sum);
    }
}

