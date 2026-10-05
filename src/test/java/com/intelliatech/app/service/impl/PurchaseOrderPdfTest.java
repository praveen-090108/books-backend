package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.entity.PurchaseOrder;
import com.intelliatech.app.entity.PurchaseOrderItem;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.PurchaseOrderRepository;
import com.intelliatech.app.repository.SupplyStateRepository;
import com.intelliatech.app.repository.TaxRateRepository;
import com.intelliatech.app.repository.VendorRepository;
import com.intelliatech.app.service.DocumentNumberPreferenceService;
import com.intelliatech.app.service.ExpenseTaxCalculator;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderPdfTest {
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock VendorRepository vendorRepository;
    @Mock BusinessRecordRepository businessRecordRepository;
    @Mock TaxRateRepository taxRateRepository;
    @Mock SupplyStateRepository supplyStateRepository;
    @Mock ExpenseTaxCalculator taxCalculator;
    @Mock DocumentNumberPreferenceService numberService;

    private PurchaseOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderServiceImpl(purchaseOrderRepository, vendorRepository, businessRecordRepository,
                taxRateRepository, supplyStateRepository, taxCalculator, numberService, new ObjectMapper());
    }

    @Test
    void pdfContainsCompleteOrderAndRepeatsItemHeaderAcrossPages() throws Exception {
        PurchaseOrder order = orderWithItems(42);
        when(purchaseOrderRepository.findByIdAndOrganizationIdAndDeletedFalse(9L, 1L)).thenReturn(Optional.of(order));

        PdfReader reader = new PdfReader(service.pdf(9L));
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page));
        }

        String normalized = text.toString().replaceAll("\\s+", " ");
        assertThat(reader.getNumberOfPages()).isGreaterThan(1);
        assertThat(normalized).contains("PURCHASE ORDER", "PO-2026-0009", "Expected Delivery", "ABC Supplies",
                "Billing Address", "Shipping Address", "Item Name", "Description", "HSN/SAC", "Discount",
                "Tax Amount", "Subtotal", "CGST", "SGST", "Shipping Charges", "Adjustment", "Grand Total",
                "Total Amount in Words", "Eleven Thousand Eight Hundred Rupees Only", "Authorized Signature",
                "Terms & Conditions", "Delivery within business hours");
        assertThat(extractor.getTextFromPage(2).replaceAll("\\s+", " ")).contains("Item Name", "Description", "HSN/SAC");
        reader.close();
    }

    @Test
    void singleItemInterstateOrderShowsIgstAndStaysOnOnePage() throws Exception {
        PurchaseOrder order = orderWithItems(1);
        order.setCgstAmount(BigDecimal.ZERO);
        order.setSgstAmount(BigDecimal.ZERO);
        order.setIgstAmount(new BigDecimal("1710.00"));
        when(purchaseOrderRepository.findByIdAndOrganizationIdAndDeletedFalse(9L, 1L)).thenReturn(Optional.of(order));

        PdfReader reader = new PdfReader(service.pdf(9L));
        String text = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");

        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        assertThat(text).contains("Managed Network Switch 1", "IGST", "1,710.00", "Grand Total");
        reader.close();
    }

    @Test
    void multipleItemsFitWithoutTruncatingTheFinalRow() throws Exception {
        PurchaseOrder order = orderWithItems(6);
        when(purchaseOrderRepository.findByIdAndOrganizationIdAndDeletedFalse(9L, 1L)).thenReturn(Optional.of(order));

        PdfReader reader = new PdfReader(service.pdf(9L));
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));

        assertThat(text.toString().replaceAll("\\s+", " "))
                .contains("Managed Network Switch 1", "Managed Network Switch 6", "Authorized Signature");
        reader.close();
    }

    private PurchaseOrder orderWithItems(int count) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.setPurchaseOrderNumber("PO-2026-0009");
        order.setPurchaseOrderDate(LocalDate.of(2026, 7, 28));
        order.setExpectedDeliveryDate(LocalDate.of(2026, 8, 12));
        order.setVendorName("ABC Supplies");
        order.setVendorAddressJson("{\"attention\":\"Accounts Team\",\"addressLine1\":\"12 Market Road\",\"city\":\"Mumbai\",\"state\":\"Maharashtra\",\"postalCode\":\"400001\",\"country\":\"India\",\"gstin\":\"27ABCDE1234F1Z5\",\"phone\":\"+91 90000 00000\"}");
        order.setDeliveryAddressJson("{\"attention\":\"Stores Team\",\"addressLine1\":\"IntelliaTech Campus\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"postalCode\":\"411001\",\"country\":\"India\"}");
        order.setPaymentTerms("Net 30");
        order.setShipmentPreference("Road Transport");
        order.setCurrencyCode("INR");
        order.setSubtotal(new BigDecimal("10000.00"));
        order.setDiscountAmount(new BigDecimal("500.00"));
        order.setCgstAmount(new BigDecimal("855.00"));
        order.setSgstAmount(new BigDecimal("855.00"));
        order.setShippingCharge(new BigDecimal("500.00"));
        order.setAdjustmentAmount(new BigDecimal("90.00"));
        order.setTotalAmount(new BigDecimal("11800.00"));
        order.setTermsAndConditions("Delivery within business hours");
        order.setNotes("Inspect all items before acceptance.");
        for (int index = 1; index <= count; index++) {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setItemName("Managed Network Switch " + index);
            item.setDescription("Twenty-four port managed switch with a long description that must wrap safely inside the item row.");
            item.setHsnCode("85176290");
            item.setQuantity(BigDecimal.ONE);
            item.setUnit("Nos");
            item.setRate(new BigDecimal("250.00"));
            item.setDiscountAmount(new BigDecimal("5.00"));
            item.setTaxRate(new BigDecimal("18.00"));
            item.setCgstAmount(new BigDecimal("22.05"));
            item.setSgstAmount(new BigDecimal("22.05"));
            item.setLineTotal(new BigDecimal("289.10"));
            item.setSortOrder(index);
            item.setPurchaseOrder(order);
            order.getItems().add(item);
        }
        return order;
    }
}
