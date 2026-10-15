package com.example.siem;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

@Singleton
@Alternative
@Priority(1)
public class TestAuditRecorder extends AuditRecorder {
    private final List<String> records = new CopyOnWriteArrayList<>();

    public TestAuditRecorder(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public void record(SecurityAuditRecord record) {
        try {
            records.add(serialize(record));
        } catch (Exception failure) {
            throw new AssertionError("The HTTP event must serialize", failure);
        }
    }

    public List<String> records() {
        return List.copyOf(records);
    }
}
