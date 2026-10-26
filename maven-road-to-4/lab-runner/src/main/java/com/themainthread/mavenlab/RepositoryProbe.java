package com.themainthread.mavenlab;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class RepositoryProbe implements AutoCloseable {
    private static final String PREFIX = "/repository/com/themainthread/mavenlab/secured/1.0.0/secured-1.0.0";
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString("demo:demo-password".getBytes(StandardCharsets.UTF_8));
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();
    private final Map<String, byte[]> contents;

    RepositoryProbe() throws IOException {
        byte[] pom = """
                <project><modelVersion>4.0.0</modelVersion>
                <groupId>com.themainthread.mavenlab</groupId><artifactId>secured</artifactId>
                <version>1.0.0</version></project>
                """.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("repository-proof.txt"));
            zip.write("dummy repository artifact\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        contents = Map.of(PREFIX + ".pom", pom, PREFIX + ".jar", bytes.toByteArray());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    String origin() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Map<String, Object>> drainRequests() {
        List<Map<String, Object>> result = new ArrayList<>(requests);
        requests.clear();
        return result;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            boolean authorized = AUTHORIZATION.equals(exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(Map.of("path", path, "authorized", authorized));
            byte[] content = content(path);
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
            } else if (!authorized) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"maven-lab\"");
                exchange.sendResponseHeaders(401, -1);
            } else {
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            }
        }
    }

    private byte[] content(String path) {
        if (contents.containsKey(path)) {
            return contents.get(path);
        }
        for (String algorithm : List.of("SHA-1", "MD5")) {
            String suffix = algorithm.equals("SHA-1") ? ".sha1" : ".md5";
            if (path.endsWith(suffix)) {
                byte[] original = contents.get(path.substring(0, path.length() - suffix.length()));
                if (original != null) {
                    try {
                        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(original))
                                .getBytes(StandardCharsets.UTF_8);
                    } catch (NoSuchAlgorithmException error) {
                        throw new IllegalStateException(error);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
