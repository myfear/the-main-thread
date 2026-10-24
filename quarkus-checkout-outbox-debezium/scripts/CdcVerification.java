import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.themainthread.checkout.OrderPlaced;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.logging.Logger;

/** Runs against the packaged application, PostgreSQL, and real Kafka Connect. */
public class CdcVerification {
    private static final Logger LOG = Logger.getLogger(CdcVerification.class);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String TOPIC = "order-placed";
    private static final String CONNECTOR = "checkout-outbox-cdc";
    private static final String APP = "http://127.0.0.1:" + env("APP_PORT", "18086");
    private static final String CONNECT = "http://127.0.0.1:" + env("CONNECT_PORT", "28083");
    private static final String BROKER = "localhost:" + env("KAFKA_PORT", "29092");
    private final Path output;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private Process app;
    private int appStarts;
    private int assertions;
    private int scenarios;
    private boolean gapCreated;
    private final String gapConnector = "checkout-gap-" + UUID.randomUUID().toString().substring(0, 8);

    private CdcVerification() throws IOException {
        output = Path.of("target", "cdc-verification", Instant.now().toString().replace(':', '-'));
        Files.createDirectories(output);
    }

    public static void main(String[] args) throws Exception {
        var context = org.jboss.logmanager.LogContext.getLogContext();
        var console = new org.jboss.logmanager.handlers.ConsoleHandler(
                new org.jboss.logmanager.formatters.PatternFormatter("%d{HH:mm:ss} %-5p %s%e%n"));
        console.setLevel(org.jboss.logmanager.Level.INFO);
        context.getLogger("").addHandler(console);
        context.getLogger("").setLevel(org.jboss.logmanager.Level.WARNING);
        context.getLogger("CdcVerification").setLevel(org.jboss.logmanager.Level.INFO);
        CdcVerification suite = new CdcVerification();
        suite.run();
    }

    private void run() throws Exception {
        boolean success = false;
        try (ServerSocket portCheck = new ServerSocket(Integer.parseInt(env("APP_PORT", "18086")))) {
            // Refuse to interfere with an application that the suite did not start.
        }
        try {
            report.put("startedAt", Instant.now().toString());
            command("bash", "scripts/infra-up.sh");
            startApp();
            command("bash", "scripts/setup-cdc.sh");
            command("bash", "scripts/setup-cdc.sh"); // Provisioning is repeatable.
            command("bash", "scripts/register-connector.sh");
            ready();
            report.put("connectRuntime", get(CONNECT + "/"));
            report.put("postgresVersion", sql("SHOW server_version"));
            report.put("javaVersion", System.getProperty("java.version"));
            check(sql("SHOW fsync").equals("on"), "PostgreSQL fsync enabled");
            check(sql("SHOW wal_level").equals("logical"), "logical WAL");
            check(sql("SELECT rolsuper FROM pg_roles WHERE rolname='checkout_cdc'").equals("f"), "limited replication role");
            check(sql("SELECT count(*) FROM information_schema.columns WHERE table_name='outbox_event' AND column_name='published_at'").equals("0"), "publication column removed");
            check(sql("SELECT count(*) FROM pg_publication_tables WHERE pubname='checkout_outbox_pub' AND schemaname='public' AND tablename='outbox_event'").equals("1"), "publication table");
            check(sql("SELECT pubinsert AND NOT pubupdate AND NOT pubdelete AND NOT pubtruncate FROM pg_publication WHERE pubname='checkout_outbox_pub'").equals("t"), "insert-only publication");
            internalTopics();
            try (Observer observer = new Observer(TOPIC)) {
                Event normal = order("normal");
                ConsumerRecord<String, byte[]> first = delivered(observer, normal);
                fulfilled(normal);
                check(sql("SELECT count(*) FROM outbox_event WHERE id='" + normal.id + "'").equals("1"), "outbox row retained after publication");
                report.put("normal", describe(first));
                passed("normal event contract and fulfillment");

                JsonNode crashRequest = JSON.createObjectNode().put("sku", sku("crash")).put("quantity", 1);
                boolean disconnected = false;
                try {
                    request("POST", APP + "/orders", crashRequest.toString(), "commit");
                } catch (IOException expected) {
                    disconnected = true;
                }
                check(disconnected, "crash request connection dropped");
                check(app.waitFor(20, TimeUnit.SECONDS) && app.exitValue() == 1, "checkout JVM halted");
                String crashId = sql("SELECT id FROM purchase_orders WHERE sku='" + crashRequest.get("sku").asText() + "'");
                Event crashed = eventFromDatabase(Long.parseLong(crashId));
                ConsumerRecord<String, byte[]> crashRecord = delivered(observer, crashed);
                check(!app.isAlive(), "Kafka event observed while checkout remains stopped");
                check(fulfillmentCount(crashed) == 0, "fulfillment waits for consumer restart");
                report.put("checkoutCrash", describe(crashRecord));
                startApp();
                fulfilled(crashed);
                passed("checkout crash after commit");

                command("podman", "compose", "stop", "connect");
                await("inactive slot", () -> sql("SELECT active FROM pg_replication_slots WHERE slot_name='checkout_outbox_slot'").equals("f"));
                JsonNode slotBefore = slot();
                Map<TopicPartition, Long> ends = observer.ends();
                List<Event> backlog = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    backlog.add(order("outage-" + i));
                }
                observer.pollFor(Duration.ofSeconds(3));
                for (Event event : backlog) {
                    check(observer.matching(event.id).isEmpty(), "no event during Connect outage: " + event.id);
                    check(fulfillmentCount(event) == 0, "no fulfillment during outage");
                }
                check(ends.equals(observer.ends()), "topic end offsets unchanged during scoped outage observation");
                JsonNode slotAfter = slot();
                check(slotBefore.get("confirmed_flush_lsn").equals(slotAfter.get("confirmed_flush_lsn")), "slot capture position preserved during outage");
                report.put("outageSlotBefore", slotBefore);
                report.put("outageSlotAfterOrders", slotAfter);
                command("podman", "compose", "start", "connect");
                ready();
                for (Event event : backlog) {
                    delivered(observer, event);
                    fulfilled(event);
                }
                report.put("recoveredEventIds", backlog.stream().map(Event::id).toList());
                report.put("recoveredSlot", slot());
                passed("Connect outage and recovery of three committed events");

                command("podman", "compose", "up", "-d", "--no-deps", "--force-recreate", "connect");
                ready();
                fulfilled(normal);
                Event recreated = order("worker-recreated");
                delivered(observer, recreated);
                fulfilled(recreated);
                passed("worker recreation with Kafka-backed configuration and offsets");

                try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                        "bootstrap.servers", BROKER, "acks", "all",
                        "key.serializer", StringSerializer.class.getName(),
                        "value.serializer", ByteArraySerializer.class.getName()))) {
                    ProducerRecord<String, byte[]> duplicate = new ProducerRecord<>(TOPIC, first.partition(), null,
                            first.key(), first.value(), first.headers());
                    var sent = producer.send(duplicate).get(20, TimeUnit.SECONDS);
                    await("duplicate record", () -> {
                        observer.poll(Duration.ofMillis(200));
                        return observer.matching(normal.id).size() >= 2;
                    });
                    consumedThrough(sent.partition(), sent.offset() + 1);
                }
                check(fulfillmentCount(normal) == 1, "duplicate UUID has one fulfillment after group offset commit");
                passed("deliberate duplicate delivered through Kafka");

                String rolledId = UUID.randomUUID().toString();
                String rolledSku = sku("rollback");
                sql("BEGIN; INSERT INTO purchase_orders(sku,quantity,status,created_at) VALUES ('" + rolledSku + "',1,'ACCEPTED',CURRENT_TIMESTAMP); "
                        + "INSERT INTO outbox_event(id,aggregatetype,aggregateid,type,payload,created_at) "
                        + "SELECT '" + rolledId + "','Order',id::text,'OrderPlaced',jsonb_build_object('eventId','" + rolledId
                        + "','orderId',id,'sku',sku,'quantity',quantity,'placedAt',created_at),CURRENT_TIMESTAMP FROM purchase_orders WHERE sku='" + rolledSku + "'; ROLLBACK;");
                Event afterRollback = order("after-rollback");
                delivered(observer, afterRollback);
                check(sql("SELECT count(*) FROM purchase_orders WHERE sku='" + rolledSku + "'").equals("0"), "order rolled back");
                check(sql("SELECT count(*) FROM outbox_event WHERE id='" + rolledId + "'").equals("0"), "outbox rolled back");
                check(observer.matching(rolledId).isEmpty(), "aborted transaction absent after later committed marker");
                fulfilled(afterRollback);
                passed("rolled-back database transaction produces no Kafka event");

                startupGap(normal, observer);
                report.put("finalSlot", slot());
                report.put("connectorStatus", get(CONNECT + "/connectors/" + CONNECTOR + "/status"));
                success = true;
            }
        } catch (Throwable failure) {
            report.put("failure", failure.toString());
            throw failure;
        } finally {
            stopApp();
            if (gapCreated) {
                request("DELETE", CONNECT + "/connectors/" + gapConnector, null, null);
                await("gap slot inactive", () -> !sql("SELECT active FROM pg_replication_slots WHERE slot_name='checkout_gap_slot'").equals("t"));
                sql("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name='checkout_gap_slot'");
                sql("DROP PUBLICATION IF EXISTS checkout_gap_pub");
            }
            commandToFile("connect.log", "podman", "compose", "logs", "connect");
            commandToFile("postgres.log", "podman", "compose", "logs", "postgres");
            report.put("success", success);
            report.put("scenarios", scenarios);
            report.put("assertions", assertions);
            report.put("finishedAt", Instant.now().toString());
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(), report);
            LOG.infof("CDC %s scenarios=%d assertions=%d report=%s", success ? "PASS" : "FAIL", scenarios, assertions, output.resolve("report.json"));
        }
    }

    private void startupGap(Event historical, Observer main) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BROKER))) {
            if (!admin.listTopics().names().get(10, TimeUnit.SECONDS).contains("order-placed-gap")) {
                admin.createTopics(List.of(new NewTopic("order-placed-gap", 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
            }
        }
        try (Observer gap = new Observer("order-placed-gap")) {
            sql("CREATE PUBLICATION checkout_gap_pub FOR TABLE public.outbox_event WITH (publish='insert')");
            gapCreated = true;
            ObjectNode config = (ObjectNode) JSON.readTree(Path.of("config/debezium/checkout-outbox-connector.json").toFile()).get("config");
            config.put("database.password", System.getenv("CDC_PASSWORD"));
            config.put("slot.name", "checkout_gap_slot");
            config.put("publication.name", "checkout_gap_pub");
            config.put("topic.prefix", gapConnector);
            config.put("transforms.outbox.route.topic.replacement", "order-placed-gap");
            var response = request("PUT", CONNECT + "/connectors/" + gapConnector + "/config", config.toString(), null);
            check(response.statusCode() == 201 || response.statusCode() == 200, "gap connector registered");
            gapCreated = true;
            await("gap task", () -> running(gapConnector));
            await("gap slot", () -> sql("SELECT active FROM pg_replication_slots WHERE slot_name='checkout_gap_slot'").equals("t"));
            Event future = order("after-new-slot");
            await("future event on new slot", () -> {
                gap.poll(Duration.ofMillis(200));
                return !gap.matching(future.id).isEmpty();
            });
            check(gap.matching(historical.id).isEmpty(), "no_data does not backfill historical outbox rows");
            delivered(main, future);
            fulfilled(future);
            report.put("startupGap", Map.of("historicalEventNotBackfilled", historical.id, "newEventCaptured", future.id));
            passed("no_data startup gap with a separate temporary slot");
        }
    }

    private void internalTopics() throws Exception {
        List<String> topics = List.of("checkout-connect-configs", "checkout-connect-offsets", "checkout-connect-status");
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BROKER))) {
            var resources = topics.stream().map(t -> new ConfigResource(ConfigResource.Type.TOPIC, t)).toList();
            var configs = admin.describeConfigs(resources).all().get(15, TimeUnit.SECONDS);
            var descriptions = admin.describeTopics(topics).allTopicNames().get(15, TimeUnit.SECONDS);
            for (var resource : resources) {
                check(configs.get(resource).get("cleanup.policy").value().contains("compact"), "compacted " + resource.name());
                check(descriptions.get(resource.name()).partitions().stream().allMatch(p -> p.replicas().size() == 1), "local RF1 " + resource.name());
            }
        }
        passed("logical replication and Connect internal topics");
    }

    private Event order(String label) throws Exception {
        ObjectNode body = JSON.createObjectNode().put("sku", sku(label)).put("quantity", 1);
        var response = request("POST", APP + "/orders", body.toString(), null);
        check(response.statusCode() == 201, "checkout returned 201");
        long id = JSON.readTree(response.body()).get("id").asLong();
        JsonNode outbox = get(APP + "/outbox");
        for (JsonNode row : outbox) {
            if (row.get("aggregateid").asText().equals(Long.toString(id))) {
                check(!row.has("publishedAt"), "outbox API exposes no delivery flag");
                check(sql("SELECT count(*) FROM purchase_orders WHERE id=" + id).equals("1"), "one order row");
                check(sql("SELECT count(*) FROM outbox_event WHERE aggregateid='" + id + "'").equals("1"), "one outbox row");
                return new Event(row.get("id").asText(), id, JSON.readTree(row.get("payload").asText()));
            }
        }
        throw new AssertionError("Missing outbox for order " + id);
    }

    private Event eventFromDatabase(long orderId) throws Exception {
        String raw = sql("SELECT json_build_object('id',id,'payload',payload) FROM outbox_event WHERE aggregateid='" + orderId + "'");
        JsonNode row = JSON.readTree(raw);
        return new Event(row.get("id").asText(), orderId, row.get("payload"));
    }

    private ConsumerRecord<String, byte[]> delivered(Observer observer, Event event) throws Exception {
        await("Kafka event " + event.id, () -> {
            observer.poll(Duration.ofMillis(200));
            return !observer.matching(event.id).isEmpty();
        });
        ConsumerRecord<String, byte[]> record = observer.matching(event.id).getFirst();
        check(record.topic().equals(TOPIC), "unchanged topic");
        check(record.key().equals(Long.toString(event.orderId)), "string order key");
        var header = record.headers().lastHeader("id");
        check(header != null && new String(header.value(), StandardCharsets.UTF_8).equals(event.id), "UTF-8 UUID header matches payload");
        JsonNode value = JSON.readTree(record.value());
        check(value.isObject() && value.equals(event.payload), "unchanged top-level JSON object");
        check(value.has("placedAt") && !value.has("createdAt") && !value.has("schema") && !value.has("after"), "original payload fields");
        try (var deserializer = new ObjectMapperDeserializer<>(OrderPlaced.class, JSON)) {
            OrderPlaced decoded = deserializer.deserialize(TOPIC, record.value());
            check(decoded.eventId().toString().equals(event.id) && decoded.orderId() == event.orderId, "typed Quarkus deserializer with standalone Java-time mapper");
        }
        return record;
    }

    private void fulfilled(Event event) {
        await("fulfillment " + event.id, () -> fulfillmentCount(event) == 1);
    }

    private int fulfillmentCount(Event event) {
        return Integer.parseInt(sql("SELECT count(*) FROM fulfillment WHERE event_id='" + event.id + "' AND order_id=" + event.orderId));
    }

    private void consumedThrough(int partition, long end) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BROKER))) {
            await("consumer committed duplicate", () -> {
                try {
                    var offsets = admin.listConsumerGroupOffsets("checkout-fulfillment").partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                    var offset = offsets.get(new TopicPartition(TOPIC, partition));
                    return offset != null && offset.offset() >= end;
                } catch (Exception failure) {
                    return false;
                }
            });
        }
    }

    private JsonNode slot() throws Exception {
        return JSON.readTree(sql("SELECT row_to_json(s) FROM (SELECT slot_name,active,restart_lsn,confirmed_flush_lsn,wal_status,"
                + "pg_wal_lsn_diff(pg_current_wal_lsn(),restart_lsn)::bigint AS wal_distance_bytes "
                + "FROM pg_replication_slots WHERE slot_name='checkout_outbox_slot') s"));
    }

    private void ready() {
        await("connector and task", () -> running(CONNECTOR));
        await("active main slot", () -> sql("SELECT active FROM pg_replication_slots WHERE slot_name='checkout_outbox_slot'").equals("t"));
    }

    private boolean running(String name) {
        try {
            JsonNode status = get(CONNECT + "/connectors/" + name + "/status");
            return status.path("connector").path("state").asText().equals("RUNNING")
                    && status.path("tasks").size() == 1 && status.path("tasks").get(0).path("state").asText().equals("RUNNING");
        } catch (Exception failure) {
            return false;
        }
    }

    private void startApp() throws Exception {
        app = new ProcessBuilder("java", "-Dquarkus.profile=demo", "-jar", "target/quarkus-app/quarkus-run.jar")
                .redirectErrorStream(true).redirectOutput(output.resolve("application-" + (++appStarts) + ".log").toFile()).start();
        await("packaged checkout", () -> {
            if (!app.isAlive()) {
                throw new AssertionError("Application exited; inspect " + output);
            }
            try {
                return request("GET", APP + "/outbox", null, null).statusCode() == 200;
            } catch (Exception failure) {
                return false;
            }
        });
    }

    private void stopApp() throws InterruptedException {
        if (app != null && app.isAlive()) {
            app.destroy();
            if (!app.waitFor(15, TimeUnit.SECONDS)) {
                app.destroyForcibly().waitFor();
            }
        }
    }

    private String sql(String statement) {
        try {
            return command("podman", "compose", "exec", "-T", "postgres", "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "checkout", "-d", "checkout", "-c", statement).strip();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private String command(String... arguments) throws Exception {
        Path stderr = output.resolve("commands-stderr.log");
        Process child = new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.appendTo(stderr.toFile())).start();
        // Read stdout concurrently so readiness scripts cannot fill the process pipe.
        var bytes = new java.util.concurrent.CompletableFuture<byte[]>();
        Thread.ofVirtual().start(() -> {
            try { bytes.complete(child.getInputStream().readAllBytes()); }
            catch (IOException failure) { bytes.completeExceptionally(failure); }
        });
        if (!child.waitFor(180, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new AssertionError("Command timed out: " + arguments[0]);
        }
        String result = new String(bytes.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        if (child.exitValue() != 0) {
            throw new AssertionError("Command failed: " + String.join(" ", arguments) + "; " + result + "; inspect " + stderr);
        }
        return result;
    }

    private void commandToFile(String filename, String... arguments) throws Exception {
        try {
            Files.writeString(output.resolve(filename), command(arguments));
        } catch (Exception | AssertionError failure) {
            Files.writeString(output.resolve(filename), "Log capture failed: " + failure);
        }
    }

    private static JsonNode get(String url) throws Exception {
        var response = request("GET", url, null, null);
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " returned " + response.statusCode());
        }
        return JSON.readTree(response.body());
    }

    private static HttpResponse<String> request(String method, String url, String body, String crash) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10));
        if (crash != null) builder.header("X-Crash-After", crash);
        if (body != null) builder.header("Content-Type", "application/json");
        return HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private void passed(String message) {
        scenarios++;
        LOG.infof("PASS %s", message);
    }

    private static void await(String message, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(200); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        }
        throw new AssertionError("Timed out waiting for " + message);
    }

    private static String sku(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    private static Map<String, Object> describe(ConsumerRecord<String, byte[]> record) throws Exception {
        return Map.of("topic", record.topic(), "partition", record.partition(), "offset", record.offset(), "key", record.key(),
                "idHeaderUtf8", new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8), "value", JSON.readTree(record.value()));
    }

    private record Event(String id, long orderId, JsonNode payload) {}

    private static final class Observer implements AutoCloseable {
        private final KafkaConsumer<String, byte[]> consumer;
        private final List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();

        Observer(String topic) {
            consumer = new KafkaConsumer<>(Map.of("bootstrap.servers", BROKER,
                    ConsumerConfig.GROUP_ID_CONFIG, "cdc-observer-" + UUID.randomUUID(),
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()));
            var partitions = consumer.partitionsFor(topic, Duration.ofSeconds(15)).stream()
                    .map(p -> new TopicPartition(topic, p.partition())).toList();
            consumer.assign(partitions);
            var ends = consumer.endOffsets(partitions);
            ends.forEach(consumer::seek);
        }

        void poll(Duration duration) {
            consumer.poll(duration).forEach(records::add);
        }

        void pollFor(Duration duration) {
            long deadline = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < deadline) poll(Duration.ofMillis(200));
        }

        Map<TopicPartition, Long> ends() {
            return consumer.endOffsets(consumer.assignment());
        }

        List<ConsumerRecord<String, byte[]>> matching(String id) {
            return records.stream().filter(record -> {
                try { return JSON.readTree(record.value()).path("eventId").asText().equals(id); }
                catch (IOException failure) { throw new IllegalStateException("Invalid Kafka JSON", failure); }
            }).toList();
        }

        @Override
        public void close() { consumer.close(); }
    }
}
