package com.intelliatech.app.service;

import com.intelliatech.app.entity.*;
import com.intelliatech.app.repository.*;
import com.intelliatech.app.security.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class FixedCostProjectMilestoneService {
 private static final Set<String> STATUSES=Set.of("PLANNED","PENDING","IN_PROGRESS","ON_HOLD","COMPLETED","CANCELLED");
 private final FixedCostProjectMilestoneRepository milestones;
 private final BusinessRecordRepository records;
 private final CurrentUserService users;
 private final DataScopeService dataScopes;

 public record Request(String name,String description,LocalDate startDate,LocalDate dueDate,BigDecimal amount,BigDecimal weightage,String status,BigDecimal progress,String notes,Long invoiceId){}
 public record Response(Long id,Long projectId,String name,String description,LocalDate startDate,LocalDate dueDate,BigDecimal amount,BigDecimal weightage,String status,BigDecimal progress,String notes,Long invoiceId,String invoiceNumber,Long createdBy,LocalDateTime createdAt,Long updatedBy,LocalDateTime updatedAt){}
 public record Summary(BigDecimal allocatedWeightage,BigDecimal availableWeightage,BigDecimal allocatedAmount,BigDecimal remainingAmount,String currencyCode,List<Response> milestones){}

 @Transactional(readOnly=true) public Summary list(Long projectId){BusinessRecord project=project(projectId,false);List<Response> rows=milestones.findByProjectIdAndActiveTrueOrderByDueDateAscIdAsc(projectId).stream().map(this::response).toList();return summary(project,rows);}
 @Transactional(readOnly=true) public Response get(Long projectId,Long id){project(projectId,false);return response(item(projectId,id));}
 @Transactional public Response create(Long projectId,Request request){BusinessRecord project=project(projectId,true);FixedCostProjectMilestone item=new FixedCostProjectMilestone();item.setProject(project);copy(item,request,null);item.setCreatedBy(users.getCurrentUserId());item.setUpdatedBy(users.getCurrentUserId());return response(milestones.save(item));}
 @Transactional public Response update(Long projectId,Long id,Request request){BusinessRecord project=project(projectId,true);FixedCostProjectMilestone item=item(projectId,id);copy(item,request,id);item.setProject(project);item.setUpdatedBy(users.getCurrentUserId());return response(milestones.save(item));}
 @Transactional public Response status(Long projectId,Long id,String status,BigDecimal progress){project(projectId,true);FixedCostProjectMilestone item=item(projectId,id);String normalized=normalizeStatus(status);item.setStatus(normalized);item.setProgress(consistency(normalized,progress));item.setUpdatedBy(users.getCurrentUserId());return response(milestones.save(item));}
 @Transactional public void delete(Long projectId,Long id){project(projectId,true);FixedCostProjectMilestone item=item(projectId,id);if(item.getInvoice()!=null)throw new IllegalArgumentException("This milestone is linked to an invoice and cannot be deleted.");item.setActive(false);item.setUpdatedBy(users.getCurrentUserId());milestones.save(item);}

 private void copy(FixedCostProjectMilestone item,Request request,Long excludeId){
   String name=request.name()==null?"":request.name().trim();if(name.isEmpty())throw new IllegalArgumentException("Milestone Name is required.");if(name.length()>180)throw new IllegalArgumentException("Milestone Name must not exceed 180 characters.");
   milestones.findByProjectIdAndActiveTrueOrderByDueDateAscIdAsc(item.getProject().getId()).stream().filter(x->!Objects.equals(x.getId(),excludeId)&&x.getName().equalsIgnoreCase(name)).findAny().ifPresent(x->{throw new IllegalArgumentException("A milestone with this name already exists for this project.");});
   if(request.dueDate()==null)throw new IllegalArgumentException("Due Date is required.");if(request.startDate()!=null&&request.dueDate().isBefore(request.startDate()))throw new IllegalArgumentException("Due Date must be on or after Start Date.");
   BusinessRecord project=item.getProject();if(request.startDate()!=null&&request.startDate().isBefore(project.getRecordDate())||project.getDueDate()!=null&&request.dueDate().isAfter(project.getDueDate()))throw new IllegalArgumentException("Milestone dates must fall within the Project duration.");
   BigDecimal amount=value(request.amount());if(amount.signum()<0)throw new IllegalArgumentException("Amount must be zero or greater.");BigDecimal weight=value(request.weightage());if(weight.signum()<=0||weight.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Weightage must be greater than 0 and at most 100%.");
   BigDecimal otherWeight=milestones.allocatedWeightage(project.getId(),excludeId);if(otherWeight.add(weight).compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Total milestone weightage cannot exceed 100%. Remaining weightage: "+new BigDecimal("100").subtract(otherWeight).stripTrailingZeros().toPlainString()+"%.");
   BigDecimal otherAmount=milestones.allocatedAmount(project.getId(),excludeId);if(otherAmount.add(amount).compareTo(project.getAmount())>0)throw new IllegalArgumentException("Total milestone amount cannot exceed the Project Contract Value. Remaining amount: "+project.getAmount().subtract(otherAmount)+".");
   String status=normalizeStatus(request.status());BigDecimal progress=consistency(status,request.progress());
   BusinessRecord invoice=null;if(request.invoiceId()!=null){invoice=records.findByModuleAndTypeAndId("sales","invoices",request.invoiceId()).orElseThrow(()->new IllegalArgumentException("Linked Invoice does not exist."));}
   item.setName(name);item.setDescription(clean(request.description()));item.setStartDate(request.startDate());item.setDueDate(request.dueDate());item.setAmount(amount);item.setWeightage(weight);item.setStatus(status);item.setProgress(progress);item.setNotes(clean(request.notes()));item.setInvoice(invoice);
 }
 private BusinessRecord project(Long id,boolean lock){BusinessRecord project=(lock?records.findFixedCostProjectForUpdate(id):records.findByModuleAndTypeAndId("projects","fixedCost",id)).orElseThrow(()->new IllegalArgumentException("Fixed Cost Project does not exist."));dataScopes.validateRecordAccess(project.getCreatedBy());return project;}
 private FixedCostProjectMilestone item(Long projectId,Long id){return milestones.findByIdAndProjectIdAndActiveTrue(id,projectId).orElseThrow(()->new IllegalArgumentException("Milestone does not exist for this project."));}
 private String normalizeStatus(String status){String value=status==null?"PLANNED":status.trim().toUpperCase().replace(' ','_');if(!STATUSES.contains(value))throw new IllegalArgumentException("Please select a valid Milestone Status.");return value;}
 private BigDecimal consistency(String status,BigDecimal progress){BigDecimal value=value(progress);if(value.signum()<0||value.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Progress must be between 0 and 100%.");if("COMPLETED".equals(status))return new BigDecimal("100");if(value.compareTo(new BigDecimal("100"))==0&&!"CANCELLED".equals(status))return new BigDecimal("100");if(("PLANNED".equals(status)||"PENDING".equals(status))&&value.signum()==0)return BigDecimal.ZERO;return value;}
 private Summary summary(BusinessRecord project,List<Response> rows){BigDecimal weight=rows.stream().map(Response::weightage).reduce(BigDecimal.ZERO,BigDecimal::add),amount=rows.stream().map(Response::amount).reduce(BigDecimal.ZERO,BigDecimal::add);String currency=project.getPaymentMode()==null?"INR":project.getPaymentMode().trim().substring(0,Math.min(3,project.getPaymentMode().trim().length())).toUpperCase();return new Summary(weight,new BigDecimal("100").subtract(weight),amount,project.getAmount().subtract(amount),currency,rows);}
 private Response response(FixedCostProjectMilestone item){return new Response(item.getId(),item.getProject().getId(),item.getName(),item.getDescription(),item.getStartDate(),item.getDueDate(),item.getAmount(),item.getWeightage(),item.getStatus(),item.getProgress(),item.getNotes(),item.getInvoice()==null?null:item.getInvoice().getId(),item.getInvoice()==null?null:item.getInvoice().getRecordNumber(),item.getCreatedBy(),item.getCreatedAt(),item.getUpdatedBy(),item.getUpdatedAt());}
 private BigDecimal value(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
 private String clean(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
}
