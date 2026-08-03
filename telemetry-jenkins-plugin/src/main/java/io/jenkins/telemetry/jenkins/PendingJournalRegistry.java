package io.jenkins.telemetry.jenkins;

import jenkins.model.Jenkins;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class PendingJournalRegistry {
    private static final Object LOCK = new Object();
    private PendingJournalRegistry() { }
    static void register(Path journal) throws IOException { if(journal==null)return; synchronized(LOCK){Properties v=load();v.setProperty(normalize(journal),Long.toString(System.currentTimeMillis()));save(v);} }
    static void remove(Path journal) throws IOException { if(journal==null)return; synchronized(LOCK){Properties v=load();if(v.remove(normalize(journal))!=null)save(v);} }
    static List<Path> oldest(int maximum) throws IOException { synchronized(LOCK){Properties v=load();List<Entry> e=new ArrayList<>();for(String p:v.stringPropertyNames()){long t;try{t=Long.parseLong(v.getProperty(p,"0"));}catch(NumberFormatException x){t=0;}e.add(new Entry(Path.of(p),t));}return e.stream().sorted(Comparator.comparingLong(Entry::timestamp)).limit(Math.max(1,maximum)).map(Entry::path).toList();} }
    static void discoverOnce(Path home,int maximum)throws IOException{Path marker=home.resolve("jenkins-telemetry-pending.discovery-complete");if(Files.exists(marker))return;try(var paths=Files.find(home,16,(p,a)->a.isDirectory()&&"journal".equals(p.getFileName().toString())&&p.getParent()!=null&&"telemetry".equals(p.getParent().getFileName().toString()))){for(Path j:paths.limit(Math.max(1,maximum)).toList())register(j);}try{Files.writeString(marker,Long.toString(System.currentTimeMillis()),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(IOException ignored){}}
    private static Properties load()throws IOException{Properties v=new Properties();Path f=file();if(Files.exists(f))try(InputStream in=Files.newInputStream(f)){v.load(in);}return v;}
    private static void save(Properties v)throws IOException{Path f=file();Files.createDirectories(f.getParent());Path tmp=f.resolveSibling(f.getFileName()+".tmp");try(OutputStream out=Files.newOutputStream(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)){v.store(out,"Jenkins delivery telemetry pending journals");}try{Files.move(tmp,f,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ignored){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}}
    private static Path file(){return Jenkins.get().getRootDir().toPath().resolve("jenkins-telemetry-pending.properties");}
    private static String normalize(Path p){return p.toAbsolutePath().normalize().toString();}
    private record Entry(Path path,long timestamp){}
}
