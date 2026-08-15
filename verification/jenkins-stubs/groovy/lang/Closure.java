package groovy.lang;
import java.io.Serializable;
public abstract class Closure<V> implements Serializable {
    private final Object owner;
    protected Closure(Object owner) { this.owner = owner; }
    public Object getOwner() { return owner; }
    public int getMaximumNumberOfParameters() { return 1; }
    public V call(Object... args) { return null; }
}
