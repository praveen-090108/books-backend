package com.intelliatech.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.intelliatech.app.entity.ExpenseAmountType;
import com.intelliatech.app.entity.GstTreatment;
import com.intelliatech.app.entity.TaxRate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExpenseTaxCalculatorTest {
    private final ExpenseTaxCalculator calculator = new ExpenseTaxCalculator();

    @Test
    void taxExclusiveIntraStateSplitsCgstAndSgst() {
        var totals = calculator.calculate(new BigDecimal("10000"), ExpenseAmountType.TAX_EXCLUSIVE,
                GstTreatment.REGISTERED_BUSINESS_REGULAR, "23", "23", tax("18"));
        assertThat(totals.taxableAmount()).isEqualByComparingTo("10000.00");
        assertThat(totals.cgstAmount()).isEqualByComparingTo("900.00");
        assertThat(totals.sgstAmount()).isEqualByComparingTo("900.00");
        assertThat(totals.igstAmount()).isEqualByComparingTo("0.00");
        assertThat(totals.totalAmount()).isEqualByComparingTo("11800.00");
    }

    @Test
    void taxInclusiveInterStateBackCalculatesTaxableValue() {
        var totals = calculator.calculate(new BigDecimal("11800"), ExpenseAmountType.TAX_INCLUSIVE,
                GstTreatment.REGISTERED_BUSINESS_REGULAR, "27", "23", tax("18"));
        assertThat(totals.taxableAmount()).isEqualByComparingTo("10000.00");
        assertThat(totals.igstAmount()).isEqualByComparingTo("1800.00");
        assertThat(totals.totalAmount()).isEqualByComparingTo("11800.00");
    }

    @Test
    void overseasExpenseNeverAppliesDomesticGst() {
        var totals = calculator.calculate(new BigDecimal("10000"), ExpenseAmountType.TAX_EXCLUSIVE,
                GstTreatment.OVERSEAS, "97", "23", tax("18"));
        assertThat(totals.totalTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(totals.totalAmount()).isEqualByComparingTo("10000.00");
    }

    private TaxRate tax(String rate) {
        TaxRate tax = new TaxRate();
        tax.setRate(new BigDecimal(rate));
        tax.setTaxCategory("TAXABLE");
        return tax;
    }
}
