package hudson.model;
import java.io.IOException;
public abstract class AsyncPeriodicWork {
    protected AsyncPeriodicWork(String name) {}
    public long getInitialDelay() { return 0; }
    public abstract long getRecurrencePeriod();
    protected abstract void execute(TaskListener listener) throws IOException, InterruptedException;
}
