package io.jenkins.telemetry.jenkins;

import hudson.model.InvisibleAction;
import hudson.model.Run;
import jenkins.model.RunAction2;
import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.core.DetachedOperation;
import java.io.*;
import java.util.*;

public final class TelemetryRunAction extends InvisibleAction implements RunAction2, Serializable {
    @Serial private static final long serialVersionUID=1L; private transient Run<?,?> run; private DetachedOperation rootOperation;
    private ReleaseIdentity release; private DeliveryManifest importedDeliveryManifest; private final Map<String,ArtifactRef> artifacts=new LinkedHashMap<>();
    private final Map<String,DetachedOperation> openOperations=new LinkedHashMap<>(); private final Map<String,String> flowNodeOperations=new LinkedHashMap<>();
    private long consoleOffset,consoleSequence; private String consoleRemainder=""; private boolean rootCompleted;
    @Override public synchronized void onAttached(Run<?,?>r){run=r;} @Override public synchronized void onLoad(Run<?,?>r){run=r;}
    public synchronized Run<?,?> run(){return run;} public synchronized DetachedOperation rootOperation(){return rootOperation;} public synchronized void rootOperation(DetachedOperation o){rootOperation=o;}
    public synchronized boolean rootCompleted(){return rootCompleted;} public synchronized void rootCompleted(boolean v){rootCompleted=v;}
    public synchronized ReleaseIdentity release(){return release;} public synchronized void release(ReleaseIdentity v){release=v;}
    public synchronized DeliveryManifest importedDeliveryManifest(){return importedDeliveryManifest;} public synchronized void importedDeliveryManifest(DeliveryManifest v){importedDeliveryManifest=v;}
    public synchronized void artifact(ArtifactRef a){artifacts.put(a.logicalName(),a);} public synchronized Map<String,ArtifactRef> artifacts(){return Map.copyOf(artifacts);}
    public synchronized void putOperation(DetachedOperation o){openOperations.put(o.operationId(),o);} public synchronized DetachedOperation operation(String id){return openOperations.get(id);}
    public synchronized DetachedOperation removeOperation(String id){return openOperations.remove(id);} public synchronized Map<String,DetachedOperation> openOperations(){return Map.copyOf(openOperations);}
    public synchronized void replaceOperation(DetachedOperation o){if(!openOperations.containsKey(o.operationId()))throw new IllegalArgumentException("Unknown operation: "+o.operationId());openOperations.put(o.operationId(),o);}
    public synchronized void flowNodeOperation(String n,String o){if(n!=null&&o!=null)flowNodeOperations.put(n,o);} public synchronized String removeFlowNodeOperation(String n){return flowNodeOperations.remove(n);} public synchronized String flowNodeOperation(String n){return flowNodeOperations.get(n);}
    public synchronized long consoleOffset(){return consoleOffset;} public synchronized void consoleOffset(long v){consoleOffset=v;} public synchronized long nextConsoleSequence(){return ++consoleSequence;}
    public synchronized String consoleRemainder(){return consoleRemainder;} public synchronized void consoleRemainder(String v){consoleRemainder=v==null?"":v;}
}
