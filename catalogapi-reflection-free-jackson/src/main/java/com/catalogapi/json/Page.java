package com.catalogapi.json;

import java.util.List;

public record Page<T>(List<T> items, int total) {
}
