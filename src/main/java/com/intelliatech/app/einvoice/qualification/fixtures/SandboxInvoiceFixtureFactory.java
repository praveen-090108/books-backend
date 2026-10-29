package com.intelliatech.app.einvoice.qualification.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SandboxInvoiceFixtureFactory {
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
 private final ObjectMapper json;
 public SandboxInvoiceFixtureFactory(ObjectMapper json){this.json=json;}
 public String uniqueDocumentNumber(String type){return ("Q"+type+LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))+UUID.randomUUID().toString().replace("-","").substring(0,6)).toUpperCase();}
 public JsonNode create(String fixture,String sellerGstin){
  String type=fixture.contains("credit")?"CRN":fixture.contains("debit")?"DBN":"INV"; String number=uniqueDocumentNumber(type);
  BigDecimal taxable=new BigDecimal("1000.00"), tax=new BigDecimal("90.00"), total=new BigDecimal("1180.00");
  Map<String,Object> payload=Map.ofEntries(
    Map.entry("Version","1.1"), Map.entry("TranDtls",Map.of("TaxSch","GST","SupTyp","B2B","RegRev","N","IgstOnIntra","N")),
    Map.entry("DocDtls",Map.of("Typ",type,"No",number,"Dt",LocalDate.now().format(DATE))),
    Map.entry("SellerDtls",Map.of("Gstin",sellerGstin,"LglNm","Sandbox Test Seller","Addr1","Sandbox Address 1","Loc","Indore","Pin",452001,"Stcd","23")),
    Map.entry("BuyerDtls",Map.of("Gstin","29AAACW6288M1Z6","LglNm","Sandbox Test Buyer","Pos","29","Addr1","Sandbox Buyer Address","Loc","Bengaluru","Pin",560001,"Stcd","29")),
    Map.entry("ItemList",java.util.List.of(Map.ofEntries(Map.entry("SlNo","1"),Map.entry("PrdDesc","Qualification test service"),Map.entry("IsServc","Y"),Map.entry("HsnCd","998513"),Map.entry("Qty",BigDecimal.ONE),Map.entry("Unit","NOS"),Map.entry("UnitPrice",taxable),Map.entry("TotAmt",taxable),Map.entry("Discount",BigDecimal.ZERO),Map.entry("AssAmt",taxable),Map.entry("GstRt",new BigDecimal("18.00")),Map.entry("IgstAmt",new BigDecimal("180.00")),Map.entry("TotItemVal",total)))),
    Map.entry("ValDtls",Map.of("AssVal",taxable,"CgstVal",BigDecimal.ZERO,"SgstVal",BigDecimal.ZERO,"IgstVal",new BigDecimal("180.00"),"CesVal",BigDecimal.ZERO,"StCesVal",BigDecimal.ZERO,"Discount",BigDecimal.ZERO,"OthChrg",BigDecimal.ZERO,"RndOffAmt",BigDecimal.ZERO,"TotInvVal",total))
  );
  JsonNode node=json.valueToTree(payload);
  if(fixture.equals("missing-document-number")) ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("DocDtls")).remove("No");
  if(fixture.equals("invalid-gstin")) ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("BuyerDtls")).put("Gstin","INVALID");
  if(fixture.equals("missing-items")) ((com.fasterxml.jackson.databind.node.ObjectNode)node).remove("ItemList");
  return node;
 }
 public String documentNumber(JsonNode fixture){return fixture.path("DocDtls").path("No").asText();}
 public String documentType(JsonNode fixture){return fixture.path("DocDtls").path("Typ").asText();}
}
