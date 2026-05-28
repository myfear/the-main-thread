package dev.quarkex.nebulatrack.support;

import java.util.UUID;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class InvocationTrace {

    private final UUID id = UUID.randomUUID();

    public UUID id() {
        return id;
    }
}
