package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.request.DocumentNumberPreferenceRequest;
import com.intelliatech.app.dto.response.DocumentNumberPreferenceResponse;
import com.intelliatech.app.entity.DocumentNumberPreference;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.DocumentNumberPreferenceRepository;
import com.intelliatech.app.service.DocumentNumberPreferenceService;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DocumentNumberPreferenceServiceImpl implements DocumentNumberPreferenceService {

    private final DocumentNumberPreferenceRepository repository;
    private final BusinessRecordRepository businessRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public DocumentNumberPreferenceResponse get(String documentType) {
        DocumentNumberPreference preference = repository.findByDocumentType(documentType).orElseGet(() -> defaultPreference(documentType));
        return toResponse(preference, nextAvailableSequence(preference));
    }

    @Override
    @Transactional
    public DocumentNumberPreferenceResponse save(String documentType, DocumentNumberPreferenceRequest request) {
        validatePreference(documentType, request);
        DocumentNumberPreference preference = lockedPreference(documentType);
        preference.setDocumentType(documentType);
        preference.setAutoGenerate(request.autoGenerate());
        preference.setPrefix(clean(request.prefix()));
        preference.setSuffix(clean(request.suffix()));
        preference.setSeparator(clean(request.separator()));
        preference.setNumberFormat(clean(request.numberFormat()));
        preference.setStartingNumber(request.startingNumber());
        preference.setNextNumber(request.nextNumber());
        preference = repository.save(preference);
        return toResponse(preference, nextAvailableSequence(preference));
    }

    @Override
    @Transactional
    public DocumentNumberPreferenceResponse consume(String documentType) {
        DocumentNumberPreference preference = lockedPreference(documentType);
        if (Boolean.TRUE.equals(preference.getAutoGenerate())) {
            preference.setNextNumber(nextAvailableSequence(preference));
            preference = repository.save(preference);
        }
        return toResponse(preference, nextAvailableSequence(preference));
    }

    @Override
    @Transactional
    public String allocateForCreate(String documentType) {
        DocumentNumberPreference preference = lockedPreference(documentType);
        long sequence = nextAvailableSequence(preference);
        String number = format(preference, sequence);
        while (businessRecordRepository.existsByRecordNumber(number)) {
            sequence++;
            number = format(preference, sequence);
        }
        preference.setNextNumber(sequence + 1);
        repository.save(preference);
        return number;
    }

    private DocumentNumberPreference lockedPreference(String documentType) {
        return repository.findByDocumentTypeForUpdate(documentType).orElseGet(() -> {
            repository.saveAndFlush(defaultPreference(documentType));
            return repository.findByDocumentTypeForUpdate(documentType).orElseThrow();
        });
    }

    private DocumentNumberPreference defaultPreference(String documentType) {
        DocumentNumberPreference preference = new DocumentNumberPreference();
        preference.setDocumentType(documentType);
        preference.setAutoGenerate(true);
        preference.setPrefix(defaultPrefix(documentType));
        preference.setSuffix("");
        preference.setSeparator("-");
        preference.setNumberFormat("customers".equals(documentType) ? "0000" : ("quotes".equals(documentType) || "orders".equals(documentType)
                || "purchaseOrders".equals(documentType) || "bills".equals(documentType)
                || "billPayments".equals(documentType)) ? "000000" : "000");
        preference.setStartingNumber(defaultNextNumber(documentType));
        preference.setNextNumber(defaultNextNumber(documentType));
        return preference;
    }

    private String defaultPrefix(String documentType) {
        if ("customers".equals(documentType)) return "CUST";
        if ("creditNotes".equals(documentType)) return "CN";
        if ("quotes".equals(documentType)) return "QUO";
        if ("orders".equals(documentType)) return "SO";
        if ("purchaseOrders".equals(documentType)) return "PO";
        if ("bills".equals(documentType)) return "BILL";
        if ("billPayments".equals(documentType)) return "PAY-BILL";
        return "INT-2026";
    }

    private Long defaultNextNumber(String documentType) {
        if ("customers".equals(documentType)) return 1L;
        if ("creditNotes".equals(documentType)) return 33L;
        if ("quotes".equals(documentType)) return 126L;
        if ("orders".equals(documentType)) return 153L;
        if ("purchaseOrders".equals(documentType)) return 129L;
        return 88L;
    }

    private DocumentNumberPreferenceResponse toResponse(DocumentNumberPreference preference, long effectiveNextNumber) {
        return new DocumentNumberPreferenceResponse(
                preference.getId(),
                preference.getDocumentType(),
                preference.getAutoGenerate(),
                preference.getPrefix(),
                preference.getSuffix(),
                preference.getSeparator(),
                preference.getNumberFormat(),
                preference.getStartingNumber(),
                effectiveNextNumber,
                format(preference, effectiveNextNumber)
        );
    }

    private long nextAvailableSequence(DocumentNumberPreference preference) {
        long next = Math.max(preference.getStartingNumber(), preference.getNextNumber());
        DocumentLocation location = documentLocation(preference.getDocumentType());
        List<String> recordNumbers = businessRecordRepository.findRecordNumbersByModuleAndType(location.module(), location.type());
        Pattern pattern = Pattern.compile(
                "^" + Pattern.quote(numberPrefix(preference)) + "(\\d+)" + Pattern.quote(numberSuffix(preference)) + "$"
        );
        for (String recordNumber : recordNumbers) {
            Matcher matcher = pattern.matcher(recordNumber == null ? "" : recordNumber);
            if (!matcher.matches()) continue;
            try {
                next = Math.max(next, Long.parseLong(matcher.group(1)) + 1);
            } catch (NumberFormatException ignored) {
                // Ignore historical values that are outside the supported numeric range.
            }
        }
        return next;
    }

    private String format(DocumentNumberPreference preference, long sequence) {
        String numeric = String.valueOf(sequence);
        int width = Math.max(paddingWidth(preference.getNumberFormat()), numeric.length());
        String padded = numeric.length() >= width ? numeric : "0".repeat(width - numeric.length()) + numeric;
        return numberPrefix(preference) + padded + numberSuffix(preference);
    }

    private int paddingWidth(String format) {
        long tokens = clean(format).chars().filter(character -> character == '0' || character == '#').count();
        return (int) Math.max(tokens, 1);
    }

    private String numberPrefix(DocumentNumberPreference preference) {
        String prefix = clean(preference.getPrefix());
        String separator = clean(preference.getSeparator());
        if (!StringUtils.hasText(prefix) || !StringUtils.hasText(separator) || prefix.endsWith(separator)) return prefix;
        return prefix + separator;
    }

    private String numberSuffix(DocumentNumberPreference preference) {
        String suffix = clean(preference.getSuffix());
        String separator = clean(preference.getSeparator());
        if (!StringUtils.hasText(suffix) || !StringUtils.hasText(separator) || suffix.startsWith(separator)) return suffix;
        return separator + suffix;
    }

    private DocumentLocation documentLocation(String documentType) {
        if ("purchaseOrders".equals(documentType)) return new DocumentLocation("purchases", "orders");
        if ("bills".equals(documentType)) return new DocumentLocation("purchases", "bills");
        if ("billPayments".equals(documentType)) return new DocumentLocation("purchases", "payments");
        return new DocumentLocation("sales", documentType);
    }

    private void validatePreference(String documentType, DocumentNumberPreferenceRequest request) {
        if (!"customers".equals(documentType)) return;
        String prefix = clean(request.prefix());
        if (!Boolean.TRUE.equals(request.autoGenerate())) {
            throw new IllegalArgumentException("Customer Codes must be generated automatically.");
        }
        if (!prefix.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,23}")) {
            throw new IllegalArgumentException("Customer Code prefix must be 1 to 24 letters, numbers, underscores or dashes.");
        }
        if (StringUtils.hasText(request.suffix())) {
            throw new IllegalArgumentException("Customer Code suffix is not supported.");
        }
        if (!Set.of("000", "0000", "00000", "000000").contains(clean(request.numberFormat()))) {
            throw new IllegalArgumentException("Customer Code padding must be between 3 and 6 digits.");
        }
        if (request.nextNumber() < request.startingNumber()) {
            throw new IllegalArgumentException("Next Customer Code number cannot be lower than the Starting Number.");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record DocumentLocation(String module, String type) {
    }
}
