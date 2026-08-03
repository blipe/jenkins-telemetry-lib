package org.jenkinsci.plugins.plaincredentials;
import hudson.util.Secret;
public interface StringCredentials {
    String getId();
    Secret getSecret();
}
