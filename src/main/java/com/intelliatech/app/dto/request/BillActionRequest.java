package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record BillActionRequest(@Size(max = 1000) String reason, LocalDate expectedPaymentDate) {}

