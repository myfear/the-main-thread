// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

public record AirportResponse(String iata, String name, String runwayLength, String sourceUri, String sourceFormat) {
}
