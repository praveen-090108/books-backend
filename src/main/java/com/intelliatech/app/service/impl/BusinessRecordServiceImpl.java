package com.intelliatech.app.service.impl;

import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.dto.response.BusinessRecordResponse;
import com.intelliatech.app.dto.response.RecordSummaryResponse;
import com.intelliatech.app.entity.BusinessRecord;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.mapper.BusinessRecordMapper;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.service.DocumentNumberPreferenceService;
import com.intelliatech.app.service.InvoiceLifecycleService;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.DataScope;
import com.intelliatech.app.security.DataScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BusinessRecordServiceImpl implements com.intelliatech.app.service.BusinessRecordService {

    private final BusinessRecordRepository repository;
    private final BusinessRecordMapper mapper;
    private final DocumentNumberPreferenceService documentNumberPreferenceService;
    private final InvoiceLifecycleService invoiceLifecycleService;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUsers;
    private final DataScopeService dataScopes;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public Page<BusinessRecordResponse> findAll(String module, String type, String search, String status, String secondaryStatus, String category, String department, String partyName, LocalDate dateFrom, LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String paymentState, Boolean overdue, Pageable pageable) {
        Page<BusinessRecord> records = repository.findAll(specification(module, type, search, status, secondaryStatus, category, department, partyName, dateFrom, dateTo, dueDateFrom, dueDateTo, paymentState, overdue), pageable);
        if (isInvoice(module, type)) records.forEach(record -> invoiceLifecycleService.refreshOverdueStatus(record.getId()));
        return records.map(mapper::toResponse);
    }

    @Override
    @Transactional
    public BusinessRecordResponse findById(String module, String type, Long id) {
        BusinessRecord record = repository.findByModuleAndTypeAndId(module, type, id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
        validateAccess(module, type, record);
        if (isInvoice(module, type)) invoiceLifecycleService.refreshOverdueStatus(id);
        return mapper.toResponse(record);
    }

    @Override
    @Transactional
    public BusinessRecordResponse create(String module, String type, BusinessRecordRequest request) {
        validateCustomer(module,type,request);
        validateStaffingProject(module, type, request);
        validateItemCategory(module, type, request, null);
        BusinessRecordRequest effectiveRequest = request;
        if (isCustomer(module, type)) {
            String allocatedNumber = documentNumberPreferenceService.allocateForCreate("customers");
            effectiveRequest = requestWithRecordNumber(request, allocatedNumber);
        }
        if (isInvoice(module, type)) {
            String allocatedNumber = documentNumberPreferenceService.allocateForCreate("invoices");
            effectiveRequest = invoiceCreateRequest(request, allocatedNumber);
        }
        if (repository.existsByRecordNumber(effectiveRequest.recordNumber())) {
            throw new DuplicateResourceException("Record number already exists: " + effectiveRequest.recordNumber());
        }
        BusinessRecord invoiceProject = validateInvoiceProject(module, type, effectiveRequest, null, true);
        BusinessRecord entity = mapper.toEntity(module, type, effectiveRequest);
        applyResourceFields(module, type, effectiveRequest, entity, null);
        validateFixedCostProject(module, type, effectiveRequest, entity, null);
        Long currentUserId = currentUsers.getCurrentUserId();
        entity.setCreatedBy(currentUserId);
        entity.setUpdatedBy(currentUserId);
        BusinessRecord saved = repository.save(entity);
        syncInvoiceProject(module, type, saved.getId(), invoiceProject);
        syncFixedCostTeam(module, type, saved.getId(), effectiveRequest.notes(), currentUserId);
        if (isInvoice(module, type)) invoiceLifecycleService.initializeDraft(saved);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessRecordResponse update(String module, String type, Long id, BusinessRecordRequest request) {
        BusinessRecord record = repository.findByModuleAndTypeAndId(module, type, id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
        validateAccess(module, type, record);
        validateCustomer(module,type,request);
        validateStaffingProject(module, type, request);
        validateItemCategory(module, type, request, record);
        BusinessRecordRequest effectiveRequest = request;
        if (isCustomer(module, type)) {
            effectiveRequest = requestWithRecordNumber(request, record.getRecordNumber());
        }
        if (isInvoice(module, type)) {
            invoiceLifecycleService.assertCanEdit(record, request);
            effectiveRequest = invoiceUpdateRequest(request, record);
        }
        BusinessRecord invoiceProject = validateInvoiceProject(module, type, effectiveRequest, id, false);
        mapper.copy(effectiveRequest, record);
        applyResourceFields(module, type, effectiveRequest, record, id);
        validateFixedCostProject(module, type, effectiveRequest, record, id);
        record.setUpdatedBy(currentUsers.getCurrentUserId());
        BusinessRecord saved = repository.save(record);
        syncInvoiceProject(module, type, saved.getId(), invoiceProject);
        syncFixedCostTeam(module, type, saved.getId(), effectiveRequest.notes(), currentUsers.getCurrentUserId());
        if (isInvoice(module, type)) invoiceLifecycleService.refreshOverdueStatus(id);
        return mapper.toResponse(saved);
    }

    private boolean isInvoice(String module, String type) {
        return "sales".equals(module) && "invoices".equals(type);
    }

    private boolean isCustomer(String module, String type) {
        return "sales".equalsIgnoreCase(module) && "customers".equalsIgnoreCase(type);
    }

    private void validateItemCategory(String module, String type, BusinessRecordRequest request, BusinessRecord existing) {
        if (!"purchases".equalsIgnoreCase(module) || !"items".equalsIgnoreCase(type)) return;
        String category = request.category() == null ? "" : request.category().trim();
        if (category.isBlank()) throw new IllegalArgumentException("Item Category is required.");
        List<Map<String, Object>> matches = jdbc.queryForList(
                "SELECT active FROM item_category_master WHERE organization_id=1 AND LOWER(category_name)=LOWER(?)",
                category
        );
        if (matches.isEmpty()) throw new IllegalArgumentException("Please select a valid Item Category.");
        Object activeValue = matches.get(0).get("active");
        boolean active = Boolean.TRUE.equals(activeValue)
                || (activeValue instanceof Number number && number.intValue() != 0);
        boolean retainingHistoricalCategory = existing != null
                && category.equalsIgnoreCase(existing.getCategory() == null ? "" : existing.getCategory().trim());
        if (!active && !retainingHistoricalCategory) {
            throw new IllegalArgumentException("Selected Item Category is inactive.");
        }
    }

    private BusinessRecordRequest requestWithRecordNumber(BusinessRecordRequest request, String recordNumber) {
        return new BusinessRecordRequest(
                recordNumber, request.partyName(), request.partyEmail(), request.partyPhone(), request.partyCity(),
                request.category(), request.status(), request.secondaryStatus(), request.amount(), request.balanceAmount(),
                request.recordDate(), request.dueDate(), request.closedDate(), request.referenceNumber(),
                request.paymentMode(), request.ownerName(), request.notes(), request.department(),
                request.designation(), request.reportingManagerId()
        );
    }

    private void validateStaffingProject(String module, String type, BusinessRecordRequest request) {
        if (!"projects".equalsIgnoreCase(module) || !"staffing".equalsIgnoreCase(type)) return;
        if (!Set.of("Active", "Hold", "Close").contains(request.status())) {
            throw new IllegalArgumentException("Staffing Project Status must be Active, Hold or Close.");
        }
        if ("Close".equals(request.status())) {
            if (request.closedDate() == null) {
                throw new IllegalArgumentException("Close Date is required when project status is Close.");
            }
            if (request.closedDate().isBefore(request.recordDate())) {
                throw new IllegalArgumentException("Close Date cannot be earlier than the project Start Date.");
            }
        } else if (request.closedDate() != null) {
            throw new IllegalArgumentException("Close Date is only applicable when project status is Close.");
        }
    }

    private BusinessRecord validateInvoiceProject(String module, String type, BusinessRecordRequest request,
                                                  Long invoiceId, boolean creating) {
        if (!isInvoice(module, type)) return null;
        try {
            JsonNode notes = objectMapper.readTree(request.notes());
            long projectId = notes.path("projectId").asLong(notes.path("Project ID").asLong(0));
            boolean explicitlyProjectInvoice = notes.has("invoiceType") || notes.has("Invoice Type");
            if (projectId <= 0 && invoiceId != null) {
                List<Long> linked = jdbc.queryForList("SELECT project_id FROM invoice_project_links WHERE invoice_id=?", Long.class, invoiceId);
                if (!linked.isEmpty()) projectId = linked.get(0);
            }
            if (projectId <= 0) {
                if (creating && explicitlyProjectInvoice) throw new IllegalArgumentException("Project is required for a Fixed Cost or Staffing Invoice.");
                return null; // Historical invoices without a project remain editable.
            }
            String invoiceType = notes.path("invoiceType").asText(notes.path("Invoice Type").asText(request.category())).trim();
            String projectType = "Fixed Cost".equalsIgnoreCase(invoiceType) ? "fixedCost"
                    : "Staffing".equalsIgnoreCase(invoiceType) ? "staffing" : "";
            if (projectType.isBlank()) throw new IllegalArgumentException("Invoice Type must be Fixed Cost or Staffing.");
            BusinessRecord project = repository.findByModuleAndTypeAndId("projects", projectType, projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected project does not match the Invoice Type."));
            dataScopes.validateRecordAccess(project.getCreatedBy());
            String projectCustomer = "fixedCost".equals(projectType) ? project.getPartyCity() : project.getPartyName();
            if (!normalizedName(projectCustomer).equals(normalizedName(request.partyName()))) {
                throw new IllegalArgumentException("Selected project does not belong to the invoice customer.");
            }
            String invoiceCurrency = notes.path("Currency").asText("INR").trim().toUpperCase(Locale.ROOT);
            String projectCurrency = StringUtils.hasText(project.getPaymentMode())
                    ? project.getPaymentMode().trim().substring(0, Math.min(3, project.getPaymentMode().trim().length())).toUpperCase(Locale.ROOT)
                    : "INR";
            if (!invoiceCurrency.equals(projectCurrency)) {
                throw new IllegalArgumentException("Invoice Currency must match the selected Project Currency (" + projectCurrency + ").");
            }
            boolean alreadyLinked = invoiceId != null && Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT COUNT(*)>0 FROM invoice_project_links WHERE invoice_id=? AND project_id=?", Boolean.class, invoiceId, projectId));
            if (!alreadyLinked && Set.of("CANCELLED", "DELETED", "ARCHIVED").contains(project.getStatus().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The selected Project is not available for new invoices.");
            }
            return project;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invoice Project details are invalid.");
        }
    }

    private void syncInvoiceProject(String module, String type, Long invoiceId, BusinessRecord project) {
        if (!isInvoice(module, type) || project == null) return;
        jdbc.update("""
                INSERT INTO invoice_project_links(invoice_id,project_id,project_type,created_by)
                VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE project_id=VALUES(project_id),project_type=VALUES(project_type)
                """, invoiceId, project.getId(), project.getType(), currentUsers.getCurrentUserId());
    }

    private String normalizedName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean isFixedCostProject(String module,String type){return "projects".equalsIgnoreCase(module)&&"fixedCost".equalsIgnoreCase(type);}

    private void validateFixedCostProject(String module,String type,BusinessRecordRequest request,BusinessRecord project,Long projectId){
        if(!isFixedCostProject(module,type))return;
        try{
            JsonNode notes=objectMapper.readTree(request.notes());
            long managerId=notes.path("projectManagerId").asLong(0);
            BusinessRecord manager=activeEmployee(managerId,"Project Manager");
            project.setOwnerName(manager.getPartyName());
            String currency=notes.path("currencyCode").asText(request.paymentMode()).trim().toUpperCase(Locale.ROOT);
            if(currency.contains(" "))currency=currency.substring(0,currency.indexOf(' '));
            Integer currencyCount=jdbc.queryForObject("SELECT COUNT(*) FROM currency_master WHERE code=? AND active=TRUE",Integer.class,currency);
            if(currencyCount==null||currencyCount==0)throw new IllegalArgumentException("Please select a supported active Currency.");
            project.setPaymentMode(currency);
            long domainId=notes.path("domainIndustryId").asLong(0);
            if(domainId>0){
                List<Map<String,Object>> domains=jdbc.queryForList("SELECT name,active FROM domain_industry_master WHERE id=? AND organization_id=1",domainId);
                if(domains.isEmpty())throw new IllegalArgumentException("Domain / Industry does not exist.");
                Object activeValue=domains.get(0).get("active");boolean active=Boolean.TRUE.equals(activeValue)||(activeValue instanceof Number number&&number.intValue()!=0);
                boolean retained=!active&&projectId!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM business_records WHERE id=? AND JSON_VALID(notes) AND CAST(JSON_UNQUOTE(JSON_EXTRACT(notes,'$.domainIndustryId')) AS UNSIGNED)=?",Integer.class,projectId,domainId)>0;
                if(!active&&!retained)throw new IllegalArgumentException("Selected Domain / Industry is inactive.");
            }
            String duration=notes.path("estimatedDuration").asText("");
            if("CUSTOM".equals(duration)){
                int value=notes.path("customDurationValue").asInt(0);String unit=notes.path("customDurationUnit").asText("");
                if(value<=0||value>1200||!Set.of("DAYS","WEEKS","MONTHS","YEARS").contains(unit))throw new IllegalArgumentException("Enter a valid Custom Duration.");
            }else if(!duration.matches("(?:[1-9]|1[0-2]|15|18|21|24|27|30|33|36)_MONTHS"))throw new IllegalArgumentException("Please select a valid Estimated Duration.");
            JsonNode members=notes.path("teamMemberIds");Set<Long> unique=new HashSet<>();
            if(members.isArray())for(JsonNode member:members){long employeeId=member.asLong(0);if(!unique.add(employeeId))throw new IllegalArgumentException("A Team Member cannot be selected twice.");activeEmployee(employeeId,"Team Member");}
        }catch(IllegalArgumentException exception){throw exception;}catch(Exception exception){throw new IllegalArgumentException("Fixed Cost Project details are invalid.");}
    }

    private BusinessRecord activeEmployee(long id,String label){
        BusinessRecord employee=repository.findByModuleAndTypeAndId("resources","resources",id).orElseThrow(()->new IllegalArgumentException(label+" does not exist."));
        if(!"Active".equalsIgnoreCase(employee.getStatus()))throw new IllegalArgumentException(label+" must be an active employee.");
        return employee;
    }

    private void syncFixedCostTeam(String module,String type,Long projectId,String notes,Long actor){
        if(!isFixedCostProject(module,type))return;
        try{
            JsonNode members=objectMapper.readTree(notes).path("teamMemberIds");Set<Long> desired=new LinkedHashSet<>();if(members.isArray())members.forEach(x->desired.add(x.asLong()));
            Set<Long> active=new HashSet<>(jdbc.queryForList("SELECT employee_id FROM fixed_cost_project_team_member WHERE project_id=? AND assignment_status='ACTIVE'",Long.class,projectId));
            LocalDate today=LocalDate.now();
            for(Long employeeId:active)if(!desired.contains(employeeId))jdbc.update("UPDATE fixed_cost_project_team_member SET assignment_status='INACTIVE',removed_date=?,removed_by=?,removal_note=? WHERE project_id=? AND employee_id=? AND assignment_status='ACTIVE'",today,actor,"Removed through Project Edit",projectId,employeeId);
            LocalDate addedDate=jdbc.queryForObject("SELECT record_date FROM business_records WHERE id=?",LocalDate.class,projectId);if(addedDate==null)addedDate=today;
            for(Long employeeId:desired)if(!active.contains(employeeId)){BusinessRecord employee=activeEmployee(employeeId,"Team Member");jdbc.update("INSERT INTO fixed_cost_project_team_member(project_id,employee_id,role_designation,added_date,assignment_status,created_by) VALUES(?,?,?,?, 'ACTIVE',?)",projectId,employeeId,employee.getCategory(),addedDate,actor);}
        }catch(Exception exception){if(exception instanceof IllegalArgumentException illegal)throw illegal;throw new IllegalArgumentException("Unable to save Project Team Members.");}
    }


    private static final Set<String> GST_TREATMENTS=Set.of("Registered Business - Regular","Registered Business - Composition","Unregistered Business","Consumer","Overseas","Special Economic Zone (SEZ)","Deemed Export");
    private static final Map<String,Set<String>> CUSTOMER_STATES=Map.of(
            "India",Set.of("Andhra Pradesh","Arunachal Pradesh","Assam","Bihar","Chhattisgarh","Goa","Gujarat","Haryana","Himachal Pradesh","Jharkhand","Karnataka","Kerala","Madhya Pradesh","Maharashtra","Manipur","Meghalaya","Mizoram","Nagaland","Odisha","Punjab","Rajasthan","Sikkim","Tamil Nadu","Telangana","Tripura","Uttar Pradesh","Uttarakhand","West Bengal","Andaman and Nicobar Islands","Chandigarh","Dadra and Nagar Haveli and Daman and Diu","Delhi","Jammu and Kashmir","Ladakh","Lakshadweep","Puducherry"),
            "United States",Set.of("Alabama","Alaska","Arizona","Arkansas","California","Colorado","Connecticut","Delaware","Florida","Georgia","Hawaii","Idaho","Illinois","Indiana","Iowa","Kansas","Kentucky","Louisiana","Maine","Maryland","Massachusetts","Michigan","Minnesota","Mississippi","Missouri","Montana","Nebraska","Nevada","New Hampshire","New Jersey","New Mexico","New York","North Carolina","North Dakota","Ohio","Oklahoma","Oregon","Pennsylvania","Rhode Island","South Carolina","South Dakota","Tennessee","Texas","Utah","Vermont","Virginia","Washington","West Virginia","Wisconsin","Wyoming","District of Columbia"),
            "Australia",Set.of("Australian Capital Territory","New South Wales","Northern Territory","Queensland","South Australia","Tasmania","Victoria","Western Australia"),
            "Canada",Set.of("Alberta","British Columbia","Manitoba","New Brunswick","Newfoundland and Labrador","Northwest Territories","Nova Scotia","Nunavut","Ontario","Prince Edward Island","Quebec","Saskatchewan","Yukon"));
    private void validateCustomer(String module,String type,BusinessRecordRequest request){
        if(!"sales".equalsIgnoreCase(module)||!"customers".equalsIgnoreCase(type))return;
        try{
            JsonNode notes=objectMapper.readTree(request.notes());String country=text(notes,"country"),state=text(notes,"state"),shippingCountry=text(notes,"shippingCountry"),shippingState=text(notes,"shippingState"),treatment=text(notes,"gstTreatment"),category=text(notes,"gstCategory");
            validateState(country,state,"Billing");if(StringUtils.hasText(shippingState))validateState(shippingCountry,shippingState,"Shipping");
            if(!GST_TREATMENTS.contains(treatment))throw new IllegalArgumentException("Please select a valid GST Treatment.");
            Map<String,String> categories=Map.of("Registered Business - Regular","Regular","Registered Business - Composition","Composition","Unregistered Business","Unregistered","Consumer","Consumer","Overseas","Overseas","Special Economic Zone (SEZ)","SEZ","Deemed Export","Deemed Export");
            if(!categories.get(treatment).equals(category))throw new IllegalArgumentException("GST Category must match the selected GST Treatment.");
            String gstin=text(notes,"gstin");if(treatment.startsWith("Registered Business")&&!gstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$"))throw new IllegalArgumentException("Enter a valid 15-character GSTIN for a registered business.");
            JsonNode attachments=notes.get("attachments");if(attachments!=null&&attachments.isArray())for(JsonNode file:attachments)if(!StringUtils.hasText(text(file,"fileName"))||!StringUtils.hasText(text(file,"url")))throw new IllegalArgumentException("Customer attachment metadata is invalid.");
        }catch(IllegalArgumentException exception){throw exception;}catch(Exception exception){throw new IllegalArgumentException("Customer details are invalid.");}
    }
    private void validateState(String country,String state,String label){if(!CUSTOMER_STATES.containsKey(country))throw new IllegalArgumentException(label+" country is not supported.");if(!StringUtils.hasText(state)||!CUSTOMER_STATES.get(country).contains(state))throw new IllegalArgumentException(label+" State must belong to the selected Country.");}
    private String text(JsonNode node,String key){JsonNode value=node==null?null:node.get(key);return value==null||value.isNull()?"":value.asText().trim();}

    private BusinessRecordRequest invoiceCreateRequest(BusinessRecordRequest request, String recordNumber) {
        return invoiceRequest(request, recordNumber, "Draft", "Unpaid", request.amount());
    }

    private BusinessRecordRequest invoiceUpdateRequest(BusinessRecordRequest request, BusinessRecord record) {
        return invoiceRequest(request, record.getRecordNumber(), record.getStatus(), record.getSecondaryStatus(), record.getBalanceAmount());
    }

    private BusinessRecordRequest invoiceRequest(
            BusinessRecordRequest request,
            String recordNumber,
            String status,
            String secondaryStatus,
            BigDecimal balanceAmount
    ) {
        return new BusinessRecordRequest(
                recordNumber,
                request.partyName(),
                request.partyEmail(),
                request.partyPhone(),
                request.partyCity(),
                request.category(),
                status,
                secondaryStatus,
                request.amount(),
                balanceAmount,
                request.recordDate(),
                request.dueDate(),
                request.closedDate(),
                request.referenceNumber(),
                request.paymentMode(),
                request.ownerName(),
                invoiceNotesWithNumber(request.notes(), recordNumber),
                null,
                null,
                null
        );
    }

    private void applyResourceFields(String module, String type, BusinessRecordRequest request,
                                     BusinessRecord resource, Long resourceId) {
        if (!"resources".equalsIgnoreCase(module) || !"resources".equalsIgnoreCase(type)) return;
        if (request.department() == null) {
            throw new IllegalArgumentException("Please select a Department.");
        }
        if (request.designation() != null && request.designation().length() > 150) {
            throw new IllegalArgumentException("Designation must not exceed 150 characters.");
        }
        try {
            JsonNode details = objectMapper.readTree(request.notes());
            if (!details.hasNonNull("monthlySalary") || details.path("monthlySalary").decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Monthly Salary is required and must be greater than zero.");
            }
            String salaryCurrency = details.path("salaryCurrency").asText("INR").trim().toUpperCase(Locale.ROOT);
            Integer salaryCurrencyCount = jdbc.queryForObject("SELECT COUNT(*) FROM currency_master WHERE code=? AND active=TRUE", Integer.class, salaryCurrency);
            if (salaryCurrencyCount == null || salaryCurrencyCount == 0) {
                throw new IllegalArgumentException("Please select a supported Salary Currency.");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Monthly Salary is required and must be a valid amount.");
        }
        resource.setCategory(request.designation()); // preserves legacy list/search behavior
        resource.setOwnerName(null); // manager name is resolved through the relationship
        if (request.reportingManagerId() == null) {
            resource.setReportingManager(null);
            return;
        }
        if (request.reportingManagerId().equals(resourceId)) {
            throw new IllegalArgumentException("A resource cannot be assigned as their own Reporting Manager.");
        }
        BusinessRecord manager = repository.findByModuleAndTypeAndId("resources", "resources", request.reportingManagerId())
                .orElseThrow(() -> new IllegalArgumentException("Reporting Manager does not exist."));
        if (!"Active".equalsIgnoreCase(manager.getStatus()) || manager.getDepartment() != request.department()) {
            throw new IllegalArgumentException(
                    "The selected Reporting Manager must be an active employee from the selected Department.");
        }
        resource.setReportingManager(manager);
    }

    private String invoiceNotesWithNumber(String notes, String recordNumber) {
        if (!StringUtils.hasText(notes)) return notes;
        try {
            JsonNode root = objectMapper.readTree(notes);
            if (!(root instanceof ObjectNode objectNode)) return notes;
            objectNode.put("Invoice#", recordNumber);
            objectNode.put("InvoiceNumber", recordNumber);
            return objectMapper.writeValueAsString(objectNode);
        } catch (Exception ignored) {
            return notes;
        }
    }

    @Override
    @Transactional
    public void delete(String module, String type, Long id) {
        BusinessRecord record = repository.findByModuleAndTypeAndId(module, type, id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
        validateAccess(module, type, record);
        if (isInvoice(module, type)) invoiceLifecycleService.assertCanDelete(id);
        repository.delete(record);
    }

    @Override
    @Transactional(readOnly = true)
    public RecordSummaryResponse summary(String module, String type, String search, String status, String secondaryStatus, String category, String partyName, LocalDate dateFrom, LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String paymentState, Boolean overdue) {
        var records = repository.findAll(specification(module, type, search, status, secondaryStatus, category, null, partyName, dateFrom, dateTo, dueDateFrom, dueDateTo, paymentState, overdue));
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> statusAmounts = new LinkedHashMap<>();

        for (BusinessRecord record : records) {
            totalAmount = totalAmount.add(record.getAmount());
            totalBalance = totalBalance.add(record.getBalanceAmount());
            statusCounts.merge(record.getStatus(), 1L, Long::sum);
            statusAmounts.merge(record.getStatus(), record.getAmount(), BigDecimal::add);
        }

        return new RecordSummaryResponse(records.size(), totalAmount, totalBalance, statusCounts, statusAmounts);
    }

    private Specification<BusinessRecord> specification(String module, String type, String search, String status, String secondaryStatus, String category, String department, String partyName, LocalDate dateFrom, LocalDate dateTo, LocalDate dueDateFrom, LocalDate dueDateTo, String paymentState, Boolean overdue) {
        DataScope dataScope = requiresDataScope(module, type) ? dataScopes.getCurrentDataScope() : DataScope.unrestrictedScope();
        return (root, query, builder) -> {
            Predicate predicate = builder.and(
                    builder.equal(root.get("module"), module),
                    builder.equal(root.get("type"), type)
            );

            if (!dataScope.unrestricted()) {
                predicate = builder.and(predicate, root.get("createdBy").in(dataScope.accessibleUserIds()));
            }

            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("recordNumber")), like),
                        builder.like(builder.lower(root.get("partyName")), like),
                        builder.like(builder.lower(root.get("partyEmail")), like),
                        builder.like(builder.lower(root.get("referenceNumber")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(secondaryStatus)) {
                predicate = builder.and(predicate, builder.equal(root.get("secondaryStatus"), secondaryStatus));
            }
            if (StringUtils.hasText(category)) {
                predicate = builder.and(predicate, builder.equal(root.get("category"), category));
            }
            if (StringUtils.hasText(department)) {
                predicate = builder.and(predicate, builder.equal(root.get("department"),
                        com.intelliatech.app.entity.Department.valueOf(department.toUpperCase())));
            }
            if (StringUtils.hasText(partyName)) {
                predicate = builder.and(predicate, builder.equal(builder.lower(root.get("partyName")), partyName.trim().toLowerCase()));
            }
            if (dateFrom != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("recordDate"), dateFrom));
            }
            if (dateTo != null) {
                predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("recordDate"), dateTo));
            }
            if (dueDateFrom != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("dueDate"), dueDateFrom));
            }
            if (dueDateTo != null) {
                predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("dueDate"), dueDateTo));
            }
            if (StringUtils.hasText(paymentState)) {
                if ("PAID".equalsIgnoreCase(paymentState)) predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("balanceAmount"), BigDecimal.ZERO));
                if ("UNPAID".equalsIgnoreCase(paymentState)) predicate = builder.and(predicate, builder.greaterThan(root.get("balanceAmount"), BigDecimal.ZERO));
            }
            if (Boolean.TRUE.equals(overdue)) {
                predicate = builder.and(predicate,
                        builder.lessThan(root.get("dueDate"), LocalDate.now()),
                        builder.greaterThan(root.get("balanceAmount"), BigDecimal.ZERO),
                        builder.not(root.get("status").in("Draft", "Paid", "Void")));
            }
            return predicate;
        };
    }

    private void validateAccess(String module, String type, BusinessRecord record) {
        if (requiresDataScope(module, type)) dataScopes.validateRecordAccess(record.getCreatedBy());
    }

    private boolean requiresDataScope(String module, String type) {
        if ("settings".equalsIgnoreCase(module)) return false;
        String normalizedType = type == null ? "" : type.toLowerCase();
        return !(normalizedType.contains("master") || normalizedType.equals("categories") || normalizedType.equals("statuses"));
    }
}
