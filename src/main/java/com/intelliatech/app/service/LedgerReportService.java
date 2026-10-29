package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.response.LedgerStatementResponse;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.security.TenantContext;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LedgerReportService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final JdbcTemplate jdbc;
    private final TenantContext tenants;
    private final ObjectMapper json;
    private final FileStorageService storage;

    @Transactional(readOnly = true)
    public List<Map<String,Object>> parties(String partyType, String search) {
        long org = tenants.requireOrganizationId();
        String q = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
        if (isCustomer(partyType)) return jdbc.queryForList("""
            SELECT id,record_number code,party_name name,party_email email,party_phone phone,party_city address
            FROM business_records WHERE organization_id=? AND module='sales' AND type='customers'
            AND (LOWER(party_name) LIKE ? OR LOWER(record_number) LIKE ? OR LOWER(COALESCE(party_email,'')) LIKE ?)
            ORDER BY party_name LIMIT 100
            """, org,q,q,q);
        return jdbc.queryForList("""
            SELECT id,vendor_number code,vendor_name name,email,COALESCE(phone,mobile) phone,
              CONCAT_WS(', ',billing_address_line1,billing_address_line2,billing_city,billing_state,billing_pincode,billing_country) address
            FROM vendors WHERE organization_id=? AND status='ACTIVE'
            AND (LOWER(vendor_name) LIKE ? OR LOWER(vendor_number) LIKE ? OR LOWER(COALESCE(email,'')) LIKE ?)
            ORDER BY vendor_name LIMIT 100
            """, org,q,q,q);
    }

    @Transactional(readOnly = true)
    public LedgerStatementResponse statement(String partyType, long partyId, LocalDate from, LocalDate to,
                                              String currency, String transactionType, boolean includeOpening) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("A valid From and To date is required.");
        String code = normalizeCurrency(currency);
        long org = tenants.requireOrganizationId();
        LedgerStatementResponse.Party party = party(partyType, partyId, org);
        List<Entry> entries = isCustomer(partyType) ? customerEntries(org, party, code) : vendorEntries(org, party, code);
        Calculation calculation = calculate(entries, from, to, transactionType, includeOpening);
        return new LedgerStatementResponse(isCustomer(partyType)?"CUSTOMER":"VENDOR",party,organization(org),from,to,code,
                calculation.opening(),calculation.debit(),calculation.credit(),calculation.closing(),calculation.lines());
    }

    public byte[] pdf(LedgerStatementResponse data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4,30,30,28,34);
            PdfWriter writer=PdfWriter.getInstance(doc,out);
            writer.setPageEvent(new LedgerPdfFooter(data.organization().name()));
            doc.open();
            Color navy=new Color(6,19,74), blue=new Color(37,99,235), green=new Color(5,150,105), muted=new Color(100,116,139), line=new Color(203,213,225), pale=new Color(248,250,252), paleBlue=new Color(239,246,255);
            Font title = new Font(Font.HELVETICA,15,Font.BOLD,navy);
            Font section = new Font(Font.HELVETICA,9,Font.BOLD,navy);
            Font label = new Font(Font.HELVETICA,6.5f,Font.BOLD,muted);
            Font value = new Font(Font.HELVETICA,7.5f,Font.BOLD,navy);
            Font h = new Font(Font.HELVETICA,6.5f,Font.BOLD,navy);
            Font body = new Font(Font.HELVETICA,6.5f,Font.NORMAL,new Color(30,41,59));
            Font balanceFont = new Font(Font.HELVETICA,6.5f,Font.BOLD,green);
            PdfPTable head = new PdfPTable(new float[]{1,2.2f}); head.setWidthPercentage(100);
            PdfPCell logo = cell("",body); logo.setBorder(Rectangle.NO_BORDER);
            byte[] logoBytes = logo(data.organization().logoUrl());
            if (logoBytes != null) { Image image=Image.getInstance(logoBytes); image.scaleToFit(105,40); logo.addElement(image); }
            else logo.addElement(new Paragraph(data.organization().name(),section));
            head.addCell(logo);
            PdfPCell company=cell(data.organization().name()+"\n"+nullSafe(data.organization().address())+"\nGSTIN: "+nullSafe(data.organization().gstin())+" | Phone: "+nullSafe(data.organization().phone()),body);
            company.setBorder(Rectangle.NO_BORDER); company.setHorizontalAlignment(Element.ALIGN_RIGHT); head.addCell(company); doc.add(head);
            Paragraph heading=new Paragraph((data.partyType().equals("CUSTOMER")?"Customer":"Vendor")+" Ledger Statement",title); heading.setAlignment(Element.ALIGN_CENTER); heading.setSpacingBefore(7); heading.setSpacingAfter(10); doc.add(heading);

            PdfPTable details=new PdfPTable(new float[]{1.55f,1}); details.setWidthPercentage(100); details.setSpacingAfter(10);
            PdfPCell partyCell=cell("",body);partyCell.setPadding(9);partyCell.setBorderColor(line);partyCell.setBackgroundColor(pale);
            partyCell.addElement(new Paragraph(data.party().name(),section));
            partyCell.addElement(new Paragraph("Address: "+nullSafe(data.party().address())+"\nEmail: "+nullSafe(data.party().email())+"\nPhone: "+nullSafe(data.party().phone()),body));details.addCell(partyCell);
            String generated=LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
            PdfPCell periodCell=cell("",body);periodCell.setPadding(9);periodCell.setBorderColor(line);periodCell.setBackgroundColor(pale);
            periodCell.addElement(new Paragraph("Statement Period",label));periodCell.addElement(new Paragraph(formatDate(data.fromDate())+" - "+formatDate(data.toDate()),value));
            periodCell.addElement(new Paragraph("Currency",label));periodCell.addElement(new Paragraph(data.currency(),value));
            periodCell.addElement(new Paragraph("Generated On",label));periodCell.addElement(new Paragraph(generated,value));details.addCell(periodCell);doc.add(details);

            PdfPTable summary=new PdfPTable(4);summary.setWidthPercentage(100);summary.setSpacingAfter(10);
            addSummary(summary,"Opening Balance",amount(data.openingBalance())+" "+side(data.openingBalance()),label,value,line,paleBlue);
            addSummary(summary,"Total Debit",amount(data.totalDebit()),label,value,line,paleBlue);
            addSummary(summary,"Total Credit",amount(data.totalCredit()),label,value,line,paleBlue);
            addSummary(summary,"Closing Balance",amount(data.closingBalance())+" "+side(data.closingBalance()),label,value,line,paleBlue);
            doc.add(summary);

            PdfPTable table=new PdfPTable(new float[]{.45f,1.15f,2.15f,1.25f,1.45f,1.2f,1.2f,1.45f}); table.setWidthPercentage(100);table.setHeaderRows(1);
            for(String text:List.of("#","Date","Particulars","Type","Ref. No.","Debit ("+data.currency()+")","Credit ("+data.currency()+")","Balance ("+data.currency()+")")){PdfPCell c=cell(text,h);c.setPadding(5);c.setBackgroundColor(paleBlue);c.setBorderColor(line);if(text.startsWith("Debit")||text.startsWith("Credit")||text.startsWith("Balance"))c.setHorizontalAlignment(Element.ALIGN_RIGHT);table.addCell(c);}
            int i=1; for(var row:data.transactions()){
                table.addCell(pdfDataCell(String.valueOf(i++),body,line,false));table.addCell(pdfDataCell(formatDate(row.date()),body,line,false));table.addCell(pdfDataCell(row.particulars(),body,line,false));table.addCell(pdfDataCell(row.transactionType(),body,line,false));table.addCell(pdfDataCell(row.referenceNumber(),body,line,false));table.addCell(pdfDataCell(row.debit().signum()==0?"-":amount(row.debit()),body,line,true));table.addCell(pdfDataCell(row.credit().signum()==0?"-":amount(row.credit()),body,line,true));table.addCell(pdfDataCell(amount(row.runningBalance())+" "+row.balanceSide(),balanceFont,line,true));
            }
            PdfPCell total=pdfDataCell("Total",value,line,true);total.setColspan(5);total.setBackgroundColor(paleBlue);table.addCell(total);
            for(String totalValue:List.of(amount(data.totalDebit()),amount(data.totalCredit()),amount(data.closingBalance())+" "+side(data.closingBalance()))){PdfPCell c=pdfDataCell(totalValue,value,line,true);c.setBackgroundColor(paleBlue);table.addCell(c);}
            doc.add(table); doc.close(); return out.toByteArray();
        } catch(Exception ex){throw new IllegalStateException("Unable to generate ledger PDF.",ex);}
    }

    public byte[] excel(LedgerStatementResponse data) {
        try(XSSFWorkbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet ledger=wb.createSheet("Ledger Statement"); String[] headers={"Date","Particulars","Transaction Type","Reference","Debit","Credit","Running Balance","Dr/Cr"};
            Row hr=ledger.createRow(0); for(int i=0;i<headers.length;i++)hr.createCell(i).setCellValue(headers[i]);
            CellStyle moneyStyle=wb.createCellStyle(); moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            int row=1; for(var line:data.transactions()){Row r=ledger.createRow(row++);r.createCell(0).setCellValue(line.date().toString());r.createCell(1).setCellValue(safeExcel(line.particulars()));r.createCell(2).setCellValue(line.transactionType());r.createCell(3).setCellValue(safeExcel(line.referenceNumber()));for(int c=4;c<=6;c++){Cell cell=r.createCell(c);cell.setCellValue((c==4?line.debit():c==5?line.credit():line.runningBalance()).doubleValue());cell.setCellStyle(moneyStyle);}r.createCell(7).setCellValue(line.balanceSide());}
            Sheet summary=wb.createSheet("Summary"); Object[][] values={{"Organization",data.organization().name()},{"Party",data.party().name()},{"Period",data.fromDate()+" to "+data.toDate()},{"Currency",data.currency()},{"Opening Balance",data.openingBalance()},{"Total Debit",data.totalDebit()},{"Total Credit",data.totalCredit()},{"Closing Balance",data.closingBalance()}};
            for(int i=0;i<values.length;i++){Row r=summary.createRow(i);r.createCell(0).setCellValue(String.valueOf(values[i][0]));if(values[i][1] instanceof BigDecimal v){r.createCell(1).setCellValue(v.doubleValue());r.getCell(1).setCellStyle(moneyStyle);}else r.createCell(1).setCellValue(safeExcel(String.valueOf(values[i][1])));}
            for(int i=0;i<8;i++)ledger.autoSizeColumn(i);summary.autoSizeColumn(0);summary.autoSizeColumn(1);wb.write(out);return out.toByteArray();
        }catch(Exception ex){throw new IllegalStateException("Unable to generate ledger Excel.",ex);}
    }

    private List<Entry> customerEntries(long org, LedgerStatementResponse.Party party, String currency) {
        List<Entry> result=new ArrayList<>();
        jdbc.query("SELECT id,record_date,record_number,amount,status,notes FROM business_records WHERE organization_id=? AND module='sales' AND type='invoices' AND (JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(notes) THEN notes ELSE '{}' END,'$.customerId'))=? OR LOWER(party_name)=LOWER(?)) AND UPPER(status) NOT IN ('DRAFT','VOID','CANCELLED')",
                rs->{String c=currency(rs.getString("notes"),"INR");if(c.equals(currency))result.add(entry("I"+rs.getLong("id"),rs.getDate("record_date").toLocalDate(),10,"Sales Invoice","Invoice",rs.getString("record_number"),money(rs.getBigDecimal("amount")),ZERO,rs.getString("status"),"/sales/invoices/"+rs.getLong("id")));},org,String.valueOf(party.id()),party.name());
        jdbc.query("SELECT id,payment_date,payment_number,gross_amount_received,reversed,reversed_at FROM invoice_payments WHERE organization_id=? AND customer_id=? AND currency_code=?",
                rs->{LocalDate d=rs.getDate("payment_date").toLocalDate();BigDecimal a=money(rs.getBigDecimal("gross_amount_received"));result.add(entry("P"+rs.getLong("id"),d,20,"Payment Received","Payment",rs.getString("payment_number"),ZERO,a,"POSTED","/sales/payments-received/"+rs.getLong("id")));if(rs.getBoolean("reversed")&&rs.getTimestamp("reversed_at")!=null)result.add(entry("PR"+rs.getLong("id"),rs.getTimestamp("reversed_at").toLocalDateTime().toLocalDate(),40,"Payment Reversal","Payment Reversal",rs.getString("payment_number"),a,ZERO,"REVERSED","/sales/payments-received/"+rs.getLong("id")));},org,party.id(),currency);
        jdbc.query("SELECT cn.id,cn.record_date,cn.record_number,cn.status,cn.notes,SUM(link.amount_applied) amount FROM invoice_credit_note_links link JOIN business_records cn ON cn.id=link.credit_note_id JOIN business_records inv ON inv.id=link.invoice_id WHERE cn.organization_id=? AND UPPER(cn.status) NOT IN ('DRAFT','VOID','CANCELLED') AND (JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(inv.notes) THEN inv.notes ELSE '{}' END,'$.customerId'))=? OR LOWER(inv.party_name)=LOWER(?)) GROUP BY cn.id,cn.record_date,cn.record_number,cn.status,cn.notes",
                rs->{if(currency(rs.getString("notes"),"INR").equals(currency)){BigDecimal a=money(rs.getBigDecimal("amount"));result.add(entry("C"+rs.getLong("id"),rs.getDate("record_date").toLocalDate(),30,"Credit Note Applied","Credit Note",rs.getString("record_number"),ZERO,a,rs.getString("status"),"/sales/credit-notes/"+rs.getLong("id")));}},org,String.valueOf(party.id()),party.name());
        return result;
    }

    private List<Entry> vendorEntries(long org, LedgerStatementResponse.Party party, String currency) {
        List<Entry> result=new ArrayList<>();
        jdbc.query("SELECT id,bill_date,bill_number,total_amount,status FROM bills WHERE organization_id=? AND vendor_id=? AND currency_code=? AND deleted=FALSE AND UPPER(status) NOT IN ('DRAFT','VOID','CANCELLED')",
                rs->{BigDecimal a=money(rs.getBigDecimal("total_amount"));result.add(entry("B"+rs.getLong("id"),rs.getDate("bill_date").toLocalDate(),10,"Purchase Bill","Bill",rs.getString("bill_number"),ZERO,a,rs.getString("status"),"/purchases/bills/"+rs.getLong("id")));},org,party.id(),currency);
        jdbc.query("SELECT p.id,p.payment_date,p.payment_number,p.amount,p.status,p.reversed_at FROM bill_payments p JOIN bills b ON b.id=p.bill_id WHERE b.organization_id=? AND b.vendor_id=? AND b.currency_code=?",
                rs->{BigDecimal a=money(rs.getBigDecimal("amount"));result.add(entry("BP"+rs.getLong("id"),rs.getDate("payment_date").toLocalDate(),20,"Payment Made","Payment",rs.getString("payment_number"),a,ZERO,rs.getString("status"),"/purchases/bills"));if("REVERSED".equals(rs.getString("status"))&&rs.getTimestamp("reversed_at")!=null)result.add(entry("BPR"+rs.getLong("id"),rs.getTimestamp("reversed_at").toLocalDateTime().toLocalDate(),40,"Payment Reversal","Payment Reversal",rs.getString("payment_number"),ZERO,a,"REVERSED","/purchases/bills"));},org,party.id(),currency);
        jdbc.query("SELECT id,record_date,record_number,amount,status,notes FROM business_records WHERE organization_id=? AND module='purchases' AND type IN ('vendorCredits','credits') AND LOWER(party_name)=LOWER(?) AND UPPER(status) NOT IN ('DRAFT','VOID','CANCELLED')",
                rs->{if(currency(rs.getString("notes"),currency).equals(currency)){BigDecimal a=money(rs.getBigDecimal("amount"));result.add(entry("VC"+rs.getLong("id"),rs.getDate("record_date").toLocalDate(),30,"Vendor Credit","Vendor Credit",rs.getString("record_number"),a,ZERO,rs.getString("status"),"/purchases/vendors/"+party.id()));}},org,party.name());
        return result;
    }

    private LedgerStatementResponse.Party party(String type,long id,long org){List<Map<String,Object>> rows;if(isCustomer(type))rows=jdbc.queryForList("SELECT id,record_number code,party_name name,party_email email,party_phone phone,party_city address FROM business_records WHERE organization_id=? AND module='sales' AND type='customers' AND id=?",org,id);else rows=jdbc.queryForList("SELECT id,vendor_number code,vendor_name name,email,COALESCE(phone,mobile) phone,CONCAT_WS(', ',billing_address_line1,billing_address_line2,billing_city,billing_state,billing_pincode,billing_country) address FROM vendors WHERE organization_id=? AND id=?",org,id);if(rows.isEmpty())throw new ResourceNotFoundException("Customer or vendor not found.");Map<String,Object> r=rows.get(0);return new LedgerStatementResponse.Party(((Number)r.get("id")).longValue(),str(r.get("code")),str(r.get("name")),str(r.get("email")),str(r.get("phone")),str(r.get("address")));}
    private LedgerStatementResponse.Organization organization(long org){Map<String,Object> r=jdbc.queryForMap("SELECT o.organization_name name,o.gstin,s.contact_email email,s.phone,CONCAT_WS(', ',s.address_line1,s.address_line2,s.city,s.state_name,s.postal_code) address FROM organizations o LEFT JOIN organization_settings s ON s.organization_id=o.id WHERE o.id=?",org);String notes=jdbc.query("SELECT notes FROM business_records WHERE organization_id=? AND module='settings' AND type='branding' ORDER BY record_date DESC,id DESC LIMIT 1",rs->rs.next()?rs.getString(1):null,org);return new LedgerStatementResponse.Organization(str(r.get("name")),str(r.get("address")),str(r.get("gstin")),str(r.get("email")),str(r.get("phone")),jsonText(notes,"logoUrl"));}
    private byte[] logo(String url){if(!StringUtils.hasText(url))return null;for(String marker:List.of("/branding/logo/","/branding-assets/")){int i=url.indexOf(marker);if(i>=0)try{return storage.download("branding/logo/"+url.substring(i+marker.length()).split("[?#]",2)[0]).content();}catch(Exception ignored){return null;}}return null;}
    private String currency(String notes,String fallback){String value=jsonText(notes,"currencyCode");return normalizeCurrency(StringUtils.hasText(value)?value:fallback);}
    private String jsonText(String notes,String key){try{JsonNode n=json.readTree(notes);return n==null?null:n.path(key).asText(null);}catch(Exception ignored){return null;}}
    private boolean isCustomer(String t){return "CUSTOMER".equalsIgnoreCase(t);}
    private String normalizeCurrency(String c){String v=StringUtils.hasText(c)?c.trim().toUpperCase(Locale.ROOT):"INR";return v.contains(" ")?v.substring(0,v.indexOf(' ')):v;}
    private boolean matches(String type,String filter){return !StringUtils.hasText(filter)||"ALL".equalsIgnoreCase(filter)||type.equalsIgnoreCase(filter);}
    static Calculation calculate(List<Entry> source, LocalDate from, LocalDate to, String transactionType, boolean includeOpening) {
        List<Entry> entries = new ArrayList<>(source);
        entries.sort(Comparator.comparing(Entry::date).thenComparing(Entry::sequence).thenComparing(Entry::id));
        BigDecimal opening = includeOpening ? entries.stream().filter(e -> e.date().isBefore(from)).map(Entry::signed).reduce(ZERO, BigDecimal::add) : ZERO;
        BigDecimal running = opening, debit = ZERO, credit = ZERO;
        List<LedgerStatementResponse.Line> lines = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.date().isBefore(from) || entry.date().isAfter(to) || !matchesType(entry.type(), transactionType)) continue;
            running = scaled(running.add(entry.debit()).subtract(entry.credit()));
            debit = debit.add(entry.debit());
            credit = credit.add(entry.credit());
            lines.add(new LedgerStatementResponse.Line(entry.id(),entry.date(),entry.particulars(),entry.type(),entry.reference(),
                    entry.debit(),entry.credit(),running,balanceSide(running),entry.status(),entry.viewPath()));
        }
        return new Calculation(scaled(opening),scaled(debit),scaled(credit),scaled(running),List.copyOf(lines));
    }
    private static boolean matchesType(String type,String filter){return !StringUtils.hasText(filter)||"ALL".equalsIgnoreCase(filter)||type.equalsIgnoreCase(filter);}
    private static BigDecimal scaled(BigDecimal value){return (value==null?ZERO:value).setScale(2,RoundingMode.HALF_UP);}
    private static String balanceSide(BigDecimal value){return value.signum()<0?"Cr":"Dr";}
    private Entry entry(String id,LocalDate d,long seq,String p,String t,String ref,BigDecimal debit,BigDecimal credit,String status,String path){return new Entry(id,d,seq,p,t,ref,money(debit),money(credit),status,path);}
    private BigDecimal money(BigDecimal v){return (v==null?ZERO:v).setScale(2,RoundingMode.HALF_UP);}
    private String side(BigDecimal v){return v.signum()<0?"Cr":"Dr";}
    private String amount(BigDecimal v){NumberFormat formatter=NumberFormat.getNumberInstance(new Locale("en","IN"));formatter.setMinimumFractionDigits(2);formatter.setMaximumFractionDigits(2);return formatter.format(money(v).abs());}
    private String safeExcel(String v){return v!=null&&v.matches("^[=+@-].*")?"'"+v:nullSafe(v);}
    private String nullSafe(String v){return v==null?"":v;}
    private String str(Object v){return v==null?"":String.valueOf(v);}
    private PdfPCell cell(String text,Font f){PdfPCell c=new PdfPCell(new Phrase(nullSafe(text),f));c.setPadding(5);return c;}
    private PdfPCell pdfDataCell(String text,Font font,Color border,boolean right){PdfPCell c=cell(text,font);c.setBorderColor(border);c.setVerticalAlignment(Element.ALIGN_MIDDLE);if(right)c.setHorizontalAlignment(Element.ALIGN_RIGHT);return c;}
    private void addSummary(PdfPTable table,String heading,String text,Font label,Font value,Color border,Color background){PdfPCell c=cell("",value);c.setPadding(8);c.setBorderColor(border);c.setBackgroundColor(background);c.addElement(new Paragraph(heading,label));c.addElement(new Paragraph(text,value));table.addCell(c);}
    private String formatDate(LocalDate date){return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));}
    static record Entry(String id,LocalDate date,long sequence,String particulars,String type,String reference,BigDecimal debit,BigDecimal credit,String status,String viewPath){BigDecimal signed(){return debit.subtract(credit);}}
    static record Calculation(BigDecimal opening,BigDecimal debit,BigDecimal credit,BigDecimal closing,List<LedgerStatementResponse.Line> lines){}
    private static final class LedgerPdfFooter extends PdfPageEventHelper {
        private final String organizationName;
        private LedgerPdfFooter(String organizationName){this.organizationName=organizationName;}
        @Override public void onEndPage(PdfWriter writer, Document document){
            Font font=new Font(Font.HELVETICA,7,Font.NORMAL,new Color(100,116,139));
            PdfPTable footer=new PdfPTable(2);footer.setTotalWidth(document.right()-document.left());
            PdfPCell left=new PdfPCell(new Phrase(organizationName+" • This is a system-generated statement.",font));
            PdfPCell right=new PdfPCell(new Phrase("Page "+writer.getPageNumber(),font));
            left.setBorder(Rectangle.NO_BORDER);right.setBorder(Rectangle.NO_BORDER);right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            footer.addCell(left);footer.addCell(right);footer.writeSelectedRows(0,-1,document.left(),document.bottom()-8,writer.getDirectContent());
        }
    }
}
