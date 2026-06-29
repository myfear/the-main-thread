package dev.mainthread.incidents;

import java.util.List;

public record SearchResponse(int count, List<IncidentMatch> matches) {
}
