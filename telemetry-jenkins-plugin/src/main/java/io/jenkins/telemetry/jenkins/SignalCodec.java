package io.jenkins.telemetry.jenkins;

import io.jenkins.telemetry.api.*;
import java.util.LinkedHashMap;
import java.util.Map;

final class SignalCodec {
    private SignalCodec() { }
    static Attributes attributes(Object raw) { if(raw instanceof Map<?,?> map){Map<String,Object> v=new LinkedHashMap<>();map.forEach((k,x)->v.put(String.valueOf(k),x));return Attributes.copyOf(v);}return Attributes.empty(); }
    static ReleaseIdentity release(Map<String,Object> d){return ReleaseIdentity.builder().serviceNamespace(string(d,"serviceNamespace")).serviceName(required(d,"serviceName")).serviceVersion(string(d,"serviceVersion")).sourceRepository(string(d,"sourceRepository")).sourceRevision(string(d,"sourceRevision")).sourceRef(string(d,"sourceRef")).buildVersion(string(d,"buildVersion")).attributes(map(d.get("attributes"))).build();}
    static ArtifactRef artifact(Map<String,Object>d){return ArtifactRef.builder().logicalName(string(d,"logicalName")).type(required(d,"type")).repository(string(d,"repository")).name(string(d,"name")).version(string(d,"version")).digest(stringOr(d,"digestAlgorithm","sha256"),required(d,"digest")).uri(string(d,"uri")).attributes(map(d.get("attributes"))).build();}
    static Map<String,Object> map(Object raw){Map<String,Object> r=new LinkedHashMap<>();if(raw instanceof Map<?,?>m)m.forEach((k,v)->r.put(String.valueOf(k),v));return r;}
    static String string(Map<String,Object>d,String k){Object v=d.get(k);return v==null?null:String.valueOf(v);} static String stringOr(Map<String,Object>d,String k,String f){String v=string(d,k);return v==null||v.isBlank()?f:v;}
    static String required(Map<String,Object>d,String k){String v=string(d,k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing telemetry field: "+k);return v;}
    static double decimal(Map<String,Object>d,String k,double f){Object v=d.get(k);if(v instanceof Number n)return n.doubleValue();if(v!=null)return Double.parseDouble(String.valueOf(v));return f;}
}
