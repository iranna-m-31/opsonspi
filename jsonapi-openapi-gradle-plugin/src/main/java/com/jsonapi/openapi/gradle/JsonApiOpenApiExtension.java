package com.jsonapi.openapi.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class JsonApiOpenApiExtension {

  public abstract RegularFileProperty getSpecFile();

  public abstract Property<String> getGeneratedPackage();

  public abstract ListProperty<String> getControllerSourceDirs();

  public abstract RegularFileProperty getControllerPathsOutput();

  /** When true, any parser message fails {@code validateOpenApi} (recommended for CI). */
  public abstract Property<Boolean> getFailOnWarnings();
}
