package com.jsonapi.openapi.gradle;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Validates OpenAPI specs using {@code io.swagger.parser.v3} before codegen tasks run. */
public final class OpenApiSpecValidator {

  private OpenApiSpecValidator() {}

  public static ValidationResult validate(File specFile, boolean failOnWarnings) {
    if (specFile == null || !specFile.isFile()) {
      throw new IllegalArgumentException("OpenAPI spec file does not exist: " + specFile);
    }

    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    options.setResolveFully(false);

    SwaggerParseResult result =
        new OpenAPIV3Parser().readLocation(specFile.getAbsolutePath(), null, options);

    List<String> messages =
        result.getMessages() != null ? new ArrayList<>(result.getMessages()) : List.of();
    OpenAPI openAPI = result.getOpenAPI();

    List<String> errors = new ArrayList<>();
    if (openAPI == null) {
      errors.add("OpenAPI document could not be parsed from " + specFile.getAbsolutePath());
    } else if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
      errors.add("OpenAPI spec defines no paths: " + specFile.getAbsolutePath());
    }

    for (String message : messages) {
      if (isErrorMessage(message) || failOnWarnings) {
        errors.add(message);
      }
    }

    if (openAPI != null) {
      errors.addAll(EntitySchemaSupport.validateEntityOperations(openAPI, specFile));
    }

    return new ValidationResult(openAPI, messages, errors);
  }

  public static ValidationResult validateWithWireSchemas(
      File specFile, File wireSchemasDir, OpenAPI patchedOpenAPI) {
    List<String> errors = new ArrayList<>();
    errors.addAll(EntitySchemaSupport.validateWireSchemas(specFile, wireSchemasDir, patchedOpenAPI));
    return new ValidationResult(patchedOpenAPI, List.of(), errors);
  }

  private static boolean isErrorMessage(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    String lower = message.toLowerCase(Locale.ROOT);
    return lower.contains("error")
        || lower.contains("failed")
        || lower.contains("could not")
        || lower.contains("unable to")
        || lower.contains("exception")
        || lower.contains("not found")
        || lower.contains("invalid");
  }

  static boolean isResolvableError(String message) {
    return isErrorMessage(message);
  }

  public record ValidationResult(OpenAPI openAPI, List<String> messages, List<String> errors) {
    public boolean isValid() {
      return errors.isEmpty();
    }
  }
}
