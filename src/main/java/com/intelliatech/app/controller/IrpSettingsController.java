package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.IrpConfigurationRequest;
import com.intelliatech.app.dto.response.IrpConfigurationResponse;
import com.intelliatech.app.entity.IrpEnvironment;
import com.intelliatech.app.service.*;
import com.intelliatech.app.security.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/settings/irp") @RequiredArgsConstructor
public class IrpSettingsController {
 private final IrpConfigurationService configurations;
 private final IrpAuthenticationService authentication;
 private final TenantContext tenants;

 @GetMapping @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public List<IrpConfigurationResponse> list(){return configurations.list(tenants.requireOrganizationId());}
 @GetMapping("/{environment}") @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public IrpConfigurationResponse get(@PathVariable IrpEnvironment environment){return configurations.get(tenants.requireOrganizationId(),environment);}
 @GetMapping("/active") @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')") public IrpConfigurationResponse active(){Long id=tenants.requireOrganizationId();return configurations.get(id,configurations.getActiveConfiguration(id).getEnvironment());}
 @PutMapping("/{environment}") @PreAuthorize("hasRole('ADMIN')") public IrpConfigurationResponse save(@PathVariable IrpEnvironment environment,@Valid @RequestBody IrpConfigurationRequest request){return configurations.save(tenants.requireOrganizationId(),environment,request);}
 @PostMapping("/{environment}/activate") @PreAuthorize("hasRole('ADMIN')") public IrpConfigurationResponse activate(@PathVariable IrpEnvironment environment){return configurations.activate(tenants.requireOrganizationId(),environment);}
 @PostMapping("/{environment}/test-connection") @PreAuthorize("hasRole('ADMIN')") public IrpConfigurationResponse test(@PathVariable IrpEnvironment environment){Long id=tenants.requireOrganizationId();try{authentication.refreshToken(environment);return configurations.recordConnectionTest(id,environment,true);}catch(RuntimeException e){configurations.recordConnectionTest(id,environment,false);throw e;}}
}
