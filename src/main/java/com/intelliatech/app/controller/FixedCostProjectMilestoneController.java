package com.intelliatech.app.controller;

import com.intelliatech.app.service.FixedCostProjectMilestoneService;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/projects/fixed-cost/{projectId}/milestones") @RequiredArgsConstructor
public class FixedCostProjectMilestoneController {
 private final FixedCostProjectMilestoneService service;
 @GetMapping @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')") public FixedCostProjectMilestoneService.Summary list(@PathVariable Long projectId){return service.list(projectId);}
 @GetMapping("/{id}") @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')") public FixedCostProjectMilestoneService.Response get(@PathVariable Long projectId,@PathVariable Long id){return service.get(projectId,id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@permissionGuard.can('projects','fixedCost','EDIT')") public FixedCostProjectMilestoneService.Response create(@PathVariable Long projectId,@RequestBody FixedCostProjectMilestoneService.Request request){return service.create(projectId,request);}
 @PutMapping("/{id}") @PreAuthorize("@permissionGuard.can('projects','fixedCost','EDIT')") public FixedCostProjectMilestoneService.Response update(@PathVariable Long projectId,@PathVariable Long id,@RequestBody FixedCostProjectMilestoneService.Request request){return service.update(projectId,id,request);}
 @PatchMapping("/{id}/status") @PreAuthorize("@permissionGuard.can('projects','fixedCost','EDIT')") public FixedCostProjectMilestoneService.Response status(@PathVariable Long projectId,@PathVariable Long id,@RequestBody Map<String,Object> body){return service.status(projectId,id,String.valueOf(body.get("status")),body.get("progress")==null?null:new BigDecimal(String.valueOf(body.get("progress"))));}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("@permissionGuard.can('projects','fixedCost','DELETE')") public void delete(@PathVariable Long projectId,@PathVariable Long id){service.delete(projectId,id);}
}
