package com.intelliatech.app.einvoice.qualification.runner;
import static com.intelliatech.app.einvoice.qualification.dto.QualificationModels.Suite;
import com.intelliatech.app.einvoice.qualification.config.QualificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class QualificationCommandLineRunner implements CommandLineRunner {
 private final QualificationProperties properties; private final SandboxQualificationRunner runner;
 @Override public void run(String...args){if(properties.isRunOnStartup()){String plan=properties.getScenarioPlan();if(plan!=null&&!plan.isBlank())runner.runPlan(plan);else runner.run(Suite.valueOf(properties.getSuite().trim().toUpperCase()));}}
}
