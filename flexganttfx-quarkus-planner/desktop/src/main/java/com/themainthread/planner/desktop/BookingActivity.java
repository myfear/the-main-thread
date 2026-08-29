package com.themainthread.planner.desktop;

import com.flexganttfx.model.activity.MutableActivityBase;
import com.themainthread.planner.contract.BookingDto;
import java.time.Instant;

public final class BookingActivity extends MutableActivityBase<BookingDto> {

    public BookingActivity(BookingDto booking) {
        apply(booking);
    }

    public void apply(BookingDto booking) {
        setUserObject(booking);
        setName(booking.reference());
        setStartTime(booking.startsAt());
        setEndTime(booking.endsAt());
    }

    public String bookingId() {
        return getUserObject().id();
    }

    public long version() {
        return getUserObject().version();
    }

    public Instant startsAt() {
        return getUserObject().startsAt();
    }

    public Instant endsAt() {
        return getUserObject().endsAt();
    }
}
