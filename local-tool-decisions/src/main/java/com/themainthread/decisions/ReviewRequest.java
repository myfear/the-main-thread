package com.themainthread.decisions;

import java.util.Map;

public record ReviewRequest(String userRequest, String toolName, Map<String, Object> arguments) {
}
