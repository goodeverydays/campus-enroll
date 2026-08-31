package com.campusenroll.authservice.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class ServiceInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "auth-service",
                "phase", "1-skeleton",
                "timestamp", Instant.now().toString());
    }
}
