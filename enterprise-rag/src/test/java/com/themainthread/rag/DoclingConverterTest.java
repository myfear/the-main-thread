package com.themainthread.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import ai.docling.serve.api.convert.response.ZipArchiveConvertDocumentResponse;

class DoclingConverterTest {
    @Test
    void acceptsOnlySuccessfulNonEmptyInBodyMarkdown() {
        var valid = InBodyConvertDocumentResponse.builder().status("success")
                .document(DocumentResponse.builder().markdownContent("# Example").build()).build();
        assertEquals("# Example", DoclingConverter.markdown(valid));
        assertThrows(IllegalStateException.class, () -> DoclingConverter.markdown(valid.toBuilder()
                .status("partial_success").build()));
        assertThrows(IllegalStateException.class, () -> DoclingConverter.markdown(valid.toBuilder()
                .document(DocumentResponse.builder().markdownContent(" ").build()).build()));
        assertThrows(IllegalStateException.class,
                () -> DoclingConverter.markdown(ZipArchiveConvertDocumentResponse.builder().build()));
        assertThrows(IllegalStateException.class,
                () -> DoclingConverter.markdown(valid.toBuilder().document(null).build()));
    }

    @Test
    void propagatesApiFailure() {
        var api = mock(DoclingServeApi.class);
        when(api.convertFilesAsync(any(ConvertDocumentRequest.class), any(Path.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("401")));
        assertThrows(Exception.class, () -> new DoclingConverter(api).convert(Path.of("example.pdf"))
                .toCompletableFuture().join());
    }
}
