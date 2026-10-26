package com.themainthread.office;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.hibernate.Session;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.ShutdownEvent;

@ApplicationScoped
public class HibernateChatMemoryStore implements ChatMemoryStore {
    private final Session session;
    private volatile boolean stopping;

    public HibernateChatMemoryStore(Session session) {
        this.session = session;
    }

    void retainSnapshotsOnShutdown(@Observes @Priority(1) ShutdownEvent event) {
        // AI service destruction clears its cached memories. A process shutdown
        // ends the cache lifetime, not the persisted conversation lifetime.
        stopping = true;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ChatMemorySnapshot snapshot = session.find(ChatMemorySnapshot.class, id(memoryId));
            return snapshot == null ? List.of() : ChatMessageDeserializer.messagesFromJson(snapshot.messagesJson);
        });
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        QuarkusTransaction.requiringNew().run(() -> {
            UUID id = id(memoryId);
            ChatMemorySnapshot snapshot = session.find(ChatMemorySnapshot.class, id);
            if (snapshot == null) {
                snapshot = new ChatMemorySnapshot();
                snapshot.memoryId = id;
                snapshot.messagesJson = ChatMessageSerializer.messagesToJson(messages);
                session.persist(snapshot);
            }
            snapshot.messagesJson = ChatMessageSerializer.messagesToJson(messages);
            snapshot.updatedAt = Instant.now();
        });
    }

    @Override
    public void deleteMessages(Object memoryId) {
        if (stopping) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> session.createMutationQuery(
                "delete from ChatMemorySnapshot where memoryId=:id").setParameter("id", id(memoryId)).executeUpdate());
    }

    private UUID id(Object memoryId) {
        return memoryId instanceof UUID value ? value : UUID.fromString(memoryId.toString());
    }
}
