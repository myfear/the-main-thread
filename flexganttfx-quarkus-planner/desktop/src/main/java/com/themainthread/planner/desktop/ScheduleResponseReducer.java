package com.themainthread.planner.desktop;

import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.ScheduleProblem;

public final class ScheduleResponseReducer {

    public enum Action {
        COMMIT,
        RESTORE,
        REPLACE
    }

    public record Decision(Action action, BookingDto booking, String message) {
    }

    public Decision decide(int statusCode, BookingDto successBody, ScheduleProblem problemBody) {
        if (statusCode >= 200 && statusCode < 300) {
            return new Decision(Action.COMMIT, successBody, null);
        }
        if (problemBody != null && "STALE_BOOKING".equals(problemBody.code())) {
            return new Decision(Action.REPLACE, problemBody.currentBooking(), problemBody.message());
        }
        String message = problemBody == null ? "Schedule proposal rejected" : problemBody.message();
        return new Decision(Action.RESTORE, null, message);
    }
}
