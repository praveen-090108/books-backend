package com.intelliatech.app.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record UserRequest(
        @NotNull Long resourceId,
        @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotBlank String role,
        String status) {}
