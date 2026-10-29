package com.intelliatech.app.einvoice.qualification.service;
import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.*;
import com.intelliatech.app.einvoice.qualification.repository.QualificationStore;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor public class QualificationProgressService {
 private final QualificationStore store;
 public Progress progress(long runId){Map<String,Object> local=new LinkedHashMap<>(store.run(runId));local.put("operationCounts",store.results(runId).stream().collect(java.util.stream.Collectors.groupingBy(r->String.valueOf(r.get("api_name")),java.util.stream.Collectors.groupingBy(r->String.valueOf(r.get("result_status")),java.util.stream.Collectors.counting()))));return new Progress(local,store.portal());}
 public void updatePortal(Api api,int success,int failure,String source,String user){if(success<0||failure<0)throw new IllegalArgumentException("Portal counts cannot be negative");store.portal(api.name(),success,failure,source,user);}
}
