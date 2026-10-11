package com.jiaul.metamodelgenerator.processor;

import com.google.auto.service.AutoService;
import com.jiaul.metamodelgenerator.annotation.EntityFields;
import com.jiaul.metamodelgenerator.metadata.FieldProperty;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

/**
 * Annotation processor for {@link EntityFields}.
 * <p>
 * Generates compile-time type-safe metamodels (e.g. {@code StudentFields}, {@code CourseFields})
 * supporting both classes and records, relation path chaining (e.g. {@code StudentFields.courses.title}),
 * and bidirectional cycle protection to prevent infinite loops.
 */
@AutoService(Processor.class)
public class FieldsProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;

    public FieldsProcessor() {
        super();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(EntityFields.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.typeUtils = env.getTypeUtils();
        this.elementUtils = env.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        // 1. Discover all entities annotated with @EntityFields
        Map<String, TypeElement> entityRegistry = new LinkedHashMap<>();
        for (Element e : roundEnv.getElementsAnnotatedWith(EntityFields.class)) {
            if (e instanceof TypeElement type) {
                entityRegistry.put(type.getQualifiedName().toString(), type);
            }
        }

        if (entityRegistry.isEmpty()) {
            return false;
        }

        // 2. Generate fields classes for each discovered entity
        for (TypeElement type : entityRegistry.values()) {
            try {
                generate(type, entityRegistry);
            } catch (Exception ex) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to generate fields class for " + type.getQualifiedName() + ": " + ex.getMessage(),
                        type
                );
            }
        }

        return true;
    }

    // =================================================================
    // MAIN CODE GENERATION
    // =================================================================

    private void generate(TypeElement type, Map<String, TypeElement> entityRegistry) throws IOException {
        String pkg = elementUtils.getPackageOf(type).getQualifiedName().toString();
        String className = type.getSimpleName() + "Fields";
        boolean isRecord = type.getKind() == ElementKind.RECORD;

        List<FieldMeta> fields = extractFields(type, entityRegistry);

        ClassName pathClass = ClassName.get(pkg, className, "Path");
        ClassName fieldProperty = ClassName.get(FieldProperty.class);

        TypeName wildcardProperty = ParameterizedTypeName.get(
                fieldProperty,
                WildcardTypeName.subtypeOf(Object.class),
                WildcardTypeName.subtypeOf(Object.class)
        );
        TypeName listPropertiesType = ParameterizedTypeName.get(ClassName.get(List.class), wildcardProperty);
        TypeName mapPropertiesType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                wildcardProperty
        );

        // -------------------------------------------------------------
        // Build the nested Path class first
        // -------------------------------------------------------------
        TypeSpec.Builder pathBuilder = TypeSpec.classBuilder("Path")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addSuperinterface(ClassName.get(CharSequence.class))
                .addJavadoc("Type-safe relation path and property holder for {@link $T}.\n", type.asType());

        pathBuilder.addField(FieldSpec.builder(String.class, "_path", Modifier.PUBLIC, Modifier.FINAL).build());

        // Instance fields on Path
        for (FieldMeta f : fields) {
            if (f.isEntityRelation) {
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.relatedEntity).getQualifiedName().toString(),
                        f.relatedEntity.getSimpleName() + "Fields",
                        "Path"
                );
                pathBuilder.addField(FieldSpec.builder(relatedPathClass, f.name, Modifier.PUBLIC, Modifier.FINAL).build());
            } else {
                TypeName mainType = getBoxedTypeName(f.type);
                TypeName elemType = f.elementType != null ? getBoxedTypeName(f.elementType) : ClassName.get(Void.class);
                TypeName propType = ParameterizedTypeName.get(fieldProperty, mainType, elemType);

                pathBuilder.addField(FieldSpec.builder(propType, f.name, Modifier.PUBLIC, Modifier.FINAL).build());
            }
        }

        pathBuilder.addField(FieldSpec.builder(listPropertiesType, "ALL_FIELDS", Modifier.PUBLIC, Modifier.FINAL).build());
        pathBuilder.addField(FieldSpec.builder(mapPropertiesType, "FIELD_MAP", Modifier.PUBLIC, Modifier.FINAL).build());

        // Path constructors
        pathBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this(\"\", $T.emptySet())", Collections.class)
                .build());

        pathBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "prefix")
                .addStatement("this(prefix, $T.emptySet())", Collections.class)
                .build());

        // Full Path constructor with cycle tracking
        MethodSpec.Builder pathCtor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "prefix")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class)), "visitedTypes")
                .addStatement("this._path = prefix != null ? prefix : \"\"");

        // Initialize scalar properties
        for (FieldMeta f : fields) {
            if (!f.isEntityRelation) {
                TypeName mainType = getBoxedTypeName(f.type);
                TypeName elemType = f.elementType != null ? getBoxedTypeName(f.elementType) : ClassName.get(Void.class);
                CodeBlock initBlock = buildFieldInit(fieldProperty, f.name, mainType, elemType, f.isCollection);

                pathCtor.addStatement("this.$L = $L", f.name, initBlock);
            }
        }

        // Initialize ALL_FIELDS and FIELD_MAP
        pathCtor.addStatement("$T<$T> _all = new $T<>()", List.class, wildcardProperty, ArrayList.class);
        pathCtor.addStatement("$T<String, $T> _map = new $T<>()", Map.class, wildcardProperty, LinkedHashMap.class);
        for (FieldMeta f : fields) {
            if (!f.isEntityRelation) {
                pathCtor.addStatement("_all.add(this.$L)", f.name);
                pathCtor.addStatement("_map.put($S, this.$L)", f.name, f.name);
            }
        }
        pathCtor.addStatement("this.ALL_FIELDS = $T.unmodifiableList(_all)", Collections.class);
        pathCtor.addStatement("this.FIELD_MAP = $T.unmodifiableMap(_map)", Collections.class);

        // Cycle tracking setup for relations
        pathCtor.addStatement("$T<String> nextVisited = new $T<>(visitedTypes)", Set.class, HashSet.class);
        pathCtor.addStatement("nextVisited.add($S)", type.getQualifiedName().toString());

        for (FieldMeta f : fields) {
            if (f.isEntityRelation) {
                String relatedQName = f.relatedEntity.getQualifiedName().toString();
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.relatedEntity).getQualifiedName().toString(),
                        f.relatedEntity.getSimpleName() + "Fields",
                        "Path"
                );

                pathCtor.beginControlFlow("if (!visitedTypes.contains($S))", relatedQName)
                        .addStatement("this.$L = new $T(prefix(this._path, $S), nextVisited)", f.name, relatedPathClass, f.name)
                        .nextControlFlow("else")
                        .addStatement("this.$L = null", f.name)
                        .endControlFlow();
            }
        }

        pathBuilder.addMethod(pathCtor.build());

        // On-demand methods for relations (enables unbounded chaining if developer explicitly calls method)
        for (FieldMeta f : fields) {
            if (f.isEntityRelation) {
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.relatedEntity).getQualifiedName().toString(),
                        f.relatedEntity.getSimpleName() + "Fields",
                        "Path"
                );

                pathBuilder.addMethod(MethodSpec.methodBuilder(f.name)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(relatedPathClass)
                        .addStatement("return new $T(prefix(this._path, $S), $T.emptySet())", relatedPathClass, f.name, Collections.class)
                        .build());
            }
        }

        // Path utility methods
        pathBuilder.addMethod(MethodSpec.methodBuilder("getProperty")
                .addModifiers(Modifier.PUBLIC)
                .returns(wildcardProperty)
                .addParameter(String.class, "fieldName")
                .addStatement("$T property = this.FIELD_MAP.get(fieldName)", wildcardProperty)
                .beginControlFlow("if (property == null)")
                .addStatement("throw new $T($S + fieldName)", IllegalArgumentException.class, "Unknown field: ")
                .endControlFlow()
                .addStatement("return property")
                .build());

        pathBuilder.addMethod(MethodSpec.methodBuilder("getType")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
                .addParameter(String.class, "fieldName")
                .addStatement("return getProperty(fieldName).type()")
                .build());

        pathBuilder.addMethod(MethodSpec.methodBuilder("path")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return _path")
                .build());

        pathBuilder.addMethod(MethodSpec.methodBuilder("getPath")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return _path")
                .build());

        // Helper prefix method in Path
        pathBuilder.addMethod(MethodSpec.methodBuilder("prefix")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(String.class)
                .addParameter(String.class, "parent")
                .addParameter(String.class, "field")
                .addStatement("return (parent == null || parent.isEmpty()) ? field : parent + \".\" + field")
                .build());

        // CharSequence implementation on Path
        pathBuilder.addMethod(MethodSpec.methodBuilder("length")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return _path != null ? _path.length() : 0")
                .build());

        pathBuilder.addMethod(MethodSpec.methodBuilder("charAt")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(char.class)
                .addParameter(int.class, "index")
                .beginControlFlow("if (_path == null)")
                .addStatement("throw new $T(index)", StringIndexOutOfBoundsException.class)
                .endControlFlow()
                .addStatement("return _path.charAt(index)")
                .build());

        pathBuilder.addMethod(MethodSpec.methodBuilder("subSequence")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(CharSequence.class)
                .addParameter(int.class, "start")
                .addParameter(int.class, "end")
                .beginControlFlow("if (_path == null)")
                .addStatement("throw new $T(start)", StringIndexOutOfBoundsException.class)
                .endControlFlow()
                .addStatement("return _path.subSequence(start, end)")
                .build());

        pathBuilder.addMethod(MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return _path != null ? _path : \"\"")
                .build());

        // -------------------------------------------------------------
        // Main Outer Class (${EntityName}Fields)
        // -------------------------------------------------------------
        TypeSpec.Builder clazz = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Generated type-safe metamodel for {@link $T}.\n", type.asType());

        // Private constructor
        clazz.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        // ROOT static instance
        clazz.addField(FieldSpec.builder(pathClass, "ROOT", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T(\"\", $T.emptySet())", pathClass, Collections.class)
                .build());

        // Static fields on outer class delegating to ROOT
        for (FieldMeta f : fields) {
            if (f.isEntityRelation) {
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.relatedEntity).getQualifiedName().toString(),
                        f.relatedEntity.getSimpleName() + "Fields",
                        "Path"
                );

                clazz.addField(FieldSpec.builder(relatedPathClass, f.name, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("ROOT.$L", f.name)
                        .build());
            } else {
                TypeName mainType = getBoxedTypeName(f.type);
                TypeName elemType = f.elementType != null ? getBoxedTypeName(f.elementType) : ClassName.get(Void.class);
                TypeName propType = ParameterizedTypeName.get(fieldProperty, mainType, elemType);

                clazz.addField(FieldSpec.builder(propType, f.name, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("ROOT.$L", f.name)
                        .build());
            }
        }

        // Static path factory method
        clazz.addMethod(MethodSpec.methodBuilder("path")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(pathClass)
                .addParameter(String.class, "prefix")
                .addStatement("return new $T(prefix, $T.emptySet())", pathClass, Collections.class)
                .build());

        // Static ALL_FIELDS and FIELD_MAP
        clazz.addField(FieldSpec.builder(listPropertiesType, "ALL_FIELDS", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("ROOT.ALL_FIELDS")
                .build());

        clazz.addField(FieldSpec.builder(mapPropertiesType, "FIELD_MAP", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("ROOT.FIELD_MAP")
                .build());

        // Static getProperty and getType
        clazz.addMethod(MethodSpec.methodBuilder("getProperty")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(wildcardProperty)
                .addParameter(String.class, "fieldName")
                .addStatement("return ROOT.getProperty(fieldName)")
                .build());

        clazz.addMethod(MethodSpec.methodBuilder("getType")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
                .addParameter(String.class, "fieldName")
                .addStatement("return ROOT.getType(fieldName)")
                .build());

        // Check if any non-relation field lacks a direct getter (e.g. classes with no getters and no Lombok)
        boolean hasFieldWithoutGetter = fields.stream().anyMatch(f -> !isRecord && !f.hasGetter);
        if (hasFieldWithoutGetter) {
            clazz.addMethod(MethodSpec.methodBuilder("_get")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(Object.class)
                    .addParameter(Object.class, "obj")
                    .addParameter(String.class, "fieldName")
                    .beginControlFlow("if (obj == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .beginControlFlow("try")
                    .addStatement("$T<?> c = obj.getClass()", Class.class)
                    .beginControlFlow("while (c != null && c != $T.class)", Object.class)
                    .beginControlFlow("try")
                    .addStatement("$T f = c.getDeclaredField(fieldName)", java.lang.reflect.Field.class)
                    .addStatement("f.setAccessible(true)")
                    .addStatement("return f.get(obj)")
                    .nextControlFlow("catch ($T e)", NoSuchFieldException.class)
                    .addStatement("c = c.getSuperclass()")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return null")
                    .nextControlFlow("catch ($T e)", Exception.class)
                    .addStatement("return null")
                    .endControlFlow()
                    .build());
        }

        // Static getPropertyValue(obj)
        TypeName propValueReturnType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                wildcardProperty,
                ClassName.get(Object.class)
        );
        MethodSpec.Builder propValMethod = MethodSpec.methodBuilder("getPropertyValue")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(propValueReturnType)
                .addParameter(TypeName.get(type.asType()), "obj");

        propValMethod.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyMap()", Collections.class)
                .endControlFlow();
        propValMethod.addStatement("$T<$T, Object> map = new $T<>()", Map.class, wildcardProperty, LinkedHashMap.class);

        for (FieldMeta f : fields) {
            if (!f.isEntityRelation) {
                if (isRecord || f.hasGetter) {
                    String accessor = isRecord ? f.name + "()" : f.getterName + "()";
                    if (f.isPrimitive) {
                        propValMethod.addStatement("map.put(ROOT.$L, obj.$L)", f.name, accessor);
                    } else {
                        propValMethod.beginControlFlow("if (obj.$L != null)", accessor)
                                .addStatement("map.put(ROOT.$L, obj.$L)", f.name, accessor)
                                .endControlFlow();
                    }
                } else {
                    // Safe reflection fallback when no getter exists
                    propValMethod.addStatement("Object _val_$L = _get(obj, $S)", f.name, f.name);
                    propValMethod.beginControlFlow("if (_val_$L != null)", f.name)
                            .addStatement("map.put(ROOT.$L, _val_$L)", f.name, f.name)
                            .endControlFlow();
                }
            }
        }
        propValMethod.addStatement("return map");
        clazz.addMethod(propValMethod.build());

        // Static getFieldMap(obj)
        TypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(Object.class)
        );
        MethodSpec.Builder fieldMapMethod = MethodSpec.methodBuilder("getFieldMap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mapType)
                .addParameter(TypeName.get(type.asType()), "obj");

        fieldMapMethod.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyMap()", Collections.class)
                .endControlFlow();
        fieldMapMethod.addStatement("$T<String, Object> m = new $T<>()", Map.class, LinkedHashMap.class);

        for (FieldMeta f : fields) {
            if (isRecord || f.hasGetter) {
                String accessor = isRecord ? f.name + "()" : f.getterName + "()";
                if (f.isPrimitive) {
                    fieldMapMethod.addStatement("m.put($S, obj.$L)", f.name, accessor);
                } else {
                    fieldMapMethod.beginControlFlow("if (obj.$L != null)", accessor)
                            .addStatement("m.put($S, obj.$L)", f.name, accessor)
                            .endControlFlow();
                }
            } else {
                // Safe reflection fallback when no getter exists
                fieldMapMethod.addStatement("Object _val_$L = _get(obj, $S)", f.name, f.name);
                fieldMapMethod.beginControlFlow("if (_val_$L != null)", f.name)
                        .addStatement("m.put($S, _val_$L)", f.name, f.name)
                        .endControlFlow();
            }
        }
        fieldMapMethod.addStatement("return m");
        clazz.addMethod(fieldMapMethod.build());

        // Add nested Path class to outer class
        clazz.addType(pathBuilder.build());

        // Write java file
        JavaFile.builder(pkg, clazz.build())
                .indent("    ")
                .build()
                .writeTo(processingEnv.getFiler());
    }

    // =================================================================
    // TYPE & FIELD EXTRACTION
    // =================================================================

    private List<FieldMeta> extractFields(TypeElement type, Map<String, TypeElement> registry) {
        List<FieldMeta> list = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        if (type.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement rc : type.getRecordComponents()) {
                String name = rc.getSimpleName().toString();
                if (seenNames.add(name)) {
                    list.add(new FieldMeta(name, rc.asType(), name, true, registry));
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
                            list.add(new FieldMeta(name, v.asType(), getter, hasGetter, registry));
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

    private String resolveGetter(TypeElement type, VariableElement field) {
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

    private boolean hasMethod(TypeElement type, String methodName) {
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

    private boolean hasLombokGetter(TypeElement type, VariableElement field) {
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

    private TypeName getBoxedTypeName(TypeMirror t) {
        if (t.getKind().isPrimitive()) {
            return TypeName.get(typeUtils.boxedClass((PrimitiveType) t).asType());
        }
        if (t instanceof DeclaredType dt) {
            return ClassName.get((TypeElement) dt.asElement());
        }
        return TypeName.get(t);
    }

    private CodeBlock buildFieldInit(
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

    private TypeElement getAnnotatedEntity(TypeMirror t, Map<String, TypeElement> registry) {
        if (t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
            String qName = te.getQualifiedName().toString();
            if (registry.containsKey(qName) || te.getAnnotation(EntityFields.class) != null) {
                return te;
            }
        }
        return null;
    }

    private boolean isCollectionType(TypeMirror t) {
        String s = typeUtils.erasure(t).toString();
        return s.equals("java.util.List")
                || s.equals("java.util.Set")
                || s.equals("java.util.Collection")
                || s.equals("java.util.Queue")
                || s.equals("java.util.Deque");
    }

    private TypeMirror extractElementType(TypeMirror t) {
        if (t instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            return dt.getTypeArguments().get(0);
        }
        return null;
    }

    // =================================================================
    // FIELD METADATA
    // =================================================================

    class FieldMeta {
        String name;
        TypeMirror type;
        TypeMirror elementType;
        String getterName;
        boolean hasGetter;
        boolean isPrimitive;
        boolean isCollection;
        boolean isEntityRelation;
        TypeElement relatedEntity;

        FieldMeta(String name, TypeMirror type, String getterName, boolean hasGetter, Map<String, TypeElement> registry) {
            this.name = name;
            this.type = type;
            this.getterName = getterName;
            this.hasGetter = hasGetter;
            this.isPrimitive = type.getKind().isPrimitive();
            this.isCollection = isCollectionType(type);
            this.elementType = extractElementType(type);

            if (isCollection) {
                if (this.elementType != null) {
                    this.relatedEntity = getAnnotatedEntity(this.elementType, registry);
                    this.isEntityRelation = (this.relatedEntity != null);
                } else {
                    this.relatedEntity = null;
                    this.isEntityRelation = false;
                }
            } else {
                this.relatedEntity = getAnnotatedEntity(this.type, registry);
                this.isEntityRelation = (this.relatedEntity != null);
            }
        }
    }
}
