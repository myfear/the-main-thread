package dev.mainthread.bobweb.acp;

public record AcpEvent(String sessionId, String type, Object update) {
}
