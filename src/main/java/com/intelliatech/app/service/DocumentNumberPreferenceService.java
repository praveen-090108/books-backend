package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.DocumentNumberPreferenceRequest;
import com.intelliatech.app.dto.response.DocumentNumberPreferenceResponse;

public interface DocumentNumberPreferenceService {

    DocumentNumberPreferenceResponse get(String documentType);

    DocumentNumberPreferenceResponse save(String documentType, DocumentNumberPreferenceRequest request);

    DocumentNumberPreferenceResponse consume(String documentType);

    String allocateForCreate(String documentType);
}
