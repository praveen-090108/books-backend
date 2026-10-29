package com.intelliatech.app.einvoice.qualification.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.einvoice.qualification.config.QualificationProperties;
import com.intelliatech.app.einvoice.qualification.repository.QualificationStore;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class QualificationReportGenerator {
 private final QualificationProperties properties; private final QualificationStore store; private final ObjectMapper json;
 public List<Path> generate(long runId){try{Path dir=properties.getReportDirectory().toAbsolutePath().normalize();Files.createDirectories(dir);String base="ey-qualification-run-"+runId;Path jp=dir.resolve(base+".json"),xp=dir.resolve(base+".xlsx"),pp=dir.resolve(base+".pdf");Map<String,Object> report=new LinkedHashMap<>();report.put("provider","EY IRP 5");report.put("environment","SANDBOX");report.put("run",store.run(runId));report.put("portalProgress",store.portal());report.put("results",store.results(runId));report.put("sandboxIrnRegister",store.documents(runId));json.writerWithDefaultPrettyPrinter().writeValue(jp.toFile(),report);excel(xp,report);pdf(pp,report);return List.of(xp,pp,jp);}catch(Exception e){throw new IllegalStateException("Could not generate qualification reports",e);}}
 @SuppressWarnings("unchecked") private void excel(Path path,Map<String,Object> report)throws Exception{try(Workbook wb=new XSSFWorkbook();OutputStream out=Files.newOutputStream(path)){Sheet summary=wb.createSheet("Executive Summary");int r=0;for(var e:((Map<String,Object>)report.get("run")).entrySet()){Row row=summary.createRow(r++);row.createCell(0).setCellValue(e.getKey());row.createCell(1).setCellValue(String.valueOf(e.getValue()));}sheet(wb,"Detailed Test Cases",(List<Map<String,Object>>)report.get("results"));sheet(wb,"Sandbox IRN Register",(List<Map<String,Object>>)report.get("sandboxIrnRegister"));wb.write(out);}}
 private void sheet(Workbook wb,String name,List<Map<String,Object>> rows){Sheet s=wb.createSheet(name);if(rows.isEmpty())return;List<String> headers=new ArrayList<>(rows.get(0).keySet());Row h=s.createRow(0);for(int i=0;i<headers.size();i++){h.createCell(i).setCellValue(headers.get(i));s.setColumnWidth(i,Math.min(12000,Math.max(3500,headers.get(i).length()*320)));}int n=1;for(Map<String,Object> data:rows){Row row=s.createRow(n++);for(int i=0;i<headers.size();i++)row.createCell(i).setCellValue(String.valueOf(data.get(headers.get(i))));}}
 @SuppressWarnings("unchecked") private void pdf(Path path,Map<String,Object> report)throws Exception{Document d=new Document(PageSize.A4.rotate());try(OutputStream out=Files.newOutputStream(path)){PdfWriter.getInstance(d,out);d.open();d.add(new Paragraph("EY IRP 5 Sandbox Qualification Report",FontFactory.getFont(FontFactory.HELVETICA_BOLD,16)));d.add(new Paragraph("Environment: SANDBOX"));d.add(new Paragraph("Run: "+report.get("run")));List<Map<String,Object>> rows=(List<Map<String,Object>>)report.get("results");PdfPTable t=new PdfPTable(6);for(String h:List.of("Scenario","API","Expected","Actual","HTTP","Result"))t.addCell(h);for(Map<String,Object> r:rows){for(String k:List.of("scenario_id","api_name","expected_outcome","actual_outcome","http_status","result_status"))t.addCell(String.valueOf(r.get(k)));}d.add(t);d.close();}}
}
