package com.intelliatech.app.dto.response;
public record AuthResponse(String token, UserResponse user, Long organizationId, String organizationName) {}
