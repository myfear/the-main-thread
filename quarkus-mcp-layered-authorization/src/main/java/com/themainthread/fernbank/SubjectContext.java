package com.themainthread.fernbank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubjectContext(
        @JsonProperty("principal_name") String principalName,
        List<String> roles) {
}
