package com.themainthread.fernbank;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdmissionInput(
        SubjectContext subject,
        SkillManifest skill,
        @JsonProperty("runtime_environment") String runtimeEnvironment,
        String action) {
}
