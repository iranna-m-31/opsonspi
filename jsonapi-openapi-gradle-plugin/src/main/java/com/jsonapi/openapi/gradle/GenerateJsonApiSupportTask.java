package com.jsonapi.openapi.gradle;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.Modifier;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class GenerateJsonApiSupportTask extends DefaultTask {

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getInputJson();

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getSpecFile();

  @Input
  public abstract Property<String> getGeneratedPackage();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @TaskAction
  public void generate() throws IOException {
    File jsonFile = getInputJson().getAsFile().get();
    File specFile = getSpecFile().getAsFile().get();
    String pkg = getGeneratedPackage().get();
    File outDir = getOutputDirectory().getAsFile().get();
    Files.createDirectories(outDir.toPath());

    OpenAPI openAPI;
    try {
      openAPI =
          io.swagger.v3.core.util.Json.mapper().readValue(jsonFile, OpenAPI.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read resolved OpenAPI JSON: " + jsonFile, e);
    }

    if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
      throw new IllegalStateException("No paths in OpenAPI document: " + jsonFile);
    }

    Map<String, Map<String, String>> entityOps = EntitySchemaSupport.loadOperationServices(specFile);
    List<OperationEntry> operations = new ArrayList<>();
    openAPI
        .getPaths()
        .forEach(
            (path, pathItem) -> {
              addOp(operations, path, "GET", pathItem.getGet(), entityOps);
              addOp(operations, path, "POST", pathItem.getPost(), entityOps);
              addOp(operations, path, "PATCH", pathItem.getPatch(), entityOps);
              addOp(operations, path, "PUT", pathItem.getPut(), entityOps);
              addOp(operations, path, "DELETE", pathItem.getDelete(), entityOps);
            });

    for (OperationEntry entry : operations) {
      generateContextClass(pkg, outDir, entry);
    }
    generateRegistry(pkg, outDir, operations);
    getLogger().lifecycle("Generated {} operation context classes in {}", operations.size(), outDir);
  }

  private void addOp(
      List<OperationEntry> ops,
      String path,
      String method,
      Operation operation,
      Map<String, Map<String, String>> entityOps) {
    if (operation == null) {
      return;
    }
    String operationId =
        operation.getOperationId() != null
            ? operation.getOperationId()
            : (method.toLowerCase(Locale.ROOT) + path.replace("/", "_").replace("{", "").replace("}", ""));
    String xService = EntitySchemaSupport.resolveService(operation, entityOps);
    String entitySchema = extension(operation, "x-entity-schema");
    String operationKey = extension(operation, "x-operation");
    List<String> allowedAtomic = new ArrayList<>();
    if (operation.getExtensions() != null
        && operation.getExtensions().get("x-atomic-allowed-operations") instanceof List<?> list) {
      for (Object o : list) {
        allowedAtomic.add(o.toString());
      }
    }
    Set<String> queryParams = new TreeSet<>();
    if (operation.getParameters() != null) {
      for (Parameter p : operation.getParameters()) {
        if ("query".equals(p.getIn()) && p.getName() != null) {
          queryParams.add(sanitizeParamName(p.getName()));
        }
      }
    }
    ops.add(
        new OperationEntry(
            operationId,
            method,
            path,
            xService,
            allowedAtomic,
            queryParams,
            pathParamNames(path),
            entitySchema,
            operationKey));
  }

  private String extension(Operation operation, String key) {
    if (operation.getExtensions() == null || operation.getExtensions().get(key) == null) {
      return null;
    }
    return operation.getExtensions().get(key).toString();
  }

  private List<String> pathParamNames(String path) {
    List<String> names = new ArrayList<>();
    int i = 0;
    while ((i = path.indexOf('{', i)) >= 0) {
      int end = path.indexOf('}', i);
      if (end > i) {
        names.add(path.substring(i + 1, end));
      }
      i = end + 1;
    }
    return names;
  }

  private String sanitizeParamName(String name) {
    return name.replace("[", "_").replace("]", "").replace("-", "_");
  }

  private String toClassName(String operationId) {
    StringBuilder sb = new StringBuilder();
    for (String part : operationId.split("[._-]")) {
      if (part.isEmpty()) continue;
      sb.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        sb.append(part.substring(1));
      }
    }
    return sb + "Context";
  }

  private void generateContextClass(String pkg, File outDir, OperationEntry entry)
      throws IOException {
    ClassName className = ClassName.get(pkg, toClassName(entry.operationId()));
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Generated context for $L $L", entry.method(), entry.path());

    for (String pathParam : entry.pathParams()) {
      builder.addField(String.class, pathParam, Modifier.PRIVATE);
    }
    for (String qp : entry.queryParams()) {
      builder.addField(String.class, qp, Modifier.PRIVATE);
    }

    MethodSpec.Builder ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
    for (String pathParam : entry.pathParams()) {
      ctor.addParameter(String.class, pathParam);
      ctor.addStatement("this.$L = $L", pathParam, pathParam);
    }
    for (String qp : entry.queryParams()) {
      ctor.addParameter(String.class, qp, Modifier.FINAL);
      ctor.addStatement("this.$L = $L", qp, qp);
    }
    builder.addMethod(ctor.build());

    for (String pathParam : entry.pathParams()) {
      builder.addMethod(
          MethodSpec.methodBuilder("get" + capitalize(pathParam))
              .addModifiers(Modifier.PUBLIC)
              .returns(String.class)
              .addStatement("return $L", pathParam)
              .build());
    }
    for (String qp : entry.queryParams()) {
      builder.addMethod(
          MethodSpec.methodBuilder("get" + capitalize(qp))
              .addModifiers(Modifier.PUBLIC)
              .returns(String.class)
              .addStatement("return $L", qp)
              .build());
    }

    JavaFile.builder(pkg, builder.build()).build().writeTo(outDir);
  }

  private void generateRegistry(String pkg, File outDir, List<OperationEntry> operations)
      throws IOException {
    ClassName descriptor = ClassName.get(pkg, "OperationDescriptor");
    ClassName registry = ClassName.get(pkg, "JsonApiOperationRegistry");

    TypeSpec descriptorType =
        TypeSpec.classBuilder("OperationDescriptor")
            .addModifiers(Modifier.PUBLIC)
            .addField(String.class, "operationId", Modifier.PUBLIC, Modifier.FINAL)
            .addField(String.class, "method", Modifier.PUBLIC, Modifier.FINAL)
            .addField(String.class, "pathTemplate", Modifier.PUBLIC, Modifier.FINAL)
            .addField(String.class, "service", Modifier.PUBLIC, Modifier.FINAL)
            .addField(String.class, "entitySchema", Modifier.PUBLIC, Modifier.FINAL)
            .addField(String.class, "operationKey", Modifier.PUBLIC, Modifier.FINAL)
            .addField(List.class, "allowedAtomicOperations", Modifier.PUBLIC, Modifier.FINAL)
            .addField(boolean.class, "atomic", Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(String.class, "operationId")
                    .addParameter(String.class, "method")
                    .addParameter(String.class, "pathTemplate")
                    .addParameter(String.class, "service")
                    .addParameter(String.class, "entitySchema")
                    .addParameter(String.class, "operationKey")
                    .addParameter(List.class, "allowedAtomicOperations")
                    .addParameter(boolean.class, "atomic")
                    .addStatement("this.operationId = operationId")
                    .addStatement("this.method = method")
                    .addStatement("this.pathTemplate = pathTemplate")
                    .addStatement("this.service = service")
                    .addStatement("this.entitySchema = entitySchema")
                    .addStatement("this.operationKey = operationKey")
                    .addStatement("this.allowedAtomicOperations = allowedAtomicOperations")
                    .addStatement("this.atomic = atomic")
                    .build())
            .build();

    JavaFile.builder(pkg, descriptorType).build().writeTo(outDir);

    MethodSpec.Builder staticInit = MethodSpec.methodBuilder("buildRegistry").addModifiers(Modifier.PRIVATE, Modifier.STATIC);
    staticInit.returns(
        ParameterizedTypeName.get(
            ClassName.get(Map.class), ClassName.get(String.class), descriptor));
    staticInit.addStatement("$T map = new $T<>()", LinkedHashMap.class, LinkedHashMap.class);

    for (OperationEntry entry : operations) {
      staticInit.addStatement(
          "map.put($S, new $T($S, $S, $S, $S, $S, $S, $L, $L))",
          entry.method() + " " + entry.path(),
          descriptor,
          entry.operationId(),
          entry.method(),
          entry.path(),
          entry.xService() != null ? entry.xService() : "",
          entry.entitySchema() != null ? entry.entitySchema() : "",
          entry.operationKey() != null ? entry.operationKey() : "",
          toJavaPoetList(entry.allowedAtomic()),
          !entry.allowedAtomic().isEmpty());
    }
    staticInit.addStatement("return map");

    TypeSpec registryType =
        TypeSpec.classBuilder("JsonApiOperationRegistry")
            .addModifiers(Modifier.PUBLIC)
            .addField(
                ParameterizedTypeName.get(
                    ClassName.get(Map.class), ClassName.get(String.class), descriptor),
                "operations",
                Modifier.PRIVATE,
                Modifier.FINAL,
                Modifier.STATIC)
            .addStaticBlock(
                com.squareup.javapoet.CodeBlock.builder()
                    .addStatement("operations = buildRegistry()")
                    .build())
            .addMethod(staticInit.build())
            .addMethod(
                MethodSpec.methodBuilder("find")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(String.class, "method")
                    .addParameter(String.class, "path")
                    .returns(descriptor)
                    .addStatement("return operations.get(method + \" \" + path)")
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("findByTemplate")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(String.class, "method")
                    .addParameter(String.class, "pathTemplate")
                    .returns(descriptor)
                    .addStatement("return operations.get(method + \" \" + pathTemplate)")
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("all")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(
                        ParameterizedTypeName.get(
                            ClassName.get(Map.class), ClassName.get(String.class), descriptor))
                    .addStatement("return operations")
                    .build())
            .build();

    JavaFile.builder(pkg, registryType).build().writeTo(outDir);
  }

  private String toJavaPoetList(List<String> items) {
    if (items.isEmpty()) {
      return "java.util.List.of()";
    }
    StringBuilder sb = new StringBuilder("java.util.List.of(");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
    }
    sb.append(")");
    return sb.toString();
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private record OperationEntry(
      String operationId,
      String method,
      String path,
      String xService,
      List<String> allowedAtomic,
      Set<String> queryParams,
      List<String> pathParams,
      String entitySchema,
      String operationKey) {}
}
