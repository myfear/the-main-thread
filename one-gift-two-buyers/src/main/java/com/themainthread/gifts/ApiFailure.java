package com.themainthread.gifts;

import java.util.UUID;

public class ApiFailure extends RuntimeException {
    final int status;
    final ApiTypes.ErrorBody body;

    ApiFailure(int status, String code, String message, UUID giftId) {
        super(message);
        this.status = status;
        this.body = new ApiTypes.ErrorBody(code, message, giftId);
    }
}
