package com.campusenroll.enrollmentservice.client;

record RemoteApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId,
        long timestamp) {
}
