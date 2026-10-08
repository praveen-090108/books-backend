package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intelliatech.app.service.IrpConfigurationService;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InvoiceToEInvoiceMapper {

    private static final DateTimeFormatter IRP_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern STATE_CODE = Pattern.compile("(?:^|\\D)(\\d{2})(?:\\D|$)");
    private static final Pattern INDIAN_PIN = Pattern.compile("(?<!\\d)([1-9]\\d{5})(?!\\d)");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_IRP_JSON_BYTES = 2 * 1024 * 1024;
    private static final Map<String, String> INDIAN_STATE_CODES = Map.ofEntries(
            Map.entry("JAMMU AND KASHMIR", "01"), Map.entry("HIMACHAL PRADESH", "02"),
            Map.entry("PUNJAB", "03"), Map.entry("CHANDIGARH", "04"),
            Map.entry("UTTARAKHAND", "05"), Map.entry("HARYANA", "06"), Map.entry("DELHI", "07"),
            Map.entry("RAJASTHAN", "08"), Map.entry("UTTAR PRADESH", "09"), Map.entry("BIHAR", "10"),
            Map.entry("SIKKIM", "11"), Map.entry("ARUNACHAL PRADESH", "12"), Map.entry("NAGALAND", "13"),
            Map.entry("MANIPUR", "14"), Map.entry("MIZORAM", "15"), Map.entry("TRIPURA", "16"),
            Map.entry("MEGHALAYA", "17"), Map.entry("ASSAM", "18"), Map.entry("WEST BENGAL", "19"),
            Map.entry("JHARKHAND", "20"), Map.entry("ODISHA", "21"), Map.entry("CHHATTISGARH", "22"),
            Map.entry("MADHYA PRADESH", "23"), Map.entry("GUJARAT", "24"),
            Map.entry("DADRA AND NAGAR HAVELI AND DAMAN AND DIU", "26"),
            Map.entry("MAHARASHTRA", "27"), Map.entry("KARNATAKA", "29"), Map.entry("GOA", "30"),
            Map.entry("LAKSHADWEEP", "31"), Map.entry("KERALA", "32"), Map.entry("TAMIL NADU", "33"),
            Map.entry("PUDUCHERRY", "34"), Map.entry("ANDAMAN AND NICOBAR ISLANDS", "35"),
            Map.entry("TELANGANA", "36"), Map.entry("ANDHRA PRADESH", "37"), Map.entry("LADAKH", "38"),
            Map.entry("OTHER TERRITORY", "97"), Map.entry("CENTRE JURISDICTION", "99")
    );

    private final ObjectMapper objectMapper;
    private final BusinessRecordRepository records;
    private final IrpConfigurationService configurations;

    public InvoiceToEInvoiceMapper(ObjectMapper objectMapper, BusinessRecordRepository records, IrpConfigurationService configurations) {
        this.objectMapper = objectMapper;
        this.records = records;
        this.configurations = configurations;
    }

    public JsonNode map(BusinessRecord invoice) {
        boolean creditNote = "creditNotes".equals(invoice.getType());
        ObjectNode invoiceNotes = object(invoice.getNotes());
        ObjectNode totals = object(firstText(invoiceNotes,
                creditNote ? "Credit Note Totals" : "Invoice Totals", "totals"));
        ArrayNode storedItems = array(firstText(invoiceNotes,
                creditNote ? "Credit Note Items" : "Invoice Items", "items"));
        BusinessRecord organization = records.findFirstByModuleAndTypeOrderByRecordDateDesc("settings", "organization")
                .orElseThrow(() -> new IllegalArgumentException("E-Invoice validation failed: Organization profile is missing"));
        ObjectNode organizationNotes = object(organization.getNotes());
        BusinessRecord customer = customer(invoiceNotes);
        ObjectNode customerNotes = customer == null ? objectMapper.createObjectNode() : object(customer.getNotes());

        List<String> errors = new ArrayList<>();
        String supplierGstin = normalizeGstin(configurations.getActiveCredentials(1L).gstin());
        String organizationGstin = normalizeGstin(firstNonBlank(organization.getReferenceNumber(),
                text(organizationNotes, "gstin", "GSTIN")));
        if (!StringUtils.hasText(supplierGstin)) errors.add("IRP GSTIN is not configured in IRN / E-Invoice Settings");
        if (StringUtils.hasText(organizationGstin) && !organizationGstin.equals(supplierGstin)) {
            errors.add("Organization GSTIN does not match the configured IRP GSTIN");
        }

        String sellerAddress = text(organizationNotes, "address", "addressLine1", "Address Line 1");
        String sellerCity = firstNonBlank(text(organizationNotes, "city", "City"),
                cityFromAddress(sellerAddress), organization.getPartyCity());
        String sellerPin = pinFrom(firstNonBlank(
                text(organizationNotes, "pinCode", "pincode", "postalCode", "PIN Code"),
                sellerAddress, organization.getPartyCity()));
        String sellerState = stateCode(firstNonBlank(text(organizationNotes, "state", "State"), supplierGstin));

        String buyerGstin = normalizeGstin(firstNonBlank(
                text(invoiceNotes, "Customer GSTIN", "customerGstin", "gstin"),
                customer == null ? null : customer.getReferenceNumber(), text(customerNotes, "gstin", "GSTIN")));
        String buyerAddress = firstNonBlank(text(invoiceNotes, "Customer Billing Address", "billingAddress"),
                text(customerNotes, "billingAddress", "addressLine1", "Address Line 1"));
        String buyerCity = firstNonBlank(text(invoiceNotes, "City", "customerCity"),
                text(customerNotes, "city", "City"), customer == null ? null : customer.getPartyCity(), invoice.getPartyCity());
        String buyerPin = digits(firstNonBlank(text(invoiceNotes, "Customer PIN Code", "pinCode"),
                text(customerNotes, "pinCode", "pincode", "zipCode", "PIN Code")));
        String buyerStateText = firstNonBlank(text(invoiceNotes, "Customer Billing State", "Place Of Supply", "placeOfSupply"),
                text(customerNotes, "state", "placeOfSupply"), buyerGstin);
        String buyerState = stateCode(buyerStateText);
        String placeOfSupply = stateCode(firstNonBlank(text(invoiceNotes, "Place Of Supply", "placeOfSupply"), buyerStateText));

        gstin(errors, "Seller GSTIN", supplierGstin);
        required(errors, "Seller legal name", organization.getPartyName());
        required(errors, "Seller address", sellerAddress);
        required(errors, "Seller city", sellerCity);
        pin(errors, "Seller PIN", sellerPin);
        state(errors, "Seller state", sellerState);
        gstin(errors, "Buyer GSTIN", buyerGstin);
        required(errors, "Buyer legal name", invoice.getPartyName());
        required(errors, "Buyer billing address", buyerAddress);
        required(errors, "Buyer city", buyerCity);
        pin(errors, "Buyer PIN", buyerPin);
        state(errors, "Buyer state", buyerState);
        state(errors, "Place of Supply", placeOfSupply);
        if (!StringUtils.hasText(invoice.getRecordNumber()) || invoice.getRecordNumber().length() > 16
                || invoice.getRecordNumber().matches("^[0/-].*")) {
            errors.add((creditNote ? "Credit Note" : "Invoice") + " number must be 1-16 characters and cannot start with 0, / or -");
        }
        if (invoice.getRecordDate() == null) errors.add((creditNote ? "Credit Note" : "Invoice") + " date is required");
        else if (invoice.getRecordDate().isBefore(LocalDate.of(2021, 10, 1))) {
            errors.add((creditNote ? "Credit Note" : "Invoice") + " date must be on or after 01 Oct 2021 for IRP 5");
        }
        if (storedItems.isEmpty()) errors.add("At least one " + (creditNote ? "credit note" : "invoice") + " item is required");
        if (storedItems.size() > 1000) errors.add("IRP supports a maximum of 1000 items");

        BusinessRecord sourceInvoice = creditNote ? sourceInvoice(invoiceNotes) : null;
        if (creditNote && sourceInvoice == null) errors.add("Original invoice reference is required for a Credit Note");
        if (creditNote && sourceInvoice != null) {
            if (!invoice.getPartyName().equalsIgnoreCase(sourceInvoice.getPartyName())) {
                errors.add("Credit Note customer must match the original invoice customer");
            }
            if (invoice.getAmount() != null && sourceInvoice.getAmount() != null
                    && money(invoice.getAmount()).compareTo(money(sourceInvoice.getAmount())) > 0) {
                errors.add("Credit Note total cannot exceed the original invoice total");
            }
        }

        BigDecimal discount = decimal(totals, "discount");
        BigDecimal rawSubtotal = BigDecimal.ZERO;
        for (JsonNode item : storedItems) {
            rawSubtotal = rawSubtotal.add(amount(decimal(item, "quantity"), decimal(item, "rate")));
        }
        String taxMode = text(totals, "taxMode").toUpperCase(Locale.ROOT);
        boolean intraState = "CGST_SGST".equals(taxMode);
        if (StringUtils.hasText(sellerState) && StringUtils.hasText(placeOfSupply)
                && (sellerState.equals(placeOfSupply) != intraState)) {
            errors.add("Invoice GST mode does not match Seller State and Place of Supply");
        }

        ArrayNode itemList = objectMapper.createArrayNode();
        BigDecimal assessableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;
        for (int index = 0; index < storedItems.size(); index++) {
            JsonNode source = storedItems.get(index);
            int line = index + 1;
            String name = firstNonBlank(source.path("itemName").asText(), source.path("description").asText());
            String hsn = digits(firstNonBlank(source.path("hsnSac").asText(), source.path("hsnCode").asText()));
            String type = source.path("itemType").asText();
            boolean service = type.toLowerCase(Locale.ROOT).contains("service") || hsn.startsWith("99");
            BigDecimal qty = decimal(source, "quantity");
            BigDecimal rate = decimal(source, "rate");
            BigDecimal gross = amount(qty, rate);
            BigDecimal allocatedDiscount = rawSubtotal.signum() == 0 ? BigDecimal.ZERO
                    : discount.multiply(gross).divide(rawSubtotal, 2, RoundingMode.HALF_UP);
            BigDecimal assessable = money(gross.subtract(allocatedDiscount));
            BigDecimal gstRate = decimal(source, "taxRate");
            BigDecimal tax = money(assessable.multiply(gstRate).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            BigDecimal cgst = intraState ? money(tax.divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP)) : BigDecimal.ZERO;
            BigDecimal sgst = intraState ? tax.subtract(cgst) : BigDecimal.ZERO;
            BigDecimal igst = intraState ? BigDecimal.ZERO : tax;
            String unit = normalizeUnit(source.path("unit").asText());

            if (!StringUtils.hasText(name)) errors.add("Item " + line + ": name/description is required");
            if (hsn.length() < 4 || hsn.length() > 8) errors.add("Item " + line + ": HSN/SAC must contain 4-8 digits");
            if (rate.signum() < 0) errors.add("Item " + line + ": unit price cannot be negative");
            if (!service && qty.signum() <= 0) errors.add("Item " + line + ": quantity is required for goods");
            if (!service && !StringUtils.hasText(unit)) errors.add("Item " + line + ": a valid UQC unit is required for goods");

            ObjectNode item = itemList.addObject();
            item.put("SlNo", String.valueOf(line));
            item.put("PrdDesc", truncate(name, 300));
            item.put("IsServc", service ? "Y" : "N");
            item.put("HsnCd", hsn);
            if (qty.signum() > 0) item.put("Qty", number(qty));
            if (StringUtils.hasText(unit)) item.put("Unit", unit);
            item.put("UnitPrice", number(rate));
            item.put("TotAmt", number(gross));
            if (allocatedDiscount.signum() > 0) item.put("Discount", number(allocatedDiscount));
            item.put("AssAmt", number(assessable));
            item.put("GstRt", number(gstRate));
            if (cgst.signum() > 0) item.put("CgstAmt", number(cgst));
            if (sgst.signum() > 0) item.put("SgstAmt", number(sgst));
            if (igst.signum() > 0) item.put("IgstAmt", number(igst));
            item.put("TotItemVal", number(assessable.add(cgst).add(sgst).add(igst)));
            assessableTotal = assessableTotal.add(assessable);
            cgstTotal = cgstTotal.add(cgst);
            sgstTotal = sgstTotal.add(sgst);
            igstTotal = igstTotal.add(igst);
        }
        if (!errors.isEmpty()) throw validation(errors);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("Version", "1.1");
        ObjectNode transaction = root.putObject("TranDtls");
        transaction.put("TaxSch", "GST");
        transaction.put("SupTyp", "B2B");
        transaction.put("RegRev", "N");
        transaction.put("IgstOnIntra", "N");
        ObjectNode document = root.putObject("DocDtls");
        document.put("Typ", creditNote ? "CRN" : "INV");
        document.put("No", invoice.getRecordNumber());
        document.put("Dt", invoice.getRecordDate().format(IRP_DATE));
        ObjectNode seller = root.putObject("SellerDtls");
        seller.put("Gstin", supplierGstin);
        seller.put("LglNm", truncate(organization.getPartyName(), 100));
        seller.put("Addr1", truncate(sellerAddress, 100));
        seller.put("Loc", truncate(sellerCity, 50));
        seller.put("Pin", Integer.parseInt(sellerPin));
        seller.put("Stcd", sellerState);
        ObjectNode buyer = root.putObject("BuyerDtls");
        buyer.put("Gstin", buyerGstin);
        buyer.put("LglNm", truncate(invoice.getPartyName(), 100));
        buyer.put("Pos", placeOfSupply);
        buyer.put("Addr1", truncate(buyerAddress, 100));
        buyer.put("Loc", truncate(buyerCity, 50));
        buyer.put("Pin", Integer.parseInt(buyerPin));
        buyer.put("Stcd", buyerState);
        root.set("ItemList", itemList);
        if (creditNote && sourceInvoice != null) {
            ObjectNode references = root.putObject("RefDtls");
            ObjectNode previousDocument = references.putArray("PrecDocDtls").addObject();
            previousDocument.put("InvNo", sourceInvoice.getRecordNumber());
            previousDocument.put("InvDt", sourceInvoice.getRecordDate().format(IRP_DATE));
        }
        BigDecimal roundOff = decimal(totals, "roundOff");
        BigDecimal calculatedTotal = money(assessableTotal.add(cgstTotal).add(sgstTotal).add(igstTotal).add(roundOff));
        ObjectNode values = root.putObject("ValDtls");
        values.put("AssVal", number(assessableTotal));
        values.put("CgstVal", number(cgstTotal));
        values.put("SgstVal", number(sgstTotal));
        values.put("IgstVal", number(igstTotal));
        values.put("CesVal", 0);
        values.put("StCesVal", 0);
        if (roundOff.signum() != 0) values.put("RndOffAmt", number(roundOff));
        values.put("TotInvVal", number(calculatedTotal));
        BigDecimal persistedTotal = firstPositive(decimal(totals, "grandTotal"), invoice.getAmount());
        if (persistedTotal != null && persistedTotal.subtract(calculatedTotal).abs().compareTo(new BigDecimal("1.00")) > 0) {
            throw validation(List.of((creditNote ? "Credit Note" : "Invoice") + " total does not reconcile with the IRP item/tax total"));
        }
        try {
            if (objectMapper.writeValueAsBytes(root).length > MAX_IRP_JSON_BYTES) {
                throw validation(List.of("Generate IRN JSON exceeds the IRP 2 MB limit"));
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize the Generate IRN payload");
        }
        return root;
    }

    private BusinessRecord customer(ObjectNode notes) {
        String value = text(notes, "Customer ID", "customerId");
        if (!StringUtils.hasText(value)) return null;
        try {
            return records.findByModuleAndTypeAndId("sales", "customers", Long.valueOf(value)).orElse(null);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BusinessRecord sourceInvoice(ObjectNode notes) {
        String value = text(notes, "Source Invoice ID", "convertedFromId");
        if (StringUtils.hasText(value)) {
            try {
                return records.findByModuleAndTypeAndId("sales", "invoices", Long.valueOf(value)).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        String invoiceNumber = text(notes, "Reference Invoice", "sourceInvoiceNumber");
        if (!StringUtils.hasText(invoiceNumber)) return null;
        return records.findByRecordNumber(invoiceNumber)
                .filter(record -> "sales".equals(record.getModule()) && "invoices".equals(record.getType()))
                .orElse(null);
    }

    private ObjectNode object(String value) {
        try {
            JsonNode node = StringUtils.hasText(value) ? objectMapper.readTree(value) : null;
            return node != null && node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private ArrayNode array(String value) {
        try {
            JsonNode node = StringUtils.hasText(value) ? objectMapper.readTree(value) : null;
            return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
        } catch (Exception ignored) {
            return objectMapper.createArrayNode();
        }
    }

    private String firstText(ObjectNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) return value.isTextual() ? value.asText() : value.toString();
        }
        return "";
    }

    private String text(JsonNode node, String... keys) {
        if (node == null) return "";
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) return value.asText().trim();
        }
        return "";
    }

    private BigDecimal decimal(JsonNode node, String key) {
        if (node == null || node.path(key).isMissingNode() || node.path(key).isNull()) return BigDecimal.ZERO;
        try { return new BigDecimal(node.path(key).asText("0")); } catch (Exception ignored) { return BigDecimal.ZERO; }
    }

    private BigDecimal amount(BigDecimal qty, BigDecimal rate) { return money(qty.multiply(rate)); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private double number(BigDecimal value) { return money(value).doubleValue(); }
    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        if (first != null && first.signum() > 0) return first;
        return second != null && second.signum() > 0 ? second : null;
    }
    private String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private String pinFrom(String value) {
        if (!StringUtils.hasText(value)) return "";
        Matcher matcher = INDIAN_PIN.matcher(value);
        return matcher.find() ? matcher.group(1) : digits(value);
    }
    private String cityFromAddress(String value) {
        if (!StringUtils.hasText(value)) return "";
        String[] parts = value.split(",");
        for (int index = parts.length - 1; index >= 0; index--) {
            String part = parts[index].replaceAll("(?i)\\b(?:PIN)?\\s*-?\\s*[1-9]\\d{5}\\b", "").trim();
            if (part.isEmpty() || stateCode(part).matches("\\d{2}") || part.matches("(?i)^(MP|MH|TN|KA|DL)$")) continue;
            if (part.matches(".*[A-Za-z].*")) return part;
        }
        return "";
    }
    private String normalizeGstin(String value) { return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT); }
    private String stateCode(String value) {
        if (!StringUtils.hasText(value)) return "";
        String normalized = value.trim();
        if (normalized.matches("^\\d{2}.*")) return normalized.substring(0, 2);
        Matcher matcher = STATE_CODE.matcher(normalized);
        if (matcher.find()) return matcher.group(1);
        return INDIAN_STATE_CODES.getOrDefault(normalized.toUpperCase(Locale.ROOT), "");
    }
    private String firstNonBlank(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "";
    }
    private String truncate(String value, int length) { return value.length() <= length ? value : value.substring(0, length); }
    private void required(List<String> errors, String name, String value) { if (!StringUtils.hasText(value)) errors.add(name + " is required"); }
    private void pin(List<String> errors, String name, String value) { if (!value.matches("\\d{6}")) errors.add(name + " must be a 6-digit PIN code"); }
    private void state(List<String> errors, String name, String value) { if (!value.matches("\\d{2}")) errors.add(name + " must include a valid 2-digit GST state code"); }
    private void gstin(List<String> errors, String name, String value) { if (!value.matches("\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z0-9]Z[A-Z0-9]")) errors.add(name + " is invalid or missing"); }
    private IllegalArgumentException validation(List<String> errors) { return new IllegalArgumentException("E-Invoice validation failed: " + String.join("; ", errors)); }

    private String normalizeUnit(String value) {
        if (!StringUtils.hasText(value)) return "";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PCS", "PIECE", "PIECES", "NOS", "NO", "NUMBER" -> "NOS";
            case "KG", "KGS", "KILOGRAM" -> "KGS";
            case "HOUR", "HOURS", "HRS" -> "HRS";
            case "DAY", "DAYS" -> "DAY";
            case "MONTH", "MONTHS" -> "MON";
            case "UNIT", "UNITS" -> "UNT";
            default -> value.trim().toUpperCase(Locale.ROOT).matches("[A-Z]{3}") ? value.trim().toUpperCase(Locale.ROOT) : "";
        };
    }
}
