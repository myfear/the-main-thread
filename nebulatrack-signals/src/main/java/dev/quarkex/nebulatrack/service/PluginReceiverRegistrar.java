package dev.quarkex.nebulatrack.service;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.quarkex.nebulatrack.model.CostAnomaly;
import dev.quarkex.nebulatrack.qualifier.Critical;
import dev.quarkex.nebulatrack.support.CostPlugin;
import io.quarkus.signals.Receivers;
import io.quarkus.signals.SignalContext;

@ApplicationScoped
public class PluginReceiverRegistrar {

    private final Receivers receivers;

    @Inject
    public PluginReceiverRegistrar(Receivers receivers) {
        this.receivers = receivers;
    }

    public Receivers.Registration register(CostPlugin plugin) {
        return receivers.newReceiver(CostAnomaly.class)
                .setQualifiers(Critical.Literal.INSTANCE)
                .setExecutionModel(Receivers.ExecutionModel.BLOCKING)
                .notify((Consumer<SignalContext<CostAnomaly>>) ctx -> plugin.process(ctx.signal()));
    }
}
