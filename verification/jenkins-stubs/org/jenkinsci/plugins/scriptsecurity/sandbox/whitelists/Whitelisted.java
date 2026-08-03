package org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Whitelisted {}
