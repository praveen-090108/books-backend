package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record OverviewCategoryTotalResponse(
        String label,
        BigDecimal total,
        long count
) {
}
