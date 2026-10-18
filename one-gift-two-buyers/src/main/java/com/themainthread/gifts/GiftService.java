package com.themainthread.gifts;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GiftService {
    private final RaceGate gate;

    public GiftService(RaceGate gate) {
        this.gate = gate;
    }

    @Transactional
    public ApiTypes.GiftView create(String name) {
        Gift gift = new Gift();
        gift.id = UUID.randomUUID();
        gift.name = name.strip();
        gift.persistAndFlush();
        return view(gift);
    }

    @Transactional
    public ApiTypes.GiftView get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public ApiTypes.GiftView reserve(UUID id, String buyer, boolean coordinated) {
        Gift gift = find(id);
        requireAvailable(gift.reservedBy, id);
        if (coordinated) {
            gate.afterRead("optimistic", id);
        }
        gift.reservedBy = buyer.strip();
        try {
            gift.flush();
        } catch (OptimisticLockException e) {
            // Throw out of the transactional method: its transaction must roll back.
            throw new ApiFailure(409, "reservation_conflict",
                    "Another buyer reserved this gift while your request was in progress. Refresh the gift.", id);
        }
        return view(gift);
    }

    static void requireAvailable(String reservedBy, UUID id) {
        if (reservedBy != null) {
            throw new ApiFailure(409, "already_reserved", "This gift is already reserved. Choose another gift.", id);
        }
    }

    static Gift find(UUID id) {
        Gift gift = Gift.findById(id);
        if (gift == null) {
            throw new ApiFailure(404, "gift_not_found", "No gift exists with this ID.", id);
        }
        return gift;
    }

    static ApiTypes.GiftView view(Gift gift) {
        return new ApiTypes.GiftView(gift.id, gift.name, gift.reservedBy, gift.version);
    }
}
