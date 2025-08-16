package com.example.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotate a class to generate a {ClassName}Builder with fluent setters and a build() method.
 */
@Retention(SOURCE)
@Target(TYPE)
public @interface AutoBuilder {
}