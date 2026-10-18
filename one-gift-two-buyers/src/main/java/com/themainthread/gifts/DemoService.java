package com.themainthread.gifts;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DemoService {
    private final RaceGate gate;

    public DemoService(RaceGate gate) {
        this.gate = gate;
    }

    @Transactional
    public ApiTypes.Round create() {
        gate.requireEnabled();
        UUID id = UUID.randomUUID();
        Gift safe = new Gift();
        safe.id = id;
        safe.name = "Winter village LEGO set";
        safe.persist();
        UnsafeGift unsafe = new UnsafeGift();
        unsafe.id = id;
        unsafe.name = safe.name;
        unsafe.persistAndFlush();
        return new ApiTypes.Round(id, view(unsafe), GiftService.view(safe));
    }

    @Transactional
    public ApiTypes.Round get(UUID id) {
        gate.requireEnabled();
        return new ApiTypes.Round(id, view(find(id)), GiftService.view(GiftService.find(id)));
    }

    @Transactional
    public ApiTypes.GiftView reserveUnsafe(UUID id, String buyer) {
        gate.requireEnabled();
        UnsafeGift gift = find(id);
        GiftService.requireAvailable(gift.reservedBy, id);
        gate.afterRead("unsafe", id);
        gift.reservedBy = buyer.strip();
        gift.flush();
        return view(gift);
    }

    private UnsafeGift find(UUID id) {
        UnsafeGift gift = UnsafeGift.findById(id);
        if (gift == null) {
            throw new ApiFailure(404, "gift_not_found", "No demo gift exists with this ID.", id);
        }
        return gift;
    }

    private ApiTypes.GiftView view(UnsafeGift gift) {
        return new ApiTypes.GiftView(gift.id, gift.name, gift.reservedBy, null);
    }
}
