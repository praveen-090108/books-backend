package com.intelliatech.app.einvoice.qualification.controller;
import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import com.intelliatech.app.einvoice.qualification.runner.SandboxQualificationRunner;
import com.intelliatech.app.einvoice.qualification.service.QualificationProgressService;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/einvoice/qualification") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class QualificationController {
 private final SandboxQualificationRunner runner; private final QualificationProgressService progress;
 @PostMapping("/runs") public RunSummary run(@RequestParam(defaultValue="ALL") Suite suite){return runner.run(suite);}
 @PostMapping("/runs/scenario/{id}") public RunSummary one(@PathVariable String id){return runner.runOne(id);}
 @PostMapping("/runs/{id}/stop") public void stop(@PathVariable long id){runner.stop(id);}
 @GetMapping("/runs/{id}/progress") public Progress progress(@PathVariable long id){return progress.progress(id);}
 @PutMapping("/portal-progress/{api}") public void portal(@PathVariable Api api,@Valid @RequestBody PortalUpdate r,Authentication a){progress.updatePortal(api,r.success(),r.failure(),r.source(),a.getName());}
 public record PortalUpdate(@Min(0) int success,@Min(0) int failure,String source){}
}
