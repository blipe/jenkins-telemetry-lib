package org.kohsuke.stapler;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DataBoundSetter {}
