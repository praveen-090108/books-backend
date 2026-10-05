package com.intelliatech.app.controller;
import com.intelliatech.app.service.FixedCostProjectTeamService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/projects/fixed-cost/{projectId}/team") @RequiredArgsConstructor
public class FixedCostProjectTeamController{
 private final FixedCostProjectTeamService service;
 @GetMapping @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')") public List<FixedCostProjectTeamService.Assignment> list(@PathVariable Long projectId){return service.list(projectId);}
 @GetMapping("/{id}") @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')") public FixedCostProjectTeamService.Assignment get(@PathVariable Long projectId,@PathVariable Long id){return service.get(projectId,id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@permissionGuard.can('projects','fixedCost','EDIT')") public FixedCostProjectTeamService.Assignment add(@PathVariable Long projectId,@RequestBody FixedCostProjectTeamService.AddRequest request){return service.add(projectId,request);}
 @PatchMapping("/{id}/remove") @PreAuthorize("@permissionGuard.can('projects','fixedCost','EDIT')") public FixedCostProjectTeamService.Assignment remove(@PathVariable Long projectId,@PathVariable Long id,@RequestBody FixedCostProjectTeamService.RemoveRequest request){return service.remove(projectId,id,request);}
}
