package com.example.processor;

import com.example.annotations.AutoMapper;
import com.squareup.javapoet.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.Modifier.*;

/**
 * Generates {FromSimpleName}To{TargetSimpleName}Mapper with:
 * - public static Target map(From from)
 * - public static From mapBack(Target target)
 * Fields are mapped by same name and assignment-compatible type.
 * Prefer getters/setters; else try direct field access (if not private).
 */
@SupportedAnnotationTypes("com.example.annotations.AutoMapper")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AutoMapperProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();

        for (Element element : roundEnv.getElementsAnnotatedWith(AutoMapper.class)) {
            if (!(element instanceof TypeElement)) continue;
            TypeElement targetType = (TypeElement) element;

            AutoMapper ann = targetType.getAnnotation(AutoMapper.class);
            TypeElement fromType = getFromTypeElement(ann);

            if (fromType == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Unable to resolve 'from' type for @AutoMapper on " + targetType.getQualifiedName());
                continue;
            }

            PackageElement pkg = processingEnv.getElementUtils().getPackageOf(targetType);
            String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();

            String fromSimple = fromType.getSimpleName().toString();
            String targetSimple = targetType.getSimpleName().toString();
            String mapperName = fromSimple + "To" + targetSimple + "Mapper";

            Map<String, VariableElement> fromFields = ElementFilter.fieldsIn(fromType.getEnclosedElements())
                    .stream().filter(f -> !f.getModifiers().contains(STATIC))
                    .collect(Collectors.toMap(f -> f.getSimpleName().toString(), f -> f, (a, b) -> a, LinkedHashMap::new));
            Map<String, VariableElement> targetFields = ElementFilter.fieldsIn(targetType.getEnclosedElements())
                    .stream().filter(f -> !f.getModifiers().contains(STATIC))
                    .collect(Collectors.toMap(f -> f.getSimpleName().toString(), f -> f, (a, b) -> a, LinkedHashMap::new));

            List<ExecutableElement> fromMethods = ElementFilter.methodsIn(fromType.getEnclosedElements());
            List<ExecutableElement> targetMethods = ElementFilter.methodsIn(targetType.getEnclosedElements());

            // Build map(from)
            MethodSpec map = buildMapMethod(fromType, targetType, fromFields, targetFields, fromMethods, targetMethods);

            // Build mapBack(target)
            MethodSpec mapBack = buildMapBackMethod(fromType, targetType, fromFields, targetFields, fromMethods, targetMethods);

            TypeSpec mapper = TypeSpec.classBuilder(mapperName)
                    .addModifiers(PUBLIC, FINAL)
                    .addMethod(map)
                    .addMethod(mapBack)
                    .build();

            try {
                JavaFile.builder(packageName, mapper).build().writeTo(filer);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Failed to write mapper " + mapperName + ": " + e.getMessage());
            }
        }

        return true;
    }

    private MethodSpec buildMapMethod(TypeElement fromType,
                                      TypeElement targetType,
                                      Map<String, VariableElement> fromFields,
                                      Map<String, VariableElement> targetFields,
                                      List<ExecutableElement> fromMethods,
                                      List<ExecutableElement> targetMethods) {

        ClassName fromClass = ClassName.get(fromType);
        ClassName targetClass = ClassName.get(targetType);

        MethodSpec.Builder m = MethodSpec.methodBuilder("map")
                .addModifiers(PUBLIC, STATIC)
                .returns(targetClass)
                .addParameter(fromClass, "from");

        m.addStatement("$T target = new $T()", targetClass, targetClass);

        for (Map.Entry<String, VariableElement> e : targetFields.entrySet()) {
            String name = e.getKey();
            VariableElement targetField = e.getValue();
            VariableElement fromField = fromFields.get(name);
            if (fromField == null) continue;

            // Resolve getter on source or direct field
            String getterName = resolveGetterName(fromField);
            boolean hasGetter = hasMethod(fromMethods, getterName, 0);

            // Resolve setter on target or direct field
            String setterName = "set" + capitalize(name);
            boolean hasSetter = hasMethod(targetMethods, setterName, 1);

            if (hasGetter && hasSetter) {
                m.addStatement("target.$N(from.$N())", setterName, getterName);
            } else if (hasGetter && !targetField.getModifiers().contains(PRIVATE)) {
                m.addStatement("target.$N = from.$N()", name, getterName);
            } else if (!fromField.getModifiers().contains(PRIVATE) && hasSetter) {
                m.addStatement("target.$N(from.$N)", setterName, name);
            } else if (!fromField.getModifiers().contains(PRIVATE) && !targetField.getModifiers().contains(PRIVATE)) {
                m.addStatement("target.$N = from.$N", name, name);
            } else {
                m.addComment("Skipped mapping for $N due to access restrictions or missing accessors", name);
            }
        }

        return m.addStatement("return target").build();
    }

    private MethodSpec buildMapBackMethod(TypeElement fromType,
                                          TypeElement targetType,
                                          Map<String, VariableElement> fromFields,
                                          Map<String, VariableElement> targetFields,
                                          List<ExecutableElement> fromMethods,
                                          List<ExecutableElement> targetMethods) {

        ClassName fromClass = ClassName.get(fromType);
        ClassName targetClass = ClassName.get(targetType);

        MethodSpec.Builder m = MethodSpec.methodBuilder("mapBack")
                .addModifiers(PUBLIC, STATIC)
                .returns(fromClass)
                .addParameter(targetClass, "target");

        m.addStatement("$T from = new $T()", fromClass, fromClass);

        for (Map.Entry<String, VariableElement> e : fromFields.entrySet()) {
            String name = e.getKey();
            VariableElement fromField = e.getValue();
            VariableElement targetField = targetFields.get(name);
            if (targetField == null) continue;

            // Resolve getter on target or direct field
            String getterName = resolveGetterName(targetField);
            boolean hasGetter = hasMethod(targetMethods, getterName, 0);

            // Resolve setter on source or direct field
            String setterName = "set" + capitalize(name);
            boolean hasSetter = hasMethod(fromMethods, setterName, 1);

            if (hasGetter && hasSetter) {
                m.addStatement("from.$N(target.$N())", setterName, getterName);
            } else if (hasGetter && !fromField.getModifiers().contains(PRIVATE)) {
                m.addStatement("from.$N = target.$N()", name, getterName);
            } else if (!targetField.getModifiers().contains(PRIVATE) && hasSetter) {
                m.addStatement("from.$N(target.$N)", setterName, name);
            } else if (!targetField.getModifiers().contains(PRIVATE) && !fromField.getModifiers().contains(PRIVATE)) {
                m.addStatement("from.$N = target.$N", name, name);
            } else {
                m.addComment("Skipped mapping for $N due to access restrictions or missing accessors", name);
            }
        }

        return m.addStatement("return from").build();
    }

    private boolean hasMethod(List<ExecutableElement> methods, String name, int paramCount) {
        return methods.stream()
                .anyMatch(m -> m.getSimpleName().contentEquals(name)
                        && m.getParameters().size() == paramCount
                        && m.getModifiers().contains(PUBLIC));
    }

    private String resolveGetterName(VariableElement field) {
        String fname = field.getSimpleName().toString();
        String cap = capitalize(fname);
        if (field.asType().toString().equals("boolean")) {
            return "is" + cap;
        }
        return "get" + cap;
    }

    private static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private TypeElement getFromTypeElement(AutoMapper ann) {
        if (ann == null) return null;
        try {
            ann.from(); // This will throw at compile time
            return null; // Not reached
        } catch (MirroredTypeException mte) {
            TypeMirror tm = mte.getTypeMirror();
            if (tm == null) return null;
            Element e = processingEnv.getTypeUtils().asElement(tm);
            if (e instanceof TypeElement) {
                return (TypeElement) e;
            }
            return null;
        }
    }
}