package com.intelliatech.app.einvoice.qualification.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qualification.runner")
public class QualificationProperties {
    private boolean enabled;
    private boolean executionPermitted;
    private boolean runOnStartup;
    private String suite = "ALL";
    private String scenarioPlan = "";
    private int maximumOperations = 25;
    private long delayMillis = 1000;
    private Path reportDirectory = Path.of("reports/ey-sandbox-qualification");
    private List<String> allowedSandboxHosts = List.of("einvapisandbox.irp5.in", "sandbox.einvoice5.gst.gov.in");
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public boolean isExecutionPermitted(){return executionPermitted;} public void setExecutionPermitted(boolean v){executionPermitted=v;}
    public boolean isRunOnStartup(){return runOnStartup;} public void setRunOnStartup(boolean v){runOnStartup=v;}
    public String getSuite(){return suite;} public void setSuite(String v){suite=v;}
    public String getScenarioPlan(){return scenarioPlan;} public void setScenarioPlan(String v){scenarioPlan=v;}
    public int getMaximumOperations(){return maximumOperations;} public void setMaximumOperations(int v){maximumOperations=v;}
    public long getDelayMillis(){return delayMillis;} public void setDelayMillis(long v){delayMillis=v;}
    public Path getReportDirectory(){return reportDirectory;} public void setReportDirectory(Path v){reportDirectory=v;}
    public List<String> getAllowedSandboxHosts(){return allowedSandboxHosts;} public void setAllowedSandboxHosts(List<String> v){allowedSandboxHosts=v;}
}
