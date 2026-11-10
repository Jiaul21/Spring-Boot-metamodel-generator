package com.jiaul.metamodelgenerator.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Triggers compile-time metamodel generation (e.g. {@code [Model]Fields})
 * for classes and records.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface ModelFields {

    /**
     * Whether to generate static {@link com.jiaul.metamodelgenerator.metadata.ModelField}
     * constants, {@code FIELD_MAP}, and the nested {@code Path} class for relation chaining.
     * Defaults to true.
     * <p>
     * Set to false for pure DTOs or utility models where only {@code maps()} or {@code lists()}
     * methods are desired, omitting all metamodel field overhead.
     */
    boolean fields() default true;

    /**
     * Whether to generate Map extraction utilities:
     * <ul>
     *   <li>{@code toMap(obj)}: full map containing all fields (including nulls)</li>
     *   <li>{@code toNonNullMap(obj)}: map containing only non-null fields</li>
     * </ul>
     * Defaults to false (opt-in).
     */
    boolean maps() default false;

    /**
     * Whether to generate List extraction utilities:
     * <ul>
     *   <li>{@code toValues(obj)}: full list of values in field order (including nulls)</li>
     *   <li>{@code toNonNullValues(obj)}: list of non-null values</li>
     * </ul>
     * Defaults to false (opt-in).
     */
    boolean lists() default false;
}

