package com.jiaul.metamodelgenerator.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Metadata model holding extracted information about an entity field or record component.
 */
public class FieldMeta {

    private final String name;
    private final TypeMirror type;
    private final TypeMirror elementType;
    private final String getterName;
    private final boolean hasGetter;
    private final boolean isPrimitive;
    private final boolean isCollection;
    private final boolean isEntityRelation;
    private final TypeElement relatedEntity;

    public FieldMeta(
            String name,
            TypeMirror type,
            TypeMirror elementType,
            String getterName,
            boolean hasGetter,
            boolean isPrimitive,
            boolean isCollection,
            boolean isEntityRelation,
            TypeElement relatedEntity
    ) {
        this.name = name;
        this.type = type;
        this.elementType = elementType;
        this.getterName = getterName;
        this.hasGetter = hasGetter;
        this.isPrimitive = isPrimitive;
        this.isCollection = isCollection;
        this.isEntityRelation = isEntityRelation;
        this.relatedEntity = relatedEntity;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }

    public TypeMirror getElementType() {
        return elementType;
    }

    public String getGetterName() {
        return getterName;
    }

    public boolean hasGetter() {
        return hasGetter;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public boolean isEntityRelation() {
        return isEntityRelation;
    }

    public TypeElement getRelatedEntity() {
        return relatedEntity;
    }
}
