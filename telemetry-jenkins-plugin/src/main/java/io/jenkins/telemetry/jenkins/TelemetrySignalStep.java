package io.jenkins.telemetry.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.core.*;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import java.util.*;

public final class TelemetrySignalStep extends Step {
    private final String action; private Map<String,Object> data=Map.of();
    @DataBoundConstructor public TelemetrySignalStep(String action){this.action=Objects.requireNonNull(action);}
    public String getAction(){return action;} public Map<String,Object> getData(){return data;}
    @DataBoundSetter public void setData(Map<String,Object> data){this.data=data==null?Map.of():new LinkedHashMap<>(data);}
    @Override public StepExecution start(StepContext context){return new Execution(this,context);}

    private static final class Execution extends SynchronousNonBlockingStepExecution<Object> {
        private static final long serialVersionUID=1L; private final String action; private final Map<String,Object> data;
        Execution(TelemetrySignalStep step,StepContext context){super(context);action=step.action;data=new LinkedHashMap<>(step.data);}
        @Override protected Object run() throws Exception {
            Run<?,?> run=getContext().get(Run.class);TaskListener listener=getContext().get(TaskListener.class);EnvVars env=getContext().get(EnvVars.class);
            JenkinsTelemetryRuntime.RunSession session=JenkinsTelemetryRuntime.session(run,listener,env==null?new EnvVars():env);session.captureConsole(false);Object result=execute(session);session.save();return result;
        }
        private Object execute(JenkinsTelemetryRuntime.RunSession session){TelemetryEngine engine=session.engine();TelemetryRunAction state=session.action();return switch(action){
            case "release"->{ReleaseIdentity release=SignalCodec.release(resolveRelease(session,data));engine.release(release);state.release(release);yield Map.of("serviceName",release.serviceName());}
            case "log"->{engine.recordLog(Severity.parse(SignalCodec.stringOr(data,"severity","INFO")),SignalCodec.stringOr(data,"message",""),SignalCodec.attributes(data.get("attributes")),session.operationCorrelation(SignalCodec.string(data,"operationId")));yield null;}
            case "metric"->{engine.recordMetric(SignalCodec.required(data,"name"),MetricKind.valueOf(SignalCodec.stringOr(data,"kind","GAUGE").toUpperCase()),SignalCodec.decimal(data,"value",0),SignalCodec.stringOr(data,"unit",""),SignalCodec.stringOr(data,"description",""),SignalCodec.attributes(data.get("attributes")),session.operationCorrelation(SignalCodec.string(data,"operationId")));yield null;}
            case "operationStart"->start(session);
            case "operationEvent"->{DetachedOperation op=required(state).withEvent(new SpanEventData(engine.nowUnixNano(),SignalCodec.required(data,"name"),SignalCodec.attributes(data.get("attributes"))));state.replaceOperation(op);yield null;}
            case "operationAttribute"->{DetachedOperation op=required(state).withAttribute(SignalCodec.required(data,"name"),data.get("value"));state.replaceOperation(op);yield null;}
            case "operationEnd"->{end(session);yield null;}
            case "artifact"->{ArtifactRef artifact=SignalCodec.artifact(data);engine.artifact(artifact);state.artifact(artifact);yield artifact.telemetryAttributes();}
            case "event"->{String id=SignalCodec.string(data,"operationId");DetachedOperation op=id==null?null:state.operation(id);if(op!=null)state.replaceOperation(op.withEvent(new SpanEventData(engine.nowUnixNano(),SignalCodec.required(data,"name"),SignalCodec.attributes(data.get("attributes")))));engine.recordLog(Severity.INFO,SignalCodec.required(data,"name"),SignalCodec.attributes(data.get("attributes")).merge(Attributes.of("event.name",SignalCodec.required(data,"name"))),session.operationCorrelation(id));yield null;}
            case "deliveryManifestExport"->exportManifest(session);
            case "deliveryManifestImport"->importManifest(session);
            case "runtimeManifest"->runtime(engine);
            case "status"->status(engine.status());
            case "flush"->{engine.flush();yield status(engine.status());}
            default->throw new IllegalArgumentException("Unknown telemetry action: "+action);
        };}
        private Object start(JenkinsTelemetryRuntime.RunSession session){TelemetryRunAction state=session.action();DetachedOperation parent=null;String parentId=SignalCodec.string(data,"parentOperationId");if(parentId!=null)parent=state.operation(parentId);if(parent==null)parent=state.rootOperation();OperationSpec spec=OperationSpec.builder(SignalCodec.required(data,"name"),OperationKind.parse(SignalCodec.stringOr(data,"kind","CUSTOM"))).attributes(SignalCodec.map(data.get("attributes"))).build();DetachedOperation op=session.engine().startDetached(spec,parent==null?null:parent.traceId(),parent==null?null:parent.spanId());state.putOperation(op);return Map.of("operationId",op.operationId(),"traceId",op.traceId(),"spanId",op.spanId());}
        private void end(JenkinsTelemetryRuntime.RunSession session){TelemetryRunAction state=session.action();String id=SignalCodec.required(data,"operationId");DetachedOperation op=state.removeOperation(id);if(op==null)throw new IllegalArgumentException("Unknown telemetry operation: "+id);String text=SignalCodec.stringOr(data,"status","OK").toUpperCase();SpanStatus status="OK".equals(text)?SpanStatus.OK:SpanStatus.ERROR;String message=SignalCodec.stringOr(data,"message",text);if(status==SpanStatus.ERROR)op=op.withAttribute("error.type",SignalCodec.stringOr(data,"errorType","pipeline.error")).withAttribute("error.message",message);session.engine().completeDetached(op,status,message,session.correlationFor(op));}
        private DetachedOperation required(TelemetryRunAction state){String id=SignalCodec.required(data,"operationId");DetachedOperation op=state.operation(id);if(op==null)throw new IllegalArgumentException("Unknown telemetry operation: "+id);return op;}
        private Object exportManifest(JenkinsTelemetryRuntime.RunSession session){DetachedOperation root=session.action().rootOperation();if(root==null)throw new IllegalStateException("Pipeline root operation is unavailable");Correlation c=session.rootCorrelation();BuildProvenance p=new BuildProvenance(c.runId(),c.jobName(),c.buildNumber(),c.buildUrl(),root.traceId(),root.spanId(),SignalCodec.attributes(data.get("attributes")));DeliveryManifest m=session.engine().deliveryManifest(p,SignalCodec.attributes(data.get("attributes")));String json=DeliveryManifestCodec.encode(m);return Map.of("values",DeliveryManifestCodec.toMap(m),"json",json,"sha256",DeliveryManifestCodec.sha256(json));}
        private Object importManifest(JenkinsTelemetryRuntime.RunSession session){String json=SignalCodec.required(data,"json");DeliveryManifest m=DeliveryManifestCodec.decodeVerified(json,SignalCodec.string(data,"sha256"));session.engine().importDeliveryManifest(m);TelemetryRunAction state=session.action();state.importedDeliveryManifest(m);state.release(m.release());m.artifacts().forEach(state::artifact);SpanLinkData link=m.build().asSpanLink();if(state.rootOperation()!=null)state.rootOperation(state.rootOperation().withLink(link));return Map.of("schema",m.schema(),"serviceName",m.release().serviceName(),"artifactCount",m.artifacts().size(),"buildRunId",m.build().runId());}
        private Object runtime(TelemetryEngine engine){RuntimeManifest m=engine.runtimeManifest(SignalCodec.string(data,"environment"),SignalCodec.string(data,"deploymentId"));Map<String,Object> values=new LinkedHashMap<>();values.put("serviceName",m.serviceName());values.put("serviceVersion",m.serviceVersion());values.put("runId",m.runId());values.put("artifactDigest",m.artifactDigest());values.put("deploymentId",m.deploymentId());values.put("deploymentEnvironment",m.deploymentEnvironment());return Map.of("values",values,"json",MiniJson.write(values),"environment",m.openTelemetryEnvironment());}
        private static Map<String,Object> resolveRelease(JenkinsTelemetryRuntime.RunSession session,Map<String,Object> supplied){Map<String,Object> r=new LinkedHashMap<>(supplied);EnvVars env=session.env();String full=session.run().getParent().getFullName();String name=full,ns=null;int slash=full.lastIndexOf('/');if(slash>=0){ns=full.substring(0,slash);name=full.substring(slash+1);}putIfBlank(r,"serviceName",first(env.get("SERVICE_NAME"),env.get("JOB_BASE_NAME"),name));putIfBlank(r,"serviceNamespace",first(env.get("SERVICE_NAMESPACE"),ns));putIfBlank(r,"sourceRepository",first(env.get("GIT_URL"),env.get("GIT_URL_1")));putIfBlank(r,"sourceRevision",env.get("GIT_COMMIT"));putIfBlank(r,"sourceRef",first(env.get("BRANCH_NAME"),env.get("GIT_BRANCH")));putIfBlank(r,"buildVersion",first(env.get("BUILD_NUMBER"),Integer.toString(session.run().getNumber())));putIfBlank(r,"serviceVersion",env.get("SERVICE_VERSION"));if(SignalCodec.string(r,"serviceVersion")==null){String b=SignalCodec.stringOr(r,"buildVersion","0"),rev=SignalCodec.string(r,"sourceRevision");r.put("serviceVersion",rev==null?b:b+"-"+rev.substring(0,Math.min(12,rev.length())));}return r;}
        private static void putIfBlank(Map<String,Object> target,String key,String value){String current=SignalCodec.string(target,key);if((current==null||current.isBlank())&&value!=null&&!value.isBlank())target.put(key,value);}private static String first(String...values){for(String v:values)if(v!=null&&!v.isBlank())return v;return null;}
        private static Map<String,Object> status(TelemetryStatus s){return Map.of("journalBytes",s.journalBytes(),"logRecords",s.logRecords(),"metricRecords",s.metricRecords(),"traceRecords",s.traceRecords(),"droppedRecords",s.droppedRecords(),"collectorLag",s.collectorLag());}
    }
    @Extension public static final class DescriptorImpl extends StepDescriptor {
        @Override public String getFunctionName(){return "telemetrySignal";} @Override public String getDisplayName(){return "Emit Jenkins delivery telemetry";}
        @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class,EnvVars.class);}
    }
}
