package com.intelliatech.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.response.IrpCredentials;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.repository.BusinessRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class InvoiceToEInvoiceMapperTest {

    @Mock
    private BusinessRecordRepository records;
    @Mock private IrpConfigurationService configurations;

    private InvoiceToEInvoiceMapper mapper;
    private BusinessRecord invoice;
    private BusinessRecord customer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(configurations.getActiveCredentials(1L)).thenReturn(new IrpCredentials(1L,1L,"EY_IRP_5", IrpEnvironment.SANDBOX,"https://sandbox.example","client","secret","user","password","23AAECI4774Q1Z0","1.0",1L));
        mapper = new InvoiceToEInvoiceMapper(objectMapper, records, configurations);

        BusinessRecord organization = record("settings", "organization", "ORG-1", "IntelliaTech Solutions Pvt Ltd");
        organization.setReferenceNumber("23AAECI4774Q1Z0");
        organization.setPartyCity("Indore");
        organization.setNotes("{\"address\":\"101 Business Park\",\"state\":\"Madhya Pradesh\",\"pinCode\":\"452001\"}");
        when(records.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization"))
                .thenReturn(Optional.of(organization));

        customer = record("sales", "customers", "CUST-1", "Registered Customer Pvt Ltd");
        customer.setId(9L);
        customer.setReferenceNumber("23AAAAA0000A1Z5");
        customer.setPartyCity("Bhopal");
        customer.setNotes("{\"billingAddress\":\"12 Market Road\",\"state\":\"Madhya Pradesh\",\"pinCode\":\"462001\",\"gstin\":\"23AAAAA0000A1Z5\"}");
        when(records.findByModuleAndTypeAndId("sales", "customers", 9L)).thenReturn(Optional.of(customer));

        invoice = record("sales", "invoices", "INV-2026-001", "Registered Customer Pvt Ltd");
        invoice.setId(44L);
        invoice.setRecordDate(LocalDate.of(2026, 10, 7));
        invoice.setAmount(new BigDecimal("1180.00"));
        invoice.setNotes("{\"Customer ID\":\"9\",\"Customer GSTIN\":\"23AAAAA0000A1Z5\"," +
                "\"Customer Billing Address\":\"12 Market Road\",\"Customer Billing State\":\"Madhya Pradesh\"," +
                "\"City\":\"Bhopal\",\"Invoice Items\":\"[{\\\"itemName\\\":\\\"Consulting service\\\",\\\"itemType\\\":\\\"Service\\\",\\\"hsnSac\\\":\\\"998311\\\",\\\"quantity\\\":1,\\\"rate\\\":1000,\\\"taxRate\\\":18}]\"," +
                "\"Invoice Totals\":\"{\\\"subtotal\\\":1000,\\\"discount\\\":0,\\\"taxMode\\\":\\\"CGST_SGST\\\",\\\"grandTotal\\\":1180}\"}");
    }

    @Test
    void mapsStoredInvoiceToIrpVersionElevenPayload() {
        var payload = mapper.map(invoice);

        assertThat(payload.path("Version").asText()).isEqualTo("1.1");
        assertThat(payload.path("SellerDtls").path("Stcd").asText()).isEqualTo("23");
        assertThat(payload.path("BuyerDtls").path("Pos").asText()).isEqualTo("23");
        assertThat(payload.path("ItemList").get(0).path("IsServc").asText()).isEqualTo("Y");
        assertThat(payload.path("ValDtls").path("TotInvVal").decimalValue()).isEqualByComparingTo("1180.0");
    }

    @Test
    void rejectsMissingBuyerGstinBeforeCallingIrp() {
        invoice.setNotes(invoice.getNotes().replace("23AAAAA0000A1Z5", ""));
        customer.setReferenceNumber("");
        customer.setNotes(customer.getNotes().replace("23AAAAA0000A1Z5", ""));

        assertThatThrownBy(() -> mapper.map(invoice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Buyer GSTIN");
    }

    @Test
    void readsLegacySellerPinAndCityFromOrganizationAddress() {
        BusinessRecord organization = record("settings", "organization", "ORG-1", "IntelliaTech Solutions Pvt Ltd");
        organization.setReferenceNumber("23AAECI4774Q1Z0");
        organization.setPartyCity("Plot No. 120, Scheme No. 78, Indore, MP - 452001");
        organization.setNotes("{\"address\":\"Plot No. 120, Scheme No. 78, Indore, MP - 452001\",\"state\":\"Madhya Pradesh (23)\"}");
        when(records.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization"))
                .thenReturn(Optional.of(organization));

        var payload = mapper.map(invoice);

        assertThat(payload.path("SellerDtls").path("Pin").asInt()).isEqualTo(452001);
        assertThat(payload.path("SellerDtls").path("Loc").asText()).isEqualTo("Indore");
    }

    @Test
    void mapsCreditNoteAsCrnWithOriginalInvoiceReferenceAndExactPartialValue() {
        BusinessRecord creditNote = record("sales", "creditNotes", "CN-2026-001", "Registered Customer Pvt Ltd");
        creditNote.setId(81L);
        creditNote.setRecordDate(LocalDate.of(2026, 10, 8));
        creditNote.setAmount(new BigDecimal("590.00"));
        creditNote.setNotes("{\"Customer ID\":\"9\",\"Source Invoice ID\":\"44\","
                + "\"Customer GSTIN\":\"23AAAAA0000A1Z5\",\"Customer Billing Address\":\"12 Market Road\","
                + "\"Customer Billing State\":\"Madhya Pradesh\",\"City\":\"Bhopal\","
                + "\"Credit Note Items\":\"[{\\\"itemName\\\":\\\"Consulting service\\\",\\\"itemType\\\":\\\"Service\\\",\\\"hsnSac\\\":\\\"998311\\\",\\\"quantity\\\":1,\\\"rate\\\":500,\\\"taxRate\\\":18}]\","
                + "\"Credit Note Totals\":\"{\\\"subtotal\\\":500,\\\"discount\\\":0,\\\"taxMode\\\":\\\"CGST_SGST\\\",\\\"grandTotal\\\":590}\"}");
        when(records.findByModuleAndTypeAndId("sales", "invoices", 44L)).thenReturn(Optional.of(invoice));

        var payload = mapper.map(creditNote);

        assertThat(payload.path("DocDtls").path("Typ").asText()).isEqualTo("CRN");
        assertThat(payload.path("DocDtls").path("No").asText()).isEqualTo("CN-2026-001");
        assertThat(payload.path("RefDtls").path("PrecDocDtls").get(0).path("InvNo").asText())
                .isEqualTo("INV-2026-001");
        assertThat(payload.path("RefDtls").path("PrecDocDtls").get(0).path("InvDt").asText())
                .isEqualTo("07/10/2026");
        assertThat(payload.path("ValDtls").path("TotInvVal").decimalValue()).isEqualByComparingTo("590.00");
    }

    @Test
    void mapsFullValueCreditNoteWithoutChangingTheOriginalInvoiceDocumentType() {
        BusinessRecord creditNote = record("sales", "creditNotes", "CN-2026-002", invoice.getPartyName());
        creditNote.setRecordDate(LocalDate.of(2026, 10, 8));
        creditNote.setAmount(new BigDecimal("1180.00"));
        creditNote.setNotes(invoice.getNotes()
                .replaceFirst("\\{", "{\\\"Source Invoice ID\\\":\\\"44\\\",")
                .replace("Invoice Items", "Credit Note Items")
                .replace("Invoice Totals", "Credit Note Totals"));
        when(records.findByModuleAndTypeAndId("sales", "invoices", 44L)).thenReturn(Optional.of(invoice));

        var creditPayload = mapper.map(creditNote);
        var unchangedInvoicePayload = mapper.map(invoice);

        assertThat(creditPayload.path("DocDtls").path("Typ").asText()).isEqualTo("CRN");
        assertThat(creditPayload.path("ValDtls").path("TotInvVal").decimalValue()).isEqualByComparingTo("1180.00");
        assertThat(unchangedInvoicePayload.path("DocDtls").path("Typ").asText()).isEqualTo("INV");
    }

    private BusinessRecord record(String module, String type, String number, String name) {
        BusinessRecord record = new BusinessRecord();
        record.setModule(module);
        record.setType(type);
        record.setRecordNumber(number);
        record.setPartyName(name);
        record.setStatus("Active");
        record.setAmount(BigDecimal.ZERO);
        record.setBalanceAmount(BigDecimal.ZERO);
        record.setRecordDate(LocalDate.of(2026, 1, 1));
        return record;
    }
}
