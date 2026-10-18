package dev.mainthread.openui;

import java.util.function.Consumer;

interface BobConnection extends AutoCloseable {
    String initialize() throws Exception;

    String prompt(String instructions, Consumer<String> text) throws Exception;

    @Override
    void close();
}
