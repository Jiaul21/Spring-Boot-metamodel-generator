package com.jiaul.metamodelgenerator.processor;

import com.jiaul.metamodelgenerator.metadata.FieldProperty;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.*;

/**
 * Generator for the nested {@code Path} class inside each metamodel class.
 */
public class PathClassGenerator {

    public TypeSpec generatePathClass(
            TypeElement type,
            List<FieldMeta> fields,
            FieldExtractor extractor,
            Elements elementUtils
    ) {
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

        TypeSpec.Builder pathBuilder = TypeSpec.classBuilder("Path")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addSuperinterface(ClassName.get(CharSequence.class))
                .addJavadoc("Type-safe relation path and property holder for {@link $T}.\n", type.asType());

        pathBuilder.addField(FieldSpec.builder(String.class, "_path", Modifier.PRIVATE, Modifier.FINAL).build());

        TypeName entityType = extractor.getBoxedTypeName(type.asType());
        TypeName selfPropertyType = ParameterizedTypeName.get(fieldProperty, entityType, ClassName.get(Void.class));
        boolean hasFieldNamedProperty = fields.stream().anyMatch(f -> f.getName().equals("property"));
        if (!hasFieldNamedProperty) {
            pathBuilder.addField(FieldSpec.builder(selfPropertyType, "property", Modifier.PUBLIC, Modifier.FINAL).build());
        }

        // Instance fields on Path
        for (FieldMeta f : fields) {
            if (f.isEntityRelation()) {
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.getRelatedEntity()).getQualifiedName().toString(),
                        f.getRelatedEntity().getSimpleName() + "Fields",
                        "Path"
                );
                pathBuilder.addField(FieldSpec.builder(relatedPathClass, f.getName(), Modifier.PUBLIC, Modifier.FINAL).build());
            } else {
                TypeName mainType = extractor.getBoxedTypeName(f.getType());
                TypeName elemType = f.getElementType() != null ? extractor.getBoxedTypeName(f.getElementType()) : ClassName.get(Void.class);
                TypeName propType = ParameterizedTypeName.get(fieldProperty, mainType, elemType);

                pathBuilder.addField(FieldSpec.builder(propType, f.getName(), Modifier.PUBLIC, Modifier.FINAL).build());
            }
        }

        pathBuilder.addField(FieldSpec.builder(mapPropertiesType, "FIELD_MAP", Modifier.PUBLIC, Modifier.FINAL).build());

        // Constructors
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

        if (!hasFieldNamedProperty) {
            pathCtor.addStatement("this.property = new $T<>(this._path, $T.class, null)", fieldProperty, entityType);
        }

        // Initialize scalar properties
        for (FieldMeta f : fields) {
            if (!f.isEntityRelation()) {
                TypeName mainType = extractor.getBoxedTypeName(f.getType());
                TypeName elemType = f.getElementType() != null ? extractor.getBoxedTypeName(f.getElementType()) : ClassName.get(Void.class);
                CodeBlock initBlock = extractor.buildFieldInit(fieldProperty, f.getName(), mainType, elemType, f.isCollection());

                pathCtor.addStatement("this.$L = $L", f.getName(), initBlock);
            }
        }

        // Initialize FIELD_MAP
        pathCtor.addStatement("$T<String, $T> _map = new $T<>()", Map.class, wildcardProperty, LinkedHashMap.class);
        for (FieldMeta f : fields) {
            if (!f.isEntityRelation()) {
                pathCtor.addStatement("_map.put($S, this.$L)", f.getName(), f.getName());
            } else {
                TypeName mainType = extractor.getBoxedTypeName(f.getType());
                TypeName elemType = f.getElementType() != null ? extractor.getBoxedTypeName(f.getElementType()) : ClassName.get(Void.class);
                CodeBlock initBlock = extractor.buildFieldInit(fieldProperty, f.getName(), mainType, elemType, f.isCollection());
                pathCtor.addStatement("_map.put($S, $L)", f.getName(), initBlock);
            }
        }
        pathCtor.addStatement("this.FIELD_MAP = $T.unmodifiableMap(_map)", Collections.class);

        // Cycle tracking setup for relations
        pathCtor.addStatement("$T<String> nextVisited = new $T<>(visitedTypes)", Set.class, HashSet.class);
        pathCtor.addStatement("nextVisited.add($S)", type.getQualifiedName().toString());

        for (FieldMeta f : fields) {
            if (f.isEntityRelation()) {
                String relatedQName = f.getRelatedEntity().getQualifiedName().toString();
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.getRelatedEntity()).getQualifiedName().toString(),
                        f.getRelatedEntity().getSimpleName() + "Fields",
                        "Path"
                );

                pathCtor.beginControlFlow("if (!visitedTypes.contains($S))", relatedQName)
                        .addStatement("this.$L = new $T(prefix(this._path, $S), nextVisited)", f.getName(), relatedPathClass, f.getName())
                        .nextControlFlow("else")
                        .addStatement("this.$L = null", f.getName())
                        .endControlFlow();
            }
        }

        pathBuilder.addMethod(pathCtor.build());

        // On-demand methods for relations (enables unbounded chaining if developer explicitly calls method)
        for (FieldMeta f : fields) {
            if (f.isEntityRelation()) {
                ClassName relatedPathClass = ClassName.get(
                        elementUtils.getPackageOf(f.getRelatedEntity()).getQualifiedName().toString(),
                        f.getRelatedEntity().getSimpleName() + "Fields",
                        "Path"
                );

                pathBuilder.addMethod(MethodSpec.methodBuilder(f.getName())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(relatedPathClass)
                        .addStatement("return new $T(prefix(this._path, $S), $T.emptySet())", relatedPathClass, f.getName(), Collections.class)
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

        MethodSpec.Builder asPropMethod = MethodSpec.methodBuilder("asProperty")
                .addModifiers(Modifier.PUBLIC)
                .returns(selfPropertyType);
        if (!hasFieldNamedProperty) {
            asPropMethod.addStatement("return this.property");
        } else {
            asPropMethod.addStatement("return new $T<>(this._path, $T.class, null)", fieldProperty, entityType);
        }
        pathBuilder.addMethod(asPropMethod.build());

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

        return pathBuilder.build();
    }
}
