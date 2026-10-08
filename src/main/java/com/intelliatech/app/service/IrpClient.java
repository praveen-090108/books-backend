package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.intelliatech.app.dto.response.IrpGenerateResult;
import com.intelliatech.app.entity.IrpEnvironment;

public interface IrpClient {
    IrpGenerateResult generateIrn(JsonNode invoicePayload);
    IrpGenerateResult cancelIrn(String irn, String reasonCode, String remarks);
    IrpGenerateResult cancelIrn(String irn, String reasonCode, String remarks, IrpEnvironment environment);
    IrpGenerateResult getIrn(String irn);
    IrpGenerateResult getIrn(String irn, IrpEnvironment environment);
}
