package com.rfizzle.distillation.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of Distillation's stable public API surface (Concord API Standard): stable
 * across patch and minor releases, breaking changes only with a major version bump and a changelog
 * entry. {@code org.jetbrains.annotations.ApiStatus} ships no {@code Stable} member, so the suite
 * declares this local marker per mod and applies it to every {@code api}-package type. Everything
 * outside {@code com.rfizzle.distillation.api} is internal and may change without notice.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Stable {
}
