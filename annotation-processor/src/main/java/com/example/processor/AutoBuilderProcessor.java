package com.example.processor;

import com.example.annotations.AutoBuilder;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.FieldSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.Modifier.*;

/**
 * Generates a standalone {TypeName}Builder class with:
 * - Fluent setters for each non-static field (method name equals field name).
 * - build() method: tries to use no-args constructor + setters; falls back to direct field assignment if accessible.
 */
@SupportedAnnotationTypes("com.example.annotations.AutoBuilder")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AutoBuilderProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();

        for (Element element : roundEnv.getElementsAnnotatedWith(AutoBuilder.class)) {
            if (!(element instanceof TypeElement)) continue;
            TypeElement type = (TypeElement) element;

            PackageElement pkg = processingEnv.getElementUtils().getPackageOf(type);
            String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
            String originalName = type.getSimpleName().toString();
            String builderName = originalName + "Builder";

            List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements())
                    .stream()
                    .filter(f -> !f.getModifiers().contains(STATIC))
                    .collect(Collectors.toList());

            List<ExecutableElement> methods = ElementFilter.methodsIn(type.getEnclosedElements());

            // Builder fields
            List<FieldSpec> builderFields = new ArrayList<>();
            for (VariableElement f : fields) {
                builderFields.add(FieldSpec.builder(
                        com.squareup.javapoet.TypeName.get(f.asType()),
                        f.getSimpleName().toString(),
                        PRIVATE).build());
            }

            // Fluent setters: method name equals field name
            List<MethodSpec> fluentSetters = new ArrayList<>();
            ClassName builderClassName = ClassName.get(packageName, builderName);
            for (VariableElement f : fields) {
                String fname = f.getSimpleName().toString();
                ParameterSpec param = ParameterSpec.builder(
                        com.squareup.javapoet.TypeName.get(f.asType()), fname).build();
                MethodSpec setter = MethodSpec.methodBuilder(fname)
                        .addModifiers(PUBLIC)
                        .returns(builderClassName)
                        .addParameter(param)
                        .addStatement("this.$N = $N", fname, fname)
                        .addStatement("return this")
                        .build();
                fluentSetters.add(setter);
            }

            // build() method
            ClassName originalTypeName = ClassName.get(type);
            MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                    .addModifiers(PUBLIC)
                    .returns(originalTypeName);

            boolean hasNoArgCtor = methods.stream()
                    .filter(m -> m.getKind() == ElementKind.CONSTRUCTOR)
                    .anyMatch(m -> m.getParameters().isEmpty() && m.getModifiers().contains(PUBLIC));

            if (hasNoArgCtor) {
                buildMethod.addStatement("$T instance = new $T()", originalTypeName, originalTypeName);
                for (VariableElement f : fields) {
                    String fname = f.getSimpleName().toString();
                    String setterName = "set" + capitalize(fname);
                    Optional<ExecutableElement> setter = methods.stream()
                            .filter(m -> m.getSimpleName().contentEquals(setterName)
                                    && m.getParameters().size() == 1)
                            .findFirst();
                    if (setter.isPresent()) {
                        buildMethod.addStatement("instance.$N(this.$N)", setterName, fname);
                    } else if (!f.getModifiers().contains(PRIVATE)) {
                        buildMethod.addStatement("instance.$N = this.$N", fname, fname);
                    } else {
                        processingEnv.getMessager().printMessage(
                                Diagnostic.Kind.NOTE,
                                "No setter and field is private for '" + fname + "' in " + originalName +
                                        ". The generated code may not compile.");
                        buildMethod.addComment("No setter and field is private: $N", fname);
                    }
                }
                buildMethod.addStatement("return instance");
            } else {
                // Fallback: try to find an all-args public constructor with same number of non-static fields (order by declaration)
                List<ExecutableElement> ctors = methods.stream()
                        .filter(m -> m.getKind() == ElementKind.CONSTRUCTOR && m.getModifiers().contains(PUBLIC))
                        .collect(Collectors.toList());
                Optional<ExecutableElement> matchingCtor = ctors.stream()
                        .filter(c -> c.getParameters().size() == fields.size())
                        .findFirst();

                if (matchingCtor.isPresent()) {
                    String args = fields.stream().map(f -> "this." + f.getSimpleName().toString())
                            .collect(Collectors.joining(", "));
                    buildMethod.addStatement("return new $T($L)", originalTypeName, args);
                } else {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.WARNING,
                            "No public no-args constructor or matching all-args constructor found for " + originalName +
                                    ". Generated builder may fail.");
                    buildMethod.addComment("Cannot find suitable constructor; returning null to avoid compile error")
                            .addStatement("return null");
                }
            }

            TypeSpec builder = TypeSpec.classBuilder(builderName)
                    .addModifiers(PUBLIC)
                    .addFields(builderFields)
                    .addMethods(fluentSetters)
                    .addMethod(buildMethod.build())
                    .build();

            try {
                JavaFile.builder(packageName, builder).build().writeTo(filer);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Failed to write builder for " + originalName + ": " + e.getMessage());
            }
        }

        return true;
    }

    private static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}