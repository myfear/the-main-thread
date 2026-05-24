package com.mainthread.loanflow.document.dto;

import java.time.Instant;

public record DocumentWriteRequest(
        String loanId,
        String submittedBy,
        String branch,
        String creditBand,
        Instant submittedAt) {
}
