package com.themainthread.mavenlab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Consumer {
    private Consumer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass the report file path");
        }
        Files.writeString(Path.of(args[0]), Greeting.message() + "\n");
    }
}
