package com.catalogapi.json;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = MoneySerializer.class)
public record Money(String currency, long amountMinor) {
}
