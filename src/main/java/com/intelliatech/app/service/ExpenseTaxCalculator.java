package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.ExpenseTaxSummaryResponse;
import com.intelliatech.app.entity.ExpenseAmountType;
import com.intelliatech.app.entity.GstTreatment;
import com.intelliatech.app.entity.TaxRate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class ExpenseTaxCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public ExpenseTaxSummaryResponse calculate(
            BigDecimal enteredAmount,
            ExpenseAmountType amountType,
            GstTreatment gstTreatment,
            String sourceStateCode,
            String destinationStateCode,
            TaxRate tax
    ) {
        BigDecimal entered = money(enteredAmount);
        BigDecimal rate = tax == null ? BigDecimal.ZERO : tax.getRate();
        boolean taxable = tax != null
                && "TAXABLE".equalsIgnoreCase(tax.getTaxCategory())
                && rate.signum() > 0
                && gstTreatment != GstTreatment.OVERSEAS;
        String mode = taxMode(taxable, gstTreatment, sourceStateCode, destinationStateCode);
        if (!taxable || "NONE".equals(mode)) {
            return new ExpenseTaxSummaryResponse(
                    entered, entered, scaleRate(rate), mode, ZERO, ZERO, ZERO, ZERO, ZERO, entered);
        }

        BigDecimal taxableAmount;
        BigDecimal totalAmount;
        BigDecimal totalTax;
        if (amountType == ExpenseAmountType.TAX_INCLUSIVE) {
            taxableAmount = entered.multiply(ONE_HUNDRED)
                    .divide(ONE_HUNDRED.add(rate), 2, RoundingMode.HALF_UP);
            totalTax = entered.subtract(taxableAmount).setScale(2, RoundingMode.HALF_UP);
            totalAmount = entered;
        } else {
            taxableAmount = entered;
            totalTax = taxableAmount.multiply(rate)
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            totalAmount = taxableAmount.add(totalTax).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal cgst = ZERO;
        BigDecimal sgst = ZERO;
        BigDecimal igst = ZERO;
        if ("CGST_SGST".equals(mode)) {
            cgst = totalTax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            sgst = totalTax.subtract(cgst).setScale(2, RoundingMode.HALF_UP);
        } else {
            igst = totalTax;
        }
        return new ExpenseTaxSummaryResponse(
                entered, taxableAmount, scaleRate(rate), mode, cgst, sgst, igst,
                ZERO, totalTax, totalAmount);
    }

    private String taxMode(boolean taxable, GstTreatment treatment, String source, String destination) {
        if (!taxable || treatment == GstTreatment.OVERSEAS) return "NONE";
        if (treatment == GstTreatment.SPECIAL_ECONOMIC_ZONE || treatment == GstTreatment.DEEMED_EXPORT) {
            return "IGST";
        }
        if (source == null || destination == null) return "NONE";
        return source.equals(destination) ? "CGST_SGST" : "IGST";
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRate(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }
}
