package com.campusenroll.authservice.api;

import com.campusenroll.authservice.service.SsoService;
import com.campusenroll.authservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class SsoController {

    private final SsoService ssoService;

    public SsoController(SsoService ssoService) {
        this.ssoService = ssoService;
    }

    @PostMapping("/internal/v1/auth/sso-tickets")
    public ApiResponse<TicketIssueResponse> issueTicket(
            @RequestHeader(value = "X-Legacy-System-Key", required = false) String legacySystemKey,
            @Valid @RequestBody TicketIssueRequest ticketRequest,
            HttpServletRequest request) {
        return ApiResponse.success(
                ssoService.issueTicket(legacySystemKey, ticketRequest),
                RequestIds.from(request));
    }

    @PostMapping("/api/v1/auth/sso/exchange")
    public ApiResponse<TokenResponse> exchange(
            @Valid @RequestBody TicketExchangeRequest exchangeRequest,
            HttpServletRequest request) {
        return ApiResponse.success(ssoService.exchange(exchangeRequest), RequestIds.from(request));
    }
}
