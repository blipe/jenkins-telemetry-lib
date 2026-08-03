package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import java.util.List;

@Extension
public final class TelemetryGlobalConfiguration extends GlobalConfiguration {
    private String configurationPath=""; private boolean automaticExportEnabled=true;
    public TelemetryGlobalConfiguration(){load();}
    public String getConfigurationPath(){return configurationPath;}
    @DataBoundSetter public void setConfigurationPath(String v){configurationPath=v==null?"":v.trim();save();}
    public boolean isAutomaticExportEnabled(){return automaticExportEnabled;}
    @DataBoundSetter public void setAutomaticExportEnabled(boolean v){automaticExportEnabled=v;save();}
    static TelemetryGlobalConfiguration current(){List<TelemetryGlobalConfiguration> c=Jenkins.get().getExtensionList(TelemetryGlobalConfiguration.class);return c.isEmpty()?null:c.get(0);}
}
