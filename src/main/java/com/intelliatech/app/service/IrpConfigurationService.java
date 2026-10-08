package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.IrpConfigurationRequest;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.entity.*;
import java.util.List;

public interface IrpConfigurationService {
 List<IrpConfigurationResponse> list(Long companyId);
 IrpConfigurationResponse get(Long companyId,IrpEnvironment environment);
 IrpConfigurationResponse save(Long companyId,IrpEnvironment environment,IrpConfigurationRequest request);
 IrpConfigurationResponse activate(Long companyId,IrpEnvironment environment);
 IrpConfigurationResponse testConnection(Long companyId,IrpEnvironment environment);
 IrpConfigurationResponse recordConnectionTest(Long companyId,IrpEnvironment environment,boolean success);
 IrpConfiguration getActiveConfiguration(Long companyId);
 IrpConfiguration getConfiguration(Long companyId,IrpEnvironment environment);
 IrpCredentials getActiveCredentials(Long companyId);
 IrpCredentials getDecryptedCredentials(Long companyId,IrpEnvironment environment);
}
