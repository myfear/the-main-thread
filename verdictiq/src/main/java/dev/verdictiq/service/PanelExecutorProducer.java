package dev.verdictiq.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.verdictiq.qualifier.PanelWork;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class PanelExecutorProducer {

    @Produces
    @ApplicationScoped
    @PanelWork
    ExecutorService panelExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    void close(@Disposes @PanelWork ExecutorService executorService) {
        executorService.close();
    }
}
