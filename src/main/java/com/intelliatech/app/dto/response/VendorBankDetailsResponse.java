package com.intelliatech.app.dto.response;

public record VendorBankDetailsResponse(
        String accountHolderName,
        String beneficiaryName,
        String bankName,
        String maskedAccountNumber,
        String ifscCode,
        String branchName,
        String accountType,
        String swiftCode,
        String iban,
        String bankCountry,
        String bankAddress,
        String upiId,
        String notes
) {
}
