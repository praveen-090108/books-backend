package com.intelliatech.app.einvoice.qualification.validation;
import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import org.springframework.stereotype.Component;
@Component public class QualificationResultValidator { public String classify(Scenario s,Execution e){if(s.expected()==Expected.SUCCESS)return e.success()?"PASSED":"UNEXPECTED_FAILURE";return !e.success()&&e.httpStatus()>0?"EXPECTED_FAILURE":"UNEXPECTED_FAILURE";} }
