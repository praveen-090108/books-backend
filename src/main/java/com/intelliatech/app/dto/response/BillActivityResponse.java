package com.intelliatech.app.dto.response;

import java.time.LocalDateTime;

public record BillActivityResponse(Long id, String action, String details, String performedBy, LocalDateTime createdAt) {}

