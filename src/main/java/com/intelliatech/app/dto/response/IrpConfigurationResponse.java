package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.IrpEnvironment;
import java.time.LocalDateTime;

public record IrpConfigurationResponse(Long id,String provider,IrpEnvironment environment,String apiBaseUrl,
 String gstin,String apiVersion,boolean active,boolean configured,boolean clientIdConfigured,
 boolean clientSecretConfigured,boolean apiUsernameConfigured,boolean apiPasswordConfigured,
 LocalDateTime lastConnectionTest,String lastConnectionStatus,LocalDateTime updatedAt) {}
