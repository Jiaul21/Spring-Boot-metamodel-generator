package com.jiaul.metamodelgenerator.processor;

import com.google.auto.service.AutoService;
import com.jiaul.metamodelgenerator.annotation.EntityFields;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor for {@link EntityFields}.
 * <p>
 * Orchestrates entity discovery and metamodel code generation by coordinating
 * {@link FieldExtractor}, {@link PathClassGenerator}, and {@link MetamodelClassGenerator}.
 */
@AutoService(Processor.class)
public class FieldsProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Filer filer;
    private Messager messager;
    private FieldExtractor extractor;
    private PathClassGenerator pathGenerator;
    private MetamodelClassGenerator metamodelGenerator;

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
        this.elementUtils = env.getElementUtils();
        this.filer = env.getFiler();
        this.messager = env.getMessager();
        this.extractor = new FieldExtractor(env.getTypeUtils(), this.elementUtils);
        this.pathGenerator = new PathClassGenerator();
        this.metamodelGenerator = new MetamodelClassGenerator();
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
                metamodelGenerator.generate(type, entityRegistry, extractor, pathGenerator, elementUtils, filer);
            } catch (Exception ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate metamodel for " + type.getQualifiedName() + ": " + ex.getMessage(), type);
            }
        }

        return true;
    }
}
