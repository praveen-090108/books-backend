package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record OverviewPartyTotalResponse(
        String name,
        BigDecimal total,
        long count
) {
}
