package com.opsonapi.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public class OpsonApiPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    OpsonApiExtension extension =
        project.getExtensions().create("opsonapi", OpsonApiExtension.class);
    extension
        .getSpecFile()
        .convention(
            project
                .getLayout()
                .getProjectDirectory()
                .file("src/main/resources/openapi/openapi.yaml"));
    extension.getGeneratedPackage().convention("com.opsonapi.generated");
    extension.getFailOnWarnings().convention(true);
    extension
        .getControllerSourceDirs()
        .convention(
            java.util.List.of(
                project.getLayout().getProjectDirectory().dir("src/main/java").getAsFile()
                    .getAbsolutePath()));
    extension
        .getControllerPathsOutput()
        .convention(
            project
                .getLayout()
                .getProjectDirectory()
                .file("build/generated/openapi/controller-paths.yaml"));

    ValidateOpsonApiSpecTask validateTask =
        project
            .getTasks()
            .register("validateOpenApi", ValidateOpsonApiSpecTask.class, task -> {
              task.getSpecFile().set(extension.getSpecFile());
              task.getFailOnWarnings().set(extension.getFailOnWarnings());
            })
            .get();

    GenerateWireSchemasTask wireTask =
        project
            .getTasks()
            .register("generateWireSchemas", GenerateWireSchemasTask.class, task -> {
              task.dependsOn(validateTask);
              task.getSpecFile().set(extension.getSpecFile());
              task.getOutputDirectory()
                  .set(project.getLayout().getBuildDirectory().dir("generated/resources/openapi"));
            })
            .get();

    ConvertOpsonApiToJsonTask convertTask =
        project
            .getTasks()
            .register("convertOpenApiToJson", ConvertOpsonApiToJsonTask.class, task -> {
              task.dependsOn(wireTask);
              task.getSpecFile().set(extension.getSpecFile());
              task.getFailOnWarnings().set(extension.getFailOnWarnings());
              task.getWireSchemasDirectory()
                  .set(project.getLayout().getBuildDirectory().dir("generated/resources/openapi"));
              task.getOutputJson()
                  .set(
                      project
                          .getLayout()
                          .getBuildDirectory()
                          .file("generated/resources/openapi/openapi/openapi.json"));
            })
            .get();

    project
        .getTasks()
        .register("generateOpenApiFromControllers", GenerateOpsonApiFromControllersTask.class, task -> {
          task.getControllerSourceDirs().set(extension.getControllerSourceDirs());
          task.getOutputYaml().set(extension.getControllerPathsOutput());
        });

    project
        .getTasks()
        .register("generateJsonApiSupport", GenerateOpsonApiSupportTask.class, task -> {
          task.dependsOn(convertTask);
          task.getInputJson().set(convertTask.getOutputJson());
          task.getSpecFile().set(extension.getSpecFile());
          task.getGeneratedPackage().set(extension.getGeneratedPackage());
          task.getOutputDirectory()
              .set(project.getLayout().getBuildDirectory().dir("generated/sources/opsonapi"));
        });

    project
        .getPlugins()
        .withType(
            JavaPlugin.class,
            javaPlugin -> {
              JavaPluginExtension javaExtension =
                  project.getExtensions().getByType(JavaPluginExtension.class);
              SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
              main.getJava()
                  .srcDir(
                      project.getLayout().getBuildDirectory().dir("generated/sources/opsonapi"));
              main.getResources()
                  .srcDir(
                      project.getLayout().getBuildDirectory().dir("generated/resources/openapi"));
              project
                  .getTasks()
                  .named("compileJava", compileJava -> compileJava.dependsOn("generateJsonApiSupport"));
            });
  }
}