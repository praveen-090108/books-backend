package com.intelliatech.app.dto.request;
import jakarta.validation.constraints.NotBlank;
public record AcceptInvitationRequest(@NotBlank String token, @NotBlank String password) {}
