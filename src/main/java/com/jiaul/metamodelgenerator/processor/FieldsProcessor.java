package com.jiaul.metamodelgenerator.processor;

import com.google.auto.service.AutoService;
import com.jiaul.metamodelgenerator.annotation.ModelFields;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor for {@link ModelFields}.
 * <p>
 * Orchestrates model discovery and metamodel code generation by coordinating
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
        Set<String> set = new LinkedHashSet<>();
        set.add(ModelFields.class.getCanonicalName());
        return set;
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
        this.extractor = new FieldExtractor(env.getTypeUtils());
        this.pathGenerator = new PathClassGenerator();
        this.metamodelGenerator = new MetamodelClassGenerator();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        // 1. Discover all classes/records annotated with @ModelFields
        Map<String, TypeElement> entityRegistry = new LinkedHashMap<>();
        for (Element e : roundEnv.getElementsAnnotatedWith(ModelFields.class)) {
            if (e instanceof TypeElement type) {
                entityRegistry.put(type.getQualifiedName().toString(), type);
            }
        }

        if (entityRegistry.isEmpty()) {
            return false;
        }

        // 2. Generate fields classes for each discovered model
        for (TypeElement type : entityRegistry.values()) {
            boolean fields = true;
            boolean maps = false;
            boolean lists = false;

            ModelFields mf = type.getAnnotation(ModelFields.class);
            if (mf != null) {
                fields = mf.fields();
                maps = mf.maps();
                lists = mf.lists();
            }

            try {
                metamodelGenerator.generate(
                        type,
                        entityRegistry,
                        extractor,
                        pathGenerator,
                        elementUtils,
                        filer,
                        fields,
                        maps,
                        lists
                );
            } catch (Exception ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate metamodel for " + type.getQualifiedName() + ": " + ex.getMessage(), type);
            }
        }

        return true;
    }
}
