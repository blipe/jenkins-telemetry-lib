package io.jenkins.telemetry.jenkins;

import groovy.lang.Closure;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Plugin-provided Jenkinsfile API. No Shared Library registration is required. */
public final class JenkinsTelemetryDsl implements Serializable {
    private static final long serialVersionUID = 1L;
    private final CpsScript script;

    JenkinsTelemetryDsl(CpsScript script) { this.script = script; }

    @Whitelisted public JenkinsTelemetryDsl current() { return this; }
    @Whitelisted public Map<String,Object> release(Map<String,Object> spec) { return map(signal("release", copy(spec))); }
    @Whitelisted public void log(String message) { log("INFO", message, Map.of()); }
    @Whitelisted public void log(String message, Map<String,Object> attrs) { log("INFO", message, attrs); }
    @Whitelisted public void log(String severity, String message, Map<String,Object> attrs) {
        signal("log", values("severity",severity,"message",message,"attributes",safe(attrs)));
    }
    @Whitelisted public void count(String name, Number delta) { count(name,delta,Map.of()); }
    @Whitelisted public void count(String name, Number delta, Map<String,Object> attrs) {
        metric(null,name,"COUNTER",delta,"{count}","",attrs);
    }
    @Whitelisted public void measure(String name, Number value, String unit) { measure(name,value,unit,Map.of()); }
    @Whitelisted public void measure(String name, Number value, String unit, Map<String,Object> attrs) {
        metric(null,name,"HISTOGRAM",value,unit,"",attrs);
    }
    @Whitelisted public LogApi logs() { return new LogApi(this,null); }
    @Whitelisted public MetricApi metrics() { return new MetricApi(this,null); }
    @Whitelisted public ArtifactApi artifacts() { return new ArtifactApi(this); }
    @Whitelisted public Object observe(Map<String,Object> spec, Closure<?> body) { return operation(spec,null,body); }
    @Whitelisted public Object operation(Map<String,Object> spec, Closure<?> body) { return observe(spec,body); }
    @Whitelisted public Object operation(String name, String kind, Closure<?> body) { return observe(values("name",name,"kind",kind),body); }
    @Whitelisted public Object deployment(Map<String,Object> spec, Closure<?> body) {
        Map<String,Object> attrs = safe(spec.get("attributes"));
        put(attrs,"deployment.id",spec.get("deploymentId")); put(attrs,"deployment.environment.name",spec.get("environment"));
        put(attrs,"deployment.target",spec.get("target")); put(attrs,"k8s.cluster.name",spec.get("cluster"));
        put(attrs,"k8s.namespace.name",spec.get("namespace")); put(attrs,"cloud.region",spec.get("region"));
        Map<String,Object> op = values("name", valueOr(spec.get("name"),"Deploy "+spec.get("target")+" to "+spec.get("environment")),"kind","DEPLOY","attributes",attrs);
        return operation(op,null,body);
    }
    @Whitelisted public void event(String name) { event(name,Map.of()); }
    @Whitelisted public void event(String name, Map<String,Object> attrs) { signal("event",values("name",name,"attributes",safe(attrs))); }
    @Whitelisted public Map<String,Object> exportDeliveryManifest() { return exportDeliveryManifest(Map.of()); }
    @Whitelisted public Map<String,Object> exportDeliveryManifest(Map<String,Object> spec) {
        Map<String,Object> result=map(signal("deliveryManifestExport",values("attributes",safe(spec.get("attributes")))));
        Object file=spec.get("file"); if(file!=null) script.invokeMethod("writeFile",values("file",file,"text",result.get("json"))); return result;
    }
    @Whitelisted public Map<String,Object> importDeliveryManifest(Map<String,Object> spec) {
        Map<String,Object> request=copy(spec); Object file=request.get("file");
        if(file!=null) request.put("json",String.valueOf(script.invokeMethod("readFile",values("file",file))));
        return map(signal("deliveryManifestImport",values("json",request.get("json"),"sha256",request.get("sha256"))));
    }
    @Whitelisted public Map<String,Object> runtimeManifest() { return runtimeManifest(Map.of()); }
    @Whitelisted public Map<String,Object> runtimeManifest(Map<String,Object> spec) {
        Map<String,Object> result=map(signal("runtimeManifest",values("environment",spec.get("environment"),"deploymentId",spec.get("deploymentId"))));
        Object file=spec.get("file"); if(file!=null) script.invokeMethod("writeFile",values("file",file,"text",result.get("json"))); return result;
    }
    @Whitelisted public Map<String,Object> status() { return map(signal("status",Map.of())); }
    @Whitelisted public Map<String,Object> flush() { return map(signal("flush",Map.of())); }

    private Object operation(Map<String,Object> spec,String parent,Closure<?> body) {
        Map<String,Object> start=map(signal("operationStart",values("name",required(spec,"name"),"kind",valueOr(spec.get("kind"),"CUSTOM"),"attributes",safe(spec.get("attributes")),"parentOperationId",parent)));
        String id=String.valueOf(start.get("operationId")); OperationContext context=new OperationContext(this,id);
        try { Object result=body.getMaximumNumberOfParameters()>0?body.call(context):body.call(); signal("operationEnd",values("operationId",id,"status","OK")); return result; }
        catch(Throwable failure){ signal("operationEnd",values("operationId",id,"status","ERROR","errorType",failure.getClass().getName(),"message",failure.getMessage(),"stackTrace",stack(failure))); throw propagate(failure); }
    }
    private void metric(String op,String name,String kind,Number value,String unit,String description,Map<String,Object> attrs) {
        signal("metric",values("operationId",op,"name",name,"kind",kind,"value",value,"unit",unit,"description",description,"attributes",safe(attrs)));
    }
    private Object signal(String action,Map<String,Object> data){return script.invokeMethod("telemetrySignal",values("action",action,"data",data));}
    private static RuntimeException propagate(Throwable t){if(t instanceof RuntimeException r)return r;if(t instanceof Error e)throw e;return new RuntimeException(t);}
    private static String stack(Throwable t){StringWriter w=new StringWriter();t.printStackTrace(new PrintWriter(w));return w.toString();}
    private static String required(Map<String,Object> m,String k){Object v=m.get(k);if(v==null||String.valueOf(v).isBlank())throw new IllegalArgumentException("Missing telemetry field: "+k);return String.valueOf(v);}
    private static Object valueOr(Object value,Object fallback){return value==null||String.valueOf(value).isBlank()?fallback:value;}
    private static Map<String,Object> safe(Object value){return value instanceof Map<?,?> m?map(m):new LinkedHashMap<>();}
    private static Map<String,Object> copy(Map<String,Object> value){return value==null?new LinkedHashMap<>():new LinkedHashMap<>(value);}
    private static Map<String,Object> map(Object value){Map<String,Object> result=new LinkedHashMap<>();if(value instanceof Map<?,?> m)m.forEach((k,v)->result.put(String.valueOf(k),v));return result;}
    private static void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private static Map<String,Object> values(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i+1<pairs.length;i+=2)if(pairs[i+1]!=null)m.put(String.valueOf(pairs[i]),pairs[i+1]);return m;}

    public static final class OperationContext implements Serializable {
        private final JenkinsTelemetryDsl owner; private final String id;
        OperationContext(JenkinsTelemetryDsl owner,String id){this.owner=owner;this.id=id;}
        @Whitelisted public LogApi logs(){return new LogApi(owner,id);} @Whitelisted public MetricApi metrics(){return new MetricApi(owner,id);}
        @Whitelisted public void log(String message){logs().info(message);} @Whitelisted public void count(String n,Number d){metrics().counter(n).add(d);} 
        @Whitelisted public void event(String name,Map<String,Object> attrs){owner.signal("operationEvent",values("operationId",id,"name",name,"attributes",safe(attrs)));}
        @Whitelisted public void attribute(String name,Object value){owner.signal("operationAttribute",values("operationId",id,"name",name,"value",value));}
        @Whitelisted public Object observe(Map<String,Object> spec,Closure<?> body){return owner.operation(spec,id,body);}
    }
    public static final class LogApi implements Serializable {
        private final JenkinsTelemetryDsl owner; private final String op; LogApi(JenkinsTelemetryDsl o,String p){owner=o;op=p;}
        @Whitelisted public void info(String m){info(m,Map.of());} @Whitelisted public void info(String m,Map<String,Object>a){emit("INFO",m,a);} @Whitelisted public void warn(String m,Map<String,Object>a){emit("WARN",m,a);} @Whitelisted public void error(String m,Map<String,Object>a){emit("ERROR",m,a);} @Whitelisted public void debug(String m,Map<String,Object>a){emit("DEBUG",m,a);}
        private void emit(String s,String m,Map<String,Object>a){owner.signal("log",values("operationId",op,"severity",s,"message",m,"attributes",safe(a)));}
    }
    public static final class MetricApi implements Serializable {
        private final JenkinsTelemetryDsl owner; private final String op; MetricApi(JenkinsTelemetryDsl o,String p){owner=o;op=p;}
        @Whitelisted public Instrument counter(String n){return new Instrument(owner,op,n,"COUNTER","{count}","");}
        @Whitelisted public Instrument histogram(String n,String u,String d){return new Instrument(owner,op,n,"HISTOGRAM",u,d);}
        @Whitelisted public Instrument gauge(String n,String u,String d){return new Instrument(owner,op,n,"GAUGE",u,d);}
    }
    public static final class Instrument implements Serializable {
        private final JenkinsTelemetryDsl o;private final String p,n,k,u,d;Instrument(JenkinsTelemetryDsl o,String p,String n,String k,String u,String d){this.o=o;this.p=p;this.n=n;this.k=k;this.u=u;this.d=d;}
        @Whitelisted public void add(Number v){record(v,Map.of());}@Whitelisted public void add(Number v,Map<String,Object>a){record(v,a);}@Whitelisted public void record(Number v){record(v,Map.of());}@Whitelisted public void record(Number v,Map<String,Object>a){o.metric(p,n,k,v,u,d,a);}@Whitelisted public void set(Number v){record(v);}
    }
    public static final class ArtifactApi implements Serializable {
        private final JenkinsTelemetryDsl owner;ArtifactApi(JenkinsTelemetryDsl o){owner=o;}
        @Whitelisted public Map<String,Object> record(Map<String,Object>s){return map(owner.signal("artifact",copy(s)));}
        @Whitelisted public Map<String,Object> file(Map<String,Object>s){Map<String,Object>v=copy(s);v.putIfAbsent("logicalName","primary");v.put("type","file");v.putIfAbsent("digestAlgorithm","sha256");return record(v);}
        @Whitelisted public Map<String,Object> maven(Map<String,Object>s){Map<String,Object>v=copy(s);v.putIfAbsent("logicalName","primary");v.put("type","maven");v.putIfAbsent("digestAlgorithm","sha256");v.put("name",required(v,"groupId")+":"+required(v,"artifactId"));return record(v);}
        @Whitelisted public Map<String,Object> oci(Map<String,Object>s){Map<String,Object>v=copy(s);v.putIfAbsent("logicalName","primary");v.put("type","oci");String digest=required(v,"digest");if(digest.startsWith("sha256:"))digest=digest.substring(7);v.put("digest",digest);v.put("digestAlgorithm","sha256");v.put("name",required(v,"image"));v.putIfAbsent("uri",required(v,"repository")+"/"+v.get("image")+"@sha256:"+digest);return record(v);}
    }
}
