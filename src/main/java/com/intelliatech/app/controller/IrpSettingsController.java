package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.IrpConfigurationRequest;
import com.intelliatech.app.dto.response.IrpConfigurationResponse;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.service.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/settings/irp") @RequiredArgsConstructor
public class IrpSettingsController {
 private static final Long COMPANY_ID=1L;
 private final IrpConfigurationService configurations;
 private final IrpAuthenticationService authentication;

 @GetMapping @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public List<IrpConfigurationResponse> list(){return configurations.list(COMPANY_ID);}
 @GetMapping("/{environment}") @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public IrpConfigurationResponse get(@PathVariable IrpEnvironment environment){return configurations.get(COMPANY_ID,environment);}
 @GetMapping("/active") @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public IrpConfigurationResponse active(){return configurations.get(COMPANY_ID,configurations.getActiveConfiguration(COMPANY_ID).getEnvironment());}
 @PutMapping("/{environment}") @PreAuthorize("hasRole('ADMIN')") public IrpConfigurationResponse save(@PathVariable IrpEnvironment environment,@Valid @RequestBody IrpConfigurationRequest request){return configurations.save(COMPANY_ID,environment,request);}
 @PostMapping("/{environment}/activate") @PreAuthorize("hasRole('ADMIN')") public IrpConfigurationResponse activate(@PathVariable IrpEnvironment environment){return configurations.activate(COMPANY_ID,environment);}
 @PostMapping("/{environment}/test-connection") @PreAuthorize("hasRole('ADMIN')") public IrpConfigurationResponse test(@PathVariable IrpEnvironment environment){try{authentication.refreshToken(environment);return configurations.recordConnectionTest(COMPANY_ID,environment,true);}catch(RuntimeException e){configurations.recordConnectionTest(COMPANY_ID,environment,false);throw e;}}
}
