package com.jiaul.metamodelgenerator.processor;

import com.jiaul.metamodelgenerator.annotation.EntityFields;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Service responsible for AST introspection, hierarchy traversal, and field extraction.
 */
public class FieldExtractor {

    private final Types typeUtils;
    private final Elements elementUtils;

    public FieldExtractor(Types typeUtils, Elements elementUtils) {
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
    }

    public List<FieldMeta> extractFields(TypeElement type, Map<String, TypeElement> registry) {
        List<FieldMeta> list = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        if (type.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement rc : type.getRecordComponents()) {
                String name = rc.getSimpleName().toString();
                if (seenNames.add(name)) {
                    list.add(createFieldMeta(name, rc.asType(), name, true, registry));
                }
            }
        } else {
            TypeElement current = type;
            while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
                for (Element e : current.getEnclosedElements()) {
                    if (e.getKind() == ElementKind.FIELD && !e.getModifiers().contains(Modifier.STATIC)) {
                        VariableElement v = (VariableElement) e;
                        String name = v.getSimpleName().toString();
                        if (seenNames.add(name)) {
                            String getter = resolveGetter(current, v);
                            boolean hasGetter = hasMethod(current, getter) || hasLombokGetter(current, v);
                            list.add(createFieldMeta(name, v.asType(), getter, hasGetter, registry));
                        }
                    }
                }
                TypeMirror superclass = current.getSuperclass();
                if (superclass instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
                    current = te;
                } else {
                    current = null;
                }
            }
        }

        return list;
    }

    private FieldMeta createFieldMeta(
            String name,
            TypeMirror type,
            String getterName,
            boolean hasGetter,
            Map<String, TypeElement> registry
    ) {
        boolean isPrimitive = type.getKind().isPrimitive();
        boolean isCollection = isCollectionType(type);
        TypeMirror elementType = extractElementType(type);
        TypeElement relatedEntity = null;
        boolean isEntityRelation = false;

        if (isCollection) {
            if (elementType != null) {
                relatedEntity = getAnnotatedEntity(elementType, registry);
                isEntityRelation = (relatedEntity != null);
            }
        } else {
            relatedEntity = getAnnotatedEntity(type, registry);
            isEntityRelation = (relatedEntity != null);
        }

        return new FieldMeta(
                name,
                type,
                elementType,
                getterName,
                hasGetter,
                isPrimitive,
                isCollection,
                isEntityRelation,
                relatedEntity
        );
    }

    public String resolveGetter(TypeElement type, VariableElement field) {
        String name = field.getSimpleName().toString();
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        if (field.asType().getKind() == TypeKind.BOOLEAN) {
            String isName = "is" + capitalized;
            if (hasMethod(type, isName)) {
                return isName;
            }
        }
        String getName = "get" + capitalized;
        if (hasMethod(type, getName)) {
            return getName;
        }

        return field.asType().getKind() == TypeKind.BOOLEAN ? "is" + capitalized : "get" + capitalized;
    }

    public boolean hasMethod(TypeElement type, String methodName) {
        TypeElement current = type;
        while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
            for (Element e : current.getEnclosedElements()) {
                if (e.getKind() == ElementKind.METHOD && e.getSimpleName().contentEquals(methodName)) {
                    return true;
                }
            }
            TypeMirror superclass = current.getSuperclass();
            if (superclass instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
                current = te;
            } else {
                current = null;
            }
        }
        return false;
    }

    public boolean hasLombokGetter(TypeElement type, VariableElement field) {
        TypeElement current = type;
        while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
            for (AnnotationMirror am : current.getAnnotationMirrors()) {
                String name = am.getAnnotationType().asElement().getSimpleName().toString();
                if (name.equals("Data") || name.equals("Getter") || name.equals("Value")) {
                    return true;
                }
            }
            TypeMirror superclass = current.getSuperclass();
            if (superclass instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
                current = te;
            } else {
                current = null;
            }
        }
        if (field != null) {
            for (AnnotationMirror am : field.getAnnotationMirrors()) {
                String name = am.getAnnotationType().asElement().getSimpleName().toString();
                if (name.equals("Getter")) {
                    return true;
                }
            }
        }
        return false;
    }

    public TypeName getBoxedTypeName(TypeMirror t) {
        if (t.getKind().isPrimitive()) {
            return TypeName.get(typeUtils.boxedClass((PrimitiveType) t).asType());
        }
        if (t instanceof DeclaredType dt) {
            return ClassName.get((TypeElement) dt.asElement());
        }
        return TypeName.get(t);
    }

    public CodeBlock buildFieldInit(
            ClassName fieldProperty,
            String name,
            TypeName mainType,
            TypeName elemType,
            boolean isCollection
    ) {
        if (isCollection) {
            return CodeBlock.of(
                    "new $T<>(prefix(this._path, $S), $T.class, $T.class)",
                    fieldProperty,
                    name,
                    mainType,
                    elemType
            );
        }

        return CodeBlock.of(
                "new $T<>(prefix(this._path, $S), $T.class, null)",
                fieldProperty,
                name,
                mainType
        );
    }

    public TypeElement getAnnotatedEntity(TypeMirror t, Map<String, TypeElement> registry) {
        if (t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            String qName = te.getQualifiedName().toString();
            if (registry.containsKey(qName) || te.getAnnotation(EntityFields.class) != null) {
                return te;
            }
        }
        return null;
    }

    public boolean isCollectionType(TypeMirror t) {
        String s = typeUtils.erasure(t).toString();
        return s.equals("java.util.List")
                || s.equals("java.util.Set")
                || s.equals("java.util.Collection")
                || s.equals("java.util.Queue")
                || s.equals("java.util.Deque");
    }

    public TypeMirror extractElementType(TypeMirror t) {
        if (t instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            return dt.getTypeArguments().get(0);
        }
        return null;
    }
}
