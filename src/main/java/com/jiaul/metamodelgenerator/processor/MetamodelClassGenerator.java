package com.jiaul.metamodelgenerator.processor;

import com.jiaul.metamodelgenerator.metadata.FieldProperty;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            Filer filer
    ) throws IOException {
        String pkg = elementUtils.getPackageOf(type).getQualifiedName().toString();
        String className = type.getSimpleName() + "Fields";
        boolean isRecord = type.getKind() == ElementKind.RECORD;

        List<FieldMeta> fields = extractor.extractFields(type, entityRegistry);

        ClassName pathClass = ClassName.get(pkg, className, "Path");
        ClassName fieldProperty = ClassName.get(FieldProperty.class);

        TypeName wildcardProperty = ParameterizedTypeName.get(
                fieldProperty,
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
                TypeName propType = ParameterizedTypeName.get(fieldProperty, mainType, elemType);

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

        // Static asProperty()
        TypeName entityType = extractor.getBoxedTypeName(type.asType());
        TypeName selfPropertyType = ParameterizedTypeName.get(fieldProperty, entityType, ClassName.get(Void.class));
        clazz.addMethod(MethodSpec.methodBuilder("asProperty")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(selfPropertyType)
                .addStatement("return ROOT.asProperty()")
                .build());

        // Static toNonNullFieldMap(obj)
        clazz.addMethod(buildToNonNullFieldMapMethod(type, fields, isRecord));

        // Add nested Path class to outer class
        TypeSpec pathSpec = pathGenerator.generatePathClass(type, fields, extractor, elementUtils);
        clazz.addType(pathSpec);

        // Write java file
        JavaFile.builder(pkg, clazz.build())
                .indent("    ")
                .build()
                .writeTo(filer);
    }

    private MethodSpec buildToNonNullFieldMapMethod(
            TypeElement type,
            List<FieldMeta> fields,
            boolean isRecord
    ) {
        TypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(Object.class)
        );
        MethodSpec.Builder fieldMapMethod = MethodSpec.methodBuilder("toNonNullFieldMap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mapType)
                .addParameter(TypeName.get(type.asType()), "obj");

        fieldMapMethod.beginControlFlow("if (obj == null)")
                .addStatement("return $T.emptyMap()", Collections.class)
                .endControlFlow();
        fieldMapMethod.addStatement("$T<String, Object> m = new $T<>()", Map.class, LinkedHashMap.class);

        for (FieldMeta f : fields) {
            if (isRecord || f.hasGetter()) {
                String accessor = isRecord ? f.getName() + "()" : f.getGetterName() + "()";
                if (f.isPrimitive()) {
                    fieldMapMethod.addStatement("m.put($S, obj.$L)", f.getName(), accessor);
                } else {
                    fieldMapMethod.beginControlFlow("if (obj.$L != null)", accessor)
                            .addStatement("m.put($S, obj.$L)", f.getName(), accessor)
                            .endControlFlow();
                }
            }
        }
        fieldMapMethod.addStatement("return m");
        return fieldMapMethod.build();
    }
}
