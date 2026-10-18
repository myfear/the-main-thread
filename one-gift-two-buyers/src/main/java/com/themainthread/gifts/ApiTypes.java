package com.themainthread.gifts;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ApiTypes {
    private ApiTypes() {
    }

    public record NewGift(@NotBlank @Size(max = 120) String name) {
    }

    public record Reservation(@NotBlank @Size(max = 80) String buyer) {
    }

    public record GiftView(UUID id, String name, String reservedBy, Long version) {
    }

    public record Round(UUID id, GiftView unsafe, GiftView optimistic) {
    }

    public record ErrorBody(String code, String message, UUID giftId) {
    }
}
