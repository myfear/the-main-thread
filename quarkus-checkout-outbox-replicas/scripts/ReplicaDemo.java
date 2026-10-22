import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jboss.logging.Logger;

// Java source launcher: uses the same PostgreSQL/Kafka/Jackson libraries as the packaged app.
public class ReplicaDemo {
    private static final Logger LOG = Logger.getLogger(ReplicaDemo.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final String BOOTSTRAP = "localhost:19092";
    private static final Path RUN = Path.of("target", "demo", UUID.randomUUID().toString());
    private static final List<Replica> PROCESSES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(RUN);
        requirePortFree(8080);
        requirePortFree(8081);
        try {
            infrastructureReady();
            check(scalar("SELECT count(*) FROM outbox_event WHERE published_at IS NULL", null) == 0,
                    "Pending data exists; finish it or use the README cleanup/reset before this demo");
        } catch (org.postgresql.util.PSQLException e) {
            // A first run has no tables until Flyway starts with the first replica.
            if (!"42P01".equals(e.getSQLState())) {
                throw e;
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> PROCESSES.forEach(Replica::stop)));
        try {
            normalConcurrency();
            Replica a = start("A-claim", 8080);
            Replica b = start("B-recover", 8081);
            crashAndRecover(a, b, "claim", 1);
            a = start("A-ack", 8080);
            UUID acknowledged = crashAndRecover(a, b, "kafka", 2);
            a = start("A-restarted", 8080);
            check(!post(a.port, "/demo/publish/" + acknowledged, null, null).path("published").asBoolean(),
                    "Completed event was reclaimed after restart");
            LOG.infof("DEMO PASS normalEvents=6 claimCrashRecords=1 ackCrashRecords=2 ackCrashFulfillments=1 logs=%s", RUN);
        } finally {
            PROCESSES.forEach(Replica::stop);
        }
    }

    private static void normalConcurrency() throws Exception {
        Replica a = start("A-normal", 8080);
        Replica b = start("B-normal", 8081);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(order(a.port, null));
        }
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> post(a.port, "/demo/publish", null, null));
            var second = pool.submit(() -> post(b.port, "/demo/publish", null, null));
            check(first.get(30, TimeUnit.SECONDS).path("published").asInt() == 1, "A did not publish one row");
            check(second.get(30, TimeUnit.SECONDS).path("published").asInt() == 1, "B did not publish one row");
        }
        post(a.port, "/demo/scheduler/resume", null, null);
        post(b.port, "/demo/scheduler/resume", null, null);
        for (UUID id : ids) {
            waitFor("normal completion " + id, 30, () -> completed(id) && fulfillmentCount(id) == 1);
            check(scalar("SELECT attempt_count FROM outbox_event WHERE id = ?", id) == 1, "Unexpected reclaim");
            check(records(id).size() == 1, "Normal publication produced duplicate records");
        }
        LOG.infof("NORMAL workers=%s,%s events=6 attemptsPerEvent=1 kafkaRecords=6 fulfillmentRows=6", a.worker, b.worker);
        a.stop();
        b.stop();
    }

    private static UUID crashAndRecover(Replica a, Replica b, String point, int expectedRecords) throws Exception {
        UUID id = order(a.port, point);
        try {
            post(a.port, "/demo/publish/" + id, null, null);
            throw new AssertionError("Targeted JVM did not close the HTTP connection");
        } catch (IOException expected) {
            // Runtime.halt closes the socket; require the exact child process to have exited.
        }
        check(a.process.waitFor(15, TimeUnit.SECONDS), "Targeted JVM did not exit");
        check(a.process.exitValue() == 1, "Crash did not exit with status 1");
        check(b.process.isAlive(), "Recovery JVM also exited");
        String oldToken = string("SELECT claim_token::text FROM outbox_event WHERE id = ?", id);
        String oldOwner = string("SELECT claimed_by FROM outbox_event WHERE id = ?", id);
        check(a.worker.equals(oldOwner), "Wrong worker acquired the controlled claim");
        check(!completed(id), "Crash recorded completion");
        check(!post(b.port, "/demo/publish/" + id, null, null).path("published").asBoolean(),
                "Recovery stole an active lease");
        captureRow(b.port, id, point + "-claimed.json");
        check(records(id).size() == expectedRecords - 1, "Unexpected pre-recovery Kafka count");
        LOG.infof("CRASH point=%s event=%s owner=%s token=%s exit=1 earlyClaim=false", point, id, oldOwner, oldToken);
        waitFor("database lease expiry", 20,
                () -> scalarUnchecked("SELECT count(*) FROM outbox_event WHERE id = ? AND claimed_until <= CURRENT_TIMESTAMP", id) == 1);
        check(post(b.port, "/demo/publish/" + id, null, null).path("published").asBoolean(), "Recovery did not complete");
        List<ConsumerRecord<String, String>> deliveries = records(id);
        check(deliveries.size() == expectedRecords, "Wrong number of Kafka records after recovery");
        waitFor("fulfillment completion", 30, () -> fulfillmentCount(id) == 1);
        check(scalar("SELECT attempt_count FROM outbox_event WHERE id = ?", id) == 2, "Expected two acquisitions");
        String newToken = claimToken(b.log, id);
        check(!oldToken.equals(newToken), "Reclaim reused the old token");
        captureRow(b.port, id, point + "-completed.json");
        LOG.infof("RECOVERY point=%s event=%s oldToken=%s newToken=%s attempts=2 kafkaRecords=%d fulfillmentRows=1 offsets=%s",
                point, id, oldToken, newToken, deliveries.size(),
                deliveries.stream().map(r -> r.partition() + ":" + r.offset()).toList());
        return id;
    }

    private static void captureRow(int port, UUID id, String filename) throws Exception {
        for (JsonNode row : get(port, "/outbox")) {
            if (id.toString().equals(row.path("id").asText())) {
                Files.writeString(RUN.resolve(filename), row.toPrettyString() + "\n");
                return;
            }
        }
        throw new AssertionError("Outbox inspection endpoint omitted " + id);
    }

    private static Replica start(String label, int port) throws Exception {
        Path log = RUN.resolve(label + ".log");
        var builder = new ProcessBuilder("bash", "scripts/start-replica.sh", label, Integer.toString(port))
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("SCHEDULER_START_MODE", "halted");
        builder.environment().put("LEASE_DURATION", "8s");
        Process process = builder.start();
        Replica replica = new Replica(process, port, log);
        PROCESSES.add(replica);
        waitFor("replica " + label + " readiness; see " + log, 45, () -> {
            if (!process.isAlive()) {
                throw new IllegalStateException("Replica exited; see " + log);
            }
            try {
                var response = get(port, "/demo/status");
                replica.worker = response.path("worker").asText();
                return !replica.worker.isBlank() && !response.path("schedulerRunning").asBoolean();
            } catch (Exception e) {
                return false;
            }
        });
        return replica;
    }

    private static UUID order(int port, String crash) throws Exception {
        long orderId = post(port, "/orders", "{\"sku\":\"replica-demo\",\"quantity\":1}", crash).path("id").asLong();
        try (var connection = database();
                var query = connection.prepareStatement("SELECT id FROM outbox_event WHERE aggregateid = ?")) {
            query.setString(1, Long.toString(orderId));
            try (var rows = query.executeQuery()) {
                check(rows.next(), "Order committed without an outbox row");
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private static JsonNode get(int port, String path) throws Exception {
        return request(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(3)).GET().build());
    }

    private static JsonNode post(int port, String path, String body, String crash) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (crash != null) {
            builder.header("X-Crash-After", crash);
        }
        return request(builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build());
    }

    private static JsonNode request(HttpRequest request) throws Exception {
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() >= 200 && response.statusCode() < 300,
                "HTTP " + response.statusCode() + ": " + response.body());
        return JSON.readTree(response.body());
    }

    private static void infrastructureReady() throws Exception {
        waitFor("PostgreSQL", 60, () -> {
            try (var connection = database()) {
                return connection.isValid(2);
            } catch (Exception e) {
                return false;
            }
        });
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP,
                "default.api.timeout.ms", "30000"))) {
            admin.describeCluster().nodes().get(45, TimeUnit.SECONDS);
            if (!admin.listTopics().names().get(10, TimeUnit.SECONDS).contains("order-placed")) {
                admin.createTopics(List.of(new NewTopic("order-placed", 1, (short) 1)))
                        .all().get(15, TimeUnit.SECONDS);
            }
        }
    }

    private static List<ConsumerRecord<String, String>> records(UUID id) throws Exception {
        try (KafkaConsumer<String, String> observer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", BOOTSTRAP, "group.id", "replica-observer-" + UUID.randomUUID(),
                "enable.auto.commit", false,
                "key.deserializer", StringDeserializer.class.getName(),
                "value.deserializer", StringDeserializer.class.getName()))) {
            var partitions = observer.partitionsFor("order-placed", Duration.ofSeconds(10)).stream()
                    .map(p -> new TopicPartition(p.topic(), p.partition())).toList();
            observer.assign(partitions);
            observer.seekToBeginning(partitions);
            var ends = observer.endOffsets(partitions, Duration.ofSeconds(10));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (partitions.stream().anyMatch(p -> observer.position(p) < ends.get(p))) {
                check(System.nanoTime() < deadline, "Observer did not reach captured end offsets");
                for (var record : observer.poll(Duration.ofMillis(100))) {
                    if (record.offset() < ends.get(new TopicPartition(record.topic(), record.partition()))
                            && id.toString().equals(JSON.readTree(record.value()).path("eventId").asText())) {
                        found.add(record);
                    }
                }
            }
            try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
                waitFor("consumer commits", 30, () -> {
                    try {
                        var offsets = admin.listConsumerGroupOffsets("checkout-fulfillment")
                                .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                        return found.stream().allMatch(r -> {
                            var offset = offsets.get(new TopicPartition(r.topic(), r.partition()));
                            return offset != null && offset.offset() > r.offset();
                        });
                    } catch (Exception e) {
                        return false;
                    }
                });
            }
            return found;
        }
    }

    private static Connection database() throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://localhost:15432/checkout?connectTimeout=2", "checkout", "checkout");
    }

    private static long scalar(String sql, UUID id) throws Exception {
        try (var connection = database(); var query = connection.prepareStatement(sql)) {
            if (id != null) {
                query.setObject(1, id);
            }
            try (var rows = query.executeQuery()) {
                check(rows.next(), "No database result");
                return rows.getLong(1);
            }
        }
    }

    private static long scalarUnchecked(String sql, UUID id) {
        try {
            return scalar(sql, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String string(String sql, UUID id) throws Exception {
        try (var connection = database(); var query = connection.prepareStatement(sql)) {
            query.setObject(1, id);
            try (var rows = query.executeQuery()) {
                check(rows.next(), "No database result");
                return rows.getString(1);
            }
        }
    }

    private static boolean completed(UUID id) {
        return scalarUnchecked("SELECT count(*) FROM outbox_event WHERE id = ? AND published_at IS NOT NULL", id) == 1;
    }

    private static long fulfillmentCount(UUID id) {
        return scalarUnchecked("SELECT count(*) FROM fulfillment WHERE event_id = ?", id);
    }

    private static String claimToken(Path log, UUID id) throws Exception {
        var match = Pattern.compile("CLAIM worker=[^ ]+ event=" + id + " token=([^ ]+)").matcher(Files.readString(log));
        check(match.find(), "Missing recovery claim log");
        return match.group(1);
    }

    private static void waitFor(String description, int seconds, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        while (!condition.getAsBoolean()) {
            check(System.nanoTime() < deadline, "Timed out: " + description);
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            check(!Thread.currentThread().isInterrupted(), "Interrupted: " + description);
        }
    }

    private static void requirePortFree(int port) throws Exception {
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class Replica {
        final Process process;
        final int port;
        final Path log;
        String worker;

        Replica(Process process, int port, Path log) {
            this.process = process;
            this.port = port;
            this.log = log;
        }

        synchronized void stop() {
            if (!process.isAlive()) {
                return;
            }
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
