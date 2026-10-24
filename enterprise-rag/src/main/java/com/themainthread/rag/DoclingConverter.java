package com.themainthread.rag;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;

@ApplicationScoped
public class DoclingConverter {
    private final DoclingServeApi api;

    public DoclingConverter(DoclingServeApi api) {
        this.api = api;
    }

    // Invoked by the ingestion worker: convertFilesAsync reads the file before returning its stage.
    public CompletionStage<String> convert(Path file) {
        var request = ConvertDocumentRequest.builder()
                .options(ConvertDocumentOptions.builder().toFormat(OutputFormat.MARKDOWN).doOcr(false).build())
                .target(InBodyTarget.builder().build())
                .build();
        return api.convertFilesAsync(request, file).thenApply(DoclingConverter::markdown);
    }

    static String markdown(ConvertDocumentResponse response) {
        if (!(response instanceof InBodyConvertDocumentResponse body)
                || !"success".equals(body.getStatus())
                || body.getDocument() == null
                || body.getDocument().getMarkdownContent() == null
                || body.getDocument().getMarkdownContent().isBlank()) {
            throw new IllegalStateException("Docling did not return a successful, non-empty Markdown document");
        }
        return body.getDocument().getMarkdownContent();
    }
}
