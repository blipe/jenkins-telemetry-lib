package io.jenkins.telemetry.jenkins;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.core.*;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class JenkinsTelemetryRuntime {
    private static final Map<String, RunSession> SESSIONS = new ConcurrentHashMap<>();
    private JenkinsTelemetryRuntime() { }

    static synchronized RunSession session(Run<?, ?> run, TaskListener listener, EnvVars env) throws IOException {
        String key = run.getExternalizableId();
        RunSession existing = SESSIONS.get(key);
        if (existing != null) return existing;
        TelemetryRunAction action = run.getAction(TelemetryRunAction.class);
        if (action == null) { action = new TelemetryRunAction(); run.addAction(action); }
        TelemetryConfiguration configuration = loadConfiguration();
        Correlation correlation = baseCorrelation(run, env);
        Path journal = run.getRootDir().toPath().resolve("telemetry").resolve("journal");
        TelemetryEngine engine = TelemetryEngine.builder(journal)
            .configuration(configuration).correlation(correlation)
            .resource("jenkins.job.full_name", run.getParent().getFullName())
            .resource("jenkins.controller.url", Jenkins.get().getRootUrl())
            .collectorFactories(collectorFactories()).build();
        if (action.importedDeliveryManifest() != null) engine.restoreDeliveryManifest(action.importedDeliveryManifest());
        else { engine.restoreRelease(action.release()); action.artifacts().values().forEach(engine::restoreArtifact); }
        RunSession created = new RunSession(run, listener, env, action, engine, correlation);
        created.ensureRootStarted(); SESSIONS.put(key, created); return created;
    }

    static synchronized void started(Run<?, ?> run, TaskListener listener) {
        try { session(run, listener, new EnvVars()).ensureRootStarted(); run.save(); }
        catch (Exception e) { listener.getLogger().println("[jenkins-telemetry] Initialization failed: " + e); }
    }

    static synchronized void completed(Run<?, ?> run, TaskListener listener) {
        try {
            RunSession session = session(run, listener, new EnvVars());
            session.captureConsole(true); session.completeOpenOperations(); session.completeRoot(run.getResult());
            Path journal = run.getRootDir().toPath().resolve("telemetry").resolve("journal");
            session.engine.close(); PendingJournalRegistry.register(journal); run.save(); TelemetrySpoolDrainer.requestSoon();
        } catch (Exception e) {
            listener.getLogger().println("[jenkins-telemetry] Completion export failed; journal is retained: " + e);
        } finally { SESSIONS.remove(run.getExternalizableId()); }
    }

    static java.util.List<TelemetryCollectorFactory> collectorFactories() {
        java.util.List<TelemetryCollectorFactory> result = new java.util.ArrayList<>();
        result.addAll(Jenkins.get().getExtensionList(JenkinsTelemetryCollectorFactory.class));
        if (result.stream().noneMatch(factory -> "file".equals(factory.type()))) result.add(new BuiltinFileCollectorFactory());
        if (result.stream().noneMatch(factory -> "http-json".equals(factory.type()))) result.add(new BuiltinHttpJsonCollectorFactory());
        if (result.stream().noneMatch(factory -> "otlp-http-json".equals(factory.type()))) result.add(new BuiltinOtlpJsonCollectorFactory());
        return result;
    }

    static TelemetryConfiguration loadConfiguration() throws IOException {
        JenkinsCredentialPlaceholder.install();
        String configured = System.getProperty("jenkins.telemetry.config");
        TelemetryGlobalConfiguration global = TelemetryGlobalConfiguration.current();
        if ((configured == null || configured.isBlank()) && global != null) configured = global.getConfigurationPath();
        Path path = configured == null || configured.isBlank()
            ? Jenkins.get().getRootDir().toPath().resolve("jenkins-telemetry.json") : Path.of(configured);
        TelemetryConfiguration configuration = TelemetryConfiguration.fromJson(path);
        return global == null ? configuration : configuration.withAutoExport(global.isAutomaticExportEnabled() && configuration.autoExport());
    }

    private static Correlation baseCorrelation(Run<?, ?> run, EnvVars env) {
        String rootUrl = Jenkins.get().getRootUrl(); String buildUrl = env == null ? null : env.get("BUILD_URL");
        if ((buildUrl == null || buildUrl.isBlank()) && rootUrl != null) buildUrl = rootUrl + run.getUrl();
        return new Correlation(run.getExternalizableId(), run.getParent().getFullName(), Integer.toString(run.getNumber()),
            buildUrl, null, null, null, env == null ? null : env.get("GIT_COMMIT"), null, null, null, null, null);
    }

    static final class RunSession {
        private final Run<?, ?> run; private final TaskListener listener; private final EnvVars env;
        private final TelemetryRunAction action; private final TelemetryEngine engine; private final Correlation baseCorrelation;
        private RunSession(Run<?, ?> run, TaskListener listener, EnvVars env, TelemetryRunAction action,
                           TelemetryEngine engine, Correlation baseCorrelation) {
            this.run=run; this.listener=listener; this.env=env; this.action=action; this.engine=engine; this.baseCorrelation=baseCorrelation;
        }
        TelemetryEngine engine(){return engine;} Run<?,?> run(){return run;} EnvVars env(){return env;} TelemetryRunAction action(){return action;}
        Correlation rootCorrelation(){ DetachedOperation root=action.rootOperation(); Correlation c=action.release()==null?baseCorrelation:baseCorrelation.withRelease(action.release()); return root==null?c:c.withTrace(root.traceId(),root.spanId(),root.name()); }
        Correlation operationCorrelation(String id){ if(id==null||id.isBlank())return rootCorrelation(); DetachedOperation op=action.operation(id); return op==null?rootCorrelation():correlationFor(op); }
        Correlation correlationFor(DetachedOperation op){ Correlation c=action.release()==null?baseCorrelation:baseCorrelation.withRelease(action.release()); return c.withTrace(op.traceId(),op.spanId(),op.name()); }
        void ensureRootStarted(){ if(action.rootOperation()!=null)return; DetachedOperation root=engine.startDetached(OperationSpec.builder("Jenkins pipeline",OperationKind.PIPELINE)
            .attribute("cicd.pipeline.name",run.getParent().getFullName()).attribute("cicd.pipeline.run.id",run.getExternalizableId())
            .attribute("cicd.pipeline.run.number",run.getNumber()).build(),null,null); action.rootOperation(root);
            engine.recordLog(Severity.INFO,"Jenkins pipeline started",Attributes.of("event.name","pipeline.started"),rootCorrelation()); }
        void captureConsole(boolean completed){ try{ int max=Integer.getInteger("jenkins.telemetry.maxConsoleLineCharacters",65536);
            ConsoleCaptureOutputStream out=new ConsoleCaptureOutputStream(engine,action,rootCorrelation(),max);
            long next=run.getLogText().writeLogTo(action.consoleOffset(),out); out.finish(completed); action.consoleOffset(next);
        }catch(IOException e){ engine.recordLog(Severity.WARN,"Console capture failed",Attributes.builder().put("error.type",e.getClass().getName()).put("error.message",e.getMessage()).build(),rootCorrelation()); }}
        void completeOpenOperations(){ for(Map.Entry<String,DetachedOperation> entry:action.openOperations().entrySet()){
            DetachedOperation op=action.removeOperation(entry.getKey()); if(op!=null){ DetachedOperation failed=op.withAttribute("error.type","jenkins.pipeline.interrupted")
            .withAttribute("error.message","Pipeline completed while operation remained open"); engine.completeDetached(failed,SpanStatus.ERROR,"Pipeline completed while operation remained open",correlationFor(failed)); }}}
        void completeRoot(Result result){ if(action.rootCompleted())return; DetachedOperation root=action.rootOperation(); if(root==null)return;
            boolean success=result==null||result.isBetterOrEqualTo(Result.SUCCESS); SpanStatus status=success?SpanStatus.OK:SpanStatus.ERROR;
            String message=result==null?"UNKNOWN":result.toString(); DetachedOperation completed=root.withAttribute("cicd.pipeline.run.result",message.toLowerCase());
            engine.completeDetached(completed,status,message,rootCorrelation()); action.rootCompleted(true); }
        void save() throws IOException { run.save(); }
    }
}
