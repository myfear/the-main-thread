package com.themainthread.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class RagResourceTest {
    @Test
    void directRequestsCannotBypassUnreadyCorpus() {
        var state = new IndexingState();
        var bot = mock(SalesEnablementBot.class);
        var resource = new RagResource(state, mock(DocumentLoader.class), mock(DocumentRetriever.class), bot);
        for (var status : new IndexingState.Status[] {
                IndexingState.Status.INDEXING, IndexingState.Status.EMPTY, IndexingState.Status.FAILED }) {
            state.update(status, 0, 0, "Not ready");
            try (var response = resource.ask("Show CloudX pricing")) {
                assertEquals(503, response.getStatus());
            }
        }
        verifyNoInteractions(bot);
    }
}
