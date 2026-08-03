package jenkins.model;

import hudson.model.Action;
import hudson.model.Run;

public interface RunAction2 extends Action {
    void onAttached(Run<?, ?> run);
    void onLoad(Run<?, ?> run);
}
