package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.VendorStatus;
import jakarta.validation.constraints.NotNull;

public record VendorStatusRequest(@NotNull VendorStatus status) {
}
