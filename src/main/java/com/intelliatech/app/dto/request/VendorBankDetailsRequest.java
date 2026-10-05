package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VendorBankDetailsRequest(
        @Size(max = 160) String accountHolderName,
        @Size(max = 160) String beneficiaryName,
        @Size(max = 160) String bankName,
        @Size(max = 80) String accountNumber,
        @Size(max = 80) String confirmAccountNumber,
        @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code") String ifscCode,
        @Size(max = 160) String branchName,
        @Pattern(regexp = "^$|^(Savings|Current)$", message = "Account type must be Savings or Current") String accountType,
        @Pattern(regexp = "^$|^[A-Z0-9]{8}([A-Z0-9]{3})?$", message = "Enter a valid SWIFT code") String swiftCode,
        @Pattern(regexp = "^$|^[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}$", message = "Enter a valid IBAN") String iban,
        @Size(max = 120) String bankCountry,
        @Size(max = 500) String bankAddress,
        @Pattern(regexp = "^$|^[A-Za-z0-9._-]{2,256}@[A-Za-z]{2,64}$", message = "Enter a valid UPI ID") String upiId,
        @Size(max = 1000) String notes
) {
}
