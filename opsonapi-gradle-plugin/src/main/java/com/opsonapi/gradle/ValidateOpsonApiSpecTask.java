package com.opsonapi.gradle;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Fails the build when the OpenAPI spec cannot be parsed or contains resolver/validation errors. */
@CacheableTask
public abstract class ValidateOpsonApiSpecTask extends DefaultTask {

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getSpecFile();

  @Input
  public abstract Property<Boolean> getFailOnWarnings();

  @TaskAction
  public void validate() {
    File spec = getSpecFile().getAsFile().get();
    boolean failOnWarnings = getFailOnWarnings().getOrElse(true);

    OpsonApiSpecValidator.ValidationResult result =
        OpsonApiSpecValidator.validate(spec, failOnWarnings);

    result
        .messages()
        .forEach(msg -> getLogger().lifecycle("OpenAPI [{}]: {}", spec.getName(), msg));

    if (!result.isValid()) {
      String detail = String.join(System.lineSeparator(), result.errors());
      throw new GradleException(
          "OpenAPI validation failed for "
              + spec.getAbsolutePath()
              + ":"
              + System.lineSeparator()
              + detail);
    }

    getLogger()
        .lifecycle(
            "Validated OpenAPI spec {} ({} paths)",
            spec.getName(),
            result.openAPI().getPaths().size());
  }
}