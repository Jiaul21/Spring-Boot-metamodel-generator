package com.jiaul.metamodelgenerator.processor;

import com.jiaul.metamodelgenerator.metadata.ModelField;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.*;

/**
 * Generator for the top-level metamodel class (e.g. {@code StudentFields}) and Java source file emission.
 */
public class MetamodelClassGenerator {

    public void generate(
            TypeElement type,
            Map<String, TypeElement> entityRegistry,
            FieldExtractor extractor,
            PathClassGenerator pathGenerator,
            Elements elementUtils,
            Filer filer,
            boolean generateFields,
            boolean generateMaps,
            boolean generateLists
    ) throws IOException {
        String pkg = elementUtils.getPackageOf(type).getQualifiedName().toString();
        String className = type.getSimpleName() + "Fields";
        boolean isRecord = type.getKind() == ElementKind.RECORD;

        List<FieldMeta> fields = extractor.extractFields(type, entityRegistry);

        ClassName pathClass = ClassName.get(pkg, className, "Path");
        ClassName modelField = ClassName.get(ModelField.class);

        TypeName wildcardProperty = ParameterizedTypeName.get(
                modelField,
                WildcardTypeName.subtypeOf(Object.class),
                WildcardTypeName.subtypeOf(Object.class)
        );
        TypeName mapPropertiesType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                wildcardProperty
        );

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

        if (generateFields) {
            // ROOT static instance
            clazz.addField(FieldSpec.builder(pathClass, "ROOT", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T(\"\", $T.emptySet())", pathClass, Collections.class)
                    .build());

            // Static fields on outer class delegating to ROOT
            for (FieldMeta f : fields) {
                if (f.isEntityRelation()) {
                    ClassName relatedPathClass = ClassName.get(
                            elementUtils.getPackageOf(f.getRelatedEntity()).getQualifiedName().toString(),
                            f.getRelatedEntity().getSimpleName() + "Fields",
                            "Path"
                    );

                    clazz.addField(FieldSpec.builder(relatedPathClass, f.getName(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .initializer("ROOT.$L", f.getName())
                            .build());
                } else {
                    TypeName mainType = extractor.getBoxedTypeName(f.getType());
                    TypeName elemType = f.getElementType() != null ? extractor.getBoxedTypeName(f.getElementType()) : ClassName.get(Void.class);
                    TypeName propType = ParameterizedTypeName.get(modelField, mainType, elemType);

                    clazz.addField(FieldSpec.builder(propType, f.getName(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .initializer("ROOT.$L", f.getName())
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

            // Static FIELD_MAP
            clazz.addField(FieldSpec.builder(mapPropertiesType, "FIELD_MAP", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("ROOT.FIELD_MAP")
                    .build());

            // Static getProperty
            clazz.addMethod(MethodSpec.methodBuilder("getProperty")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(wildcardProperty)
                    .addParameter(String.class, "fieldName")
                    .addStatement("return ROOT.getProperty(fieldName)")
                    .build());

            // Static asProperty()
            TypeName entityType = extractor.getBoxedTypeName(type.asType());
            TypeName selfPropertyType = ParameterizedTypeName.get(modelField, entityType, ClassName.get(Void.class));
            clazz.addMethod(MethodSpec.methodBuilder("asProperty")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(selfPropertyType)
                    .addStatement("return ROOT.asProperty()")
                    .build());

            // Add nested Path class to outer class
            TypeSpec pathSpec = pathGenerator.generatePathClass(type, fields, extractor, elementUtils);
            clazz.addType(pathSpec);
        }

        // -------------------------------------------------------------
        // Map Utilities (Generated only if generateMaps is true)
        // -------------------------------------------------------------
        if (generateMaps) {
            clazz.addMethod(buildToMapMethod(type, fields, isRecord));
            clazz.addMethod(buildToNonNullMapMethod(type, fields, isRecord));
        }

        // -------------------------------------------------------------
        // List Utilities (Generated only if generateLists is true)
        // -------------------------------------------------------------
        if (generateLists) {
            clazz.addMethod(buildToValuesMethod(type, fields, isRecord));
            clazz.addMethod(buildToNonNullValuesMethod(type, fields, isRecord));
        }

        // Write java file
        JavaFile.builder(pkg, clazz.build())
                .indent("    ")
                .build()
                .writeTo(filer);
    }

    private MethodSpec buildToMapMethod(
            TypeElement type,
            List<FieldMeta> fields,
            boolean isRecord
    ) {
        TypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(Object.class)
        );
        MethodSpec.Builder method = MethodSpec.methodBuilder("toMap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Extracts all fields (including null values) into an ordered {@link $T}.\n", Map.class)
                .returns(mapType)
                .addParameter(TypeName.get(type.asType()), "obj");

        method.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyMap()", Collections.class)
                .endControlFlow();
        method.addStatement("$T<String, Object> m = new $T<>()", Map.class, LinkedHashMap.class);

        for (FieldMeta f : fields) {
            if (isRecord || f.hasGetter()) {
                String accessor = isRecord ? f.getName() + "()" : f.getGetterName() + "()";
                method.addStatement("m.put($S, obj.$L)", f.getName(), accessor);
            }
        }
        method.addStatement("return m");
        return method.build();
    }

    private MethodSpec buildToNonNullMapMethod(
            TypeElement type,
            List<FieldMeta> fields,
            boolean isRecord
    ) {
        TypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(Object.class)
        );
        MethodSpec.Builder method = MethodSpec.methodBuilder("toNonNullMap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Extracts only non-null fields into an ordered {@link $T}.\n", Map.class)
                .returns(mapType)
                .addParameter(TypeName.get(type.asType()), "obj");

        method.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyMap()", Collections.class)
                .endControlFlow();
        method.addStatement("$T<String, Object> m = new $T<>()", Map.class, LinkedHashMap.class);

        for (FieldMeta f : fields) {
            if (isRecord || f.hasGetter()) {
                String accessor = isRecord ? f.getName() + "()" : f.getGetterName() + "()";
                if (f.isPrimitive()) {
                    method.addStatement("m.put($S, obj.$L)", f.getName(), accessor);
                } else {
                    method.beginControlFlow("if (obj.$L != null)", accessor)
                            .addStatement("m.put($S, obj.$L)", f.getName(), accessor)
                            .endControlFlow();
                }
            }
        }
        method.addStatement("return m");
        return method.build();
    }

    private MethodSpec buildToValuesMethod(
            TypeElement type,
            List<FieldMeta> fields,
            boolean isRecord
    ) {
        TypeName listType = ParameterizedTypeName.get(
                ClassName.get(List.class),
                ClassName.get(Object.class)
        );
        MethodSpec.Builder method = MethodSpec.methodBuilder("toValues")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Extracts all field values (including nulls) in field declaration order.\n")
                .returns(listType)
                .addParameter(TypeName.get(type.asType()), "obj");

        method.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyList()", Collections.class)
                .endControlFlow();
        method.addStatement("$T<Object> list = new $T<>()", List.class, ArrayList.class);

        for (FieldMeta f : fields) {
            if (isRecord || f.hasGetter()) {
                String accessor = isRecord ? f.getName() + "()" : f.getGetterName() + "()";
                method.addStatement("list.add(obj.$L)", accessor);
            }
        }
        method.addStatement("return list");
        return method.build();
    }

    private MethodSpec buildToNonNullValuesMethod(
            TypeElement type,
            List<FieldMeta> fields,
            boolean isRecord
    ) {
        TypeName listType = ParameterizedTypeName.get(
                ClassName.get(List.class),
                ClassName.get(Object.class)
        );
        MethodSpec.Builder method = MethodSpec.methodBuilder("toNonNullValues")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Extracts only non-null field values in field declaration order.\n")
                .returns(listType)
                .addParameter(TypeName.get(type.asType()), "obj");

        method.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyList()", Collections.class)
                .endControlFlow();
        method.addStatement("$T<Object> list = new $T<>()", List.class, ArrayList.class);

        for (FieldMeta f : fields) {
            if (isRecord || f.hasGetter()) {
                String accessor = isRecord ? f.getName() + "()" : f.getGetterName() + "()";
                if (f.isPrimitive()) {
                    method.addStatement("list.add(obj.$L)", accessor);
                } else {
                    method.beginControlFlow("if (obj.$L != null)", accessor)
                            .addStatement("list.add(obj.$L)", accessor)
                            .endControlFlow();
                }
            }
        }
        method.addStatement("return list");
        return method.build();
    }
}
