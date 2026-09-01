package com.campusenroll.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.campusenroll.authservice.config.SecurityProperties;
import com.campusenroll.authservice.support.AuthUnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class LegacySystemKeyVerifier {

    private final byte[] expectedKey;

    public LegacySystemKeyVerifier(SecurityProperties properties) {
        this.expectedKey = properties.sso().legacySystemApiKey().getBytes(StandardCharsets.UTF_8);
        if (expectedKey.length < 24) {
            throw new IllegalArgumentException("LEGACY_SYSTEM_API_KEY must contain at least 24 UTF-8 bytes");
        }
    }

    public void verify(String suppliedKey) {
        byte[] supplied = suppliedKey == null
                ? new byte[0]
                : suppliedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, supplied)) {
            throw new AuthUnauthorizedException(40102, "Invalid legacy system credential");
        }
    }
}
