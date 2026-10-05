package com.intelliatech.app.controller;

import com.intelliatech.app.entity.DomainIndustryMaster;
import com.intelliatech.app.repository.DomainIndustryMasterRepository;
import com.intelliatech.app.security.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/masters/domain-industries") @RequiredArgsConstructor
public class DomainIndustryMasterController {
    private static final long ORG=1L;
    private final DomainIndustryMasterRepository repository;
    private final CurrentUserService users;
    private final JdbcTemplate jdbc;
    public record Request(@NotBlank String name,String description,boolean active,Integer displayOrder) {}
    public record Response(Long id,String name,String description,boolean active,int displayOrder,long usageCount) {}

    @GetMapping @PreAuthorize("isAuthenticated()")
    public List<Response> list(@RequestParam(defaultValue="false") boolean includeInactive){
        return (includeInactive?repository.findByOrganizationIdOrderByDisplayOrderAscNameAsc(ORG):repository.findByOrganizationIdAndActiveTrueOrderByDisplayOrderAscNameAsc(ORG)).stream().map(this::response).toList();
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response create(@Valid @RequestBody Request request){DomainIndustryMaster item=new DomainIndustryMaster();copy(item,request);item.setCreatedBy(users.getCurrentUserId());item.setUpdatedBy(users.getCurrentUserId());return response(repository.save(item));}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response update(@PathVariable Long id,@Valid @RequestBody Request request){DomainIndustryMaster item=get(id);copy(item,request);item.setUpdatedBy(users.getCurrentUserId());return response(repository.save(item));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void delete(@PathVariable Long id){DomainIndustryMaster item=get(id);if(usage(id)>0){item.setActive(false);item.setUpdatedBy(users.getCurrentUserId());repository.save(item);}else repository.delete(item);}
    private DomainIndustryMaster get(Long id){return repository.findById(id).filter(x->x.getOrganizationId().equals(ORG)).orElseThrow(()->new IllegalArgumentException("Domain / Industry does not exist."));}
    private void copy(DomainIndustryMaster item,Request request){String name=request.name().trim();repository.findByOrganizationIdAndNameIgnoreCase(ORG,name).filter(x->!x.getId().equals(item.getId())).ifPresent(x->{throw new IllegalArgumentException("A Domain / Industry with this name already exists.");});item.setName(name);item.setDescription(request.description()==null?null:request.description().trim());item.setActive(request.active());item.setDisplayOrder(request.displayOrder()==null?0:request.displayOrder());}
    private long usage(Long id){return jdbc.queryForObject("SELECT COUNT(*) FROM business_records WHERE module='projects' AND type='fixedCost' AND JSON_VALID(notes) AND CAST(JSON_UNQUOTE(JSON_EXTRACT(notes,'$.domainIndustryId')) AS UNSIGNED)=?",Long.class,id);}
    private Response response(DomainIndustryMaster item){return new Response(item.getId(),item.getName(),item.getDescription(),item.isActive(),item.getDisplayOrder(),usage(item.getId()));}
}
