package io.jenkins.telemetry.jenkins;

import hudson.security.ACL;
import io.jenkins.telemetry.core.ConfigurationResolver;
import jenkins.model.Jenkins;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class JenkinsCredentialPlaceholder {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private JenkinsCredentialPlaceholder() { }
    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        ConfigurationResolver.registerSource("credential", JenkinsCredentialPlaceholder::resolve);
    }
    private static String resolve(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Class<?> credentialType = Class.forName("org.jenkinsci.plugins.plaincredentials.StringCredentials");
            Class<?> providerType = Class.forName("com.cloudbees.plugins.credentials.CredentialsProvider");
            Method lookup = lookupMethod(providerType);
            Object credentials = lookup.invoke(null, credentialType, Jenkins.get(), ACL.SYSTEM2, List.of());
            if (!(credentials instanceof Iterable<?> iterable)) return null;
            for (Object credential : iterable) {
                if (!id.equals(invokeString(credential, "getId"))) continue;
                Object secret = credential.getClass().getMethod("getSecret").invoke(credential);
                return invokeString(secret, "getPlainText");
            }
            return null;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("${credential:" + id + "} requires the Jenkins Credentials and Plain Credentials plugins", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalArgumentException("Unable to resolve Jenkins Secret Text credential '" + id + "': " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to resolve Jenkins Secret Text credential '" + id + "'", e);
        }
    }
    private static Method lookupMethod(Class<?> providerType) throws NoSuchMethodException {
        for (String name : List.of("lookupCredentialsInItemGroup", "lookupCredentials")) {
            for (Method method : providerType.getMethods()) {
                if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 4 && method.getParameterTypes()[0] == Class.class) return method;
            }
        }
        throw new NoSuchMethodException("No compatible CredentialsProvider lookup method is available");
    }
    private static String invokeString(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) return null;
        Object value = target.getClass().getMethod(methodName).invoke(target);
        return value == null ? null : String.valueOf(value);
    }
}
