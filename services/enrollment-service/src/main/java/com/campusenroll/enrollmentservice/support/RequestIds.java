package com.campusenroll.enrollmentservice.support;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestIds.class.getName() + ".value";

    private RequestIds() {
    }

    public static String from(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
