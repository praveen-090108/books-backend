package com.intelliatech.app.controller;

import com.intelliatech.app.service.LeadManagementService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LeadManagementController {
    private final LeadManagementService service;

    public LeadManagementController(LeadManagementService service) {
        this.service = service;
    }

    @GetMapping("/lead-options") public Map<String,Object> options(){ return service.options(); }
    @GetMapping("/lead-dashboard") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_DASHBOARD_VIEW')") public Map<String,Object> dashboard(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long pipelineId,
            @RequestParam(required=false) String owner,
            @RequestParam(required=false) String source,
            @RequestParam(required=false) String status){ return service.dashboard(from,to,pipelineId,owner,source,status); }

    @GetMapping("/lead-reports/{reportType}") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_REPORTS_VIEW')") public Map<String,Object> report(@PathVariable String reportType,@RequestParam Map<String,String> filters){return service.leadReport(reportType,filters);}
    @GetMapping(value="/lead-reports/{reportType}/export",produces="text/csv") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_REPORTS_DOWNLOAD')") public ResponseEntity<String> reportExport(@PathVariable String reportType,@RequestParam Map<String,String> filters){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=lead-report-"+reportType+".csv").contentType(MediaType.parseMediaType("text/csv")).body(service.leadReportCsv(reportType,filters));}

    @GetMapping("/leads") public Map<String,Object> leads(@RequestParam Map<String,String> filters,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){ return service.leads(filters,page,size); }
    @PostMapping("/leads") public Map<String,Object> createLead(@RequestBody Map<String,Object> body,@RequestParam(defaultValue="false") boolean draft){ return service.saveLead(null,body,draft); }
    @GetMapping("/leads/{id}") public Map<String,Object> lead(@PathVariable long id){ return service.lead(id); }
    @GetMapping("/leads/{id}/timeline") public Map<String,Object> leadTimeline(@PathVariable long id,@RequestParam(required=false) String type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){ return service.leadTimeline(id,type,page,size); }
    @GetMapping("/leads/{id}/activities") public Map<String,Object> leadActivities(@PathVariable long id,@RequestParam(required=false) String type){ return service.leadActivities(id,type); }
    @GetMapping("/leads/{id}/tasks") public Map<String,Object> leadTasks(@PathVariable long id){ return service.leadTasks(id); }
    @GetMapping("/leads/{id}/events") public Map<String,Object> leadEvents(@PathVariable long id){ return service.leadActivities(id,"EVENT"); }
    @GetMapping("/leads/{id}/calls") public Map<String,Object> leadCalls(@PathVariable long id){ return service.leadActivities(id,"CALL"); }
    @GetMapping("/leads/{id}/stage-history") public Map<String,Object> leadStageHistory(@PathVariable long id){ return service.leadStageHistory(id); }
    @PutMapping("/leads/{id}") public Map<String,Object> updateLead(@PathVariable long id,@RequestBody Map<String,Object> body,@RequestParam(defaultValue="false") boolean draft){ return service.saveLead(id,body,draft); }
    @DeleteMapping("/leads/{id}") public void deleteLead(@PathVariable long id){ service.deleteLead(id); }
    @PostMapping("/leads/{id}/change-stage") public Map<String,Object> changeStage(@PathVariable long id,@RequestBody Map<String,Object> body){ return service.changeStage(id,body); }
    @PostMapping("/leads/{id}/change-owner") public Map<String,Object> changeOwner(@PathVariable long id,@RequestBody Map<String,String> body){ return service.changeOwner(id,body.get("ownerId")); }
    @PostMapping("/leads/{id}/convert") public Map<String,Object> convert(@PathVariable long id,@RequestBody Map<String,Object> body){ return service.convert(id,body); }
    @GetMapping("/leads/eligible-assignees") public Object eligibleAssignees(){return service.eligibleAssignees();}
    @GetMapping("/leads/profile-vendors") public Object profileVendors(){return service.profileVendors();}
    @PostMapping("/leads/{id}/assign") public Map<String,Object> assignLead(@PathVariable long id,@RequestBody Map<String,Object> body){return service.assignLead(id,body);}
    @GetMapping("/lead-notifications") public Map<String,Object> notifications(){return service.leadNotifications();}
    @PostMapping("/lead-notifications/{id}/read") public void markNotificationRead(@PathVariable long id){service.markLeadNotificationRead(id);}

    @GetMapping("/pipelines") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINES_VIEW')") public Map<String,Object> pipelines(@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){ return service.pipelines(search,page,size); }
    @PostMapping("/pipelines") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINES_CREATE')") public Map<String,Object> createPipeline(@RequestBody Map<String,Object> body){ return service.savePipeline(null,body); }
    @GetMapping("/pipelines/{id}") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINES_VIEW')") public Map<String,Object> pipeline(@PathVariable long id){ return service.pipeline(id); }
    @PutMapping("/pipelines/{id}") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINES_EDIT')") public Map<String,Object> updatePipeline(@PathVariable long id,@RequestBody Map<String,Object> body){ return service.savePipeline(id,body); }
    @DeleteMapping("/pipelines/{id}") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINES_DELETE')") public void deletePipeline(@PathVariable long id){ service.deletePipeline(id); }
    @PostMapping("/pipelines/{id}/clone") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINES_CLONE')") public Map<String,Object> clonePipeline(@PathVariable long id){ return service.clonePipeline(id); }
    @GetMapping("/pipelines/{id}/kanban") @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEAD_PIPELINE_VIEW')") public Map<String,Object> kanban(@PathVariable long id){ return service.kanban(id); }

    @GetMapping("/companies") public Map<String,Object> companies(@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){ return service.companies(filters,page,size); }
    @PostMapping("/companies") public Map<String,Object> createCompany(@RequestBody Map<String,Object> body){ return service.saveCompany(null,body); }
    @GetMapping("/companies/{id}") public Map<String,Object> company(@PathVariable long id){ return service.company(id); }
    @PutMapping("/companies/{id}") public Map<String,Object> updateCompany(@PathVariable long id,@RequestBody Map<String,Object> body){ return service.saveCompany(id,body); }
    @DeleteMapping("/companies/{id}") public void deleteCompany(@PathVariable long id){ service.deleteCompany(id); }

    @GetMapping("/contacts") public Map<String,Object> contacts(@RequestParam Map<String,String> filters,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){ return service.contacts(filters,page,size); }
    @PostMapping("/contacts") public Map<String,Object> createContact(@RequestBody Map<String,Object> body){ return service.saveContact(null,body); }
    @GetMapping("/contacts/{id}") public Map<String,Object> contact(@PathVariable long id){ return service.contact(id); }
    @PutMapping("/contacts/{id}") public Map<String,Object> updateContact(@PathVariable long id,@RequestBody Map<String,Object> body){ return service.saveContact(id,body); }
    @DeleteMapping("/contacts/{id}") public void deleteContact(@PathVariable long id){ service.deleteContact(id); }

    @PostMapping("/{type:activities|tasks|notes|attachments}") public Map<String,Object> addSupport(@PathVariable String type,@RequestBody Map<String,Object> body){ return service.addSupport(type,body); }
    @PutMapping("/{type:activities|tasks|notes|attachments}/{id}") public Map<String,Object> updateSupport(@PathVariable String type,@PathVariable long id,@RequestBody Map<String,Object> body){ return service.updateSupport(type,id,body); }
    @DeleteMapping("/{type:activities|tasks|notes|attachments}/{id}") public void deleteSupport(@PathVariable String type,@PathVariable long id){ service.deleteSupport(type,id); }

    @GetMapping(value="/lead-export/{entity}",produces="text/csv") public ResponseEntity<String> export(@PathVariable String entity,@RequestParam Map<String,String> filters){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=lead-"+entity+".csv").contentType(MediaType.parseMediaType("text/csv")).body(service.exportCsv(entity,filters));
    }
}
