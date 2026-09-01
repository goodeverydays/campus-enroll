package com.campusenroll.authservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketExchangeRequest(@NotBlank @Size(max = 256) String ticket) {
}
