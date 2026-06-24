package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateOpenApiFromControllersTaskTest {

  @TempDir Path tempDir;

  @Test
  void parsesGetMappingOnSpecAnchor() throws Exception {
    Path javaRoot = tempDir.resolve("src/main/java");
    Path specDir = javaRoot.resolve("com/example/spec");
    Files.createDirectories(specDir);
    Files.writeString(
        specDir.resolve("ItemsSpecAnchor.java"),
        """
        package com.example.spec;
        import org.springframework.web.bind.annotation.GetMapping;
        import org.springframework.web.bind.annotation.RequestMapping;
        @RequestMapping("/api")
        public class ItemsSpecAnchor {
          @GetMapping("items")
          public void list() {}
        }
        """);

    var project = ProjectBuilder.builder().build();
    GenerateOpenApiFromControllersTask task =
        project.getTasks().create("gen", GenerateOpenApiFromControllersTask.class);
    task.getControllerSourceDirs().set(List.of(javaRoot.toString()));
    Path output = tempDir.resolve("controller-paths.yaml");
    task.getOutputYaml().set(output.toFile());
    task.generate();

    String yaml = Files.readString(output);
    assertTrue(yaml.contains("/api/items"));
    assertTrue(yaml.contains("get"));
  }

  @Test
  void parsesClassLevelRequestMappingWithMethod() throws Exception {
    Path javaRoot = tempDir.resolve("src/main/java");
    Path specDir = javaRoot.resolve("com/example/spec");
    Files.createDirectories(specDir);
    Files.writeString(
        specDir.resolve("CategoriesSpecAnchor.java"),
        """
        package com.example.spec;
        import org.springframework.web.bind.annotation.RequestMapping;
        import org.springframework.web.bind.annotation.RequestMethod;
        @RequestMapping("/api/categories")
        public class CategoriesSpecAnchor {
          @RequestMapping(value = "{id}", method = RequestMethod.GET)
          public void byId() {}
        }
        """);

    var project = ProjectBuilder.builder().build();
    GenerateOpenApiFromControllersTask task =
        project.getTasks().create("gen", GenerateOpenApiFromControllersTask.class);
    task.getControllerSourceDirs().set(List.of(javaRoot.toString()));
    Path output = tempDir.resolve("controller-paths.yaml");
    task.getOutputYaml().set(output.toFile());
    task.generate();

    String yaml = Files.readString(output);
    assertTrue(yaml.contains("/api/categories/{id}"));
  }

  @Test
  void ignoresNonSpecAnchorFiles() throws Exception {
    Path javaRoot = tempDir.resolve("src/main/java");
    Path pkg = javaRoot.resolve("com/example/web");
    Files.createDirectories(pkg);
    Files.writeString(
        pkg.resolve("ItemsController.java"),
        """
        package com.example.web;
        import org.springframework.web.bind.annotation.GetMapping;
        public class ItemsController {
          @GetMapping("/hidden")
          public void list() {}
        }
        """);

    var project = ProjectBuilder.builder().build();
    GenerateOpenApiFromControllersTask task =
        project.getTasks().create("gen", GenerateOpenApiFromControllersTask.class);
    task.getControllerSourceDirs().set(List.of(javaRoot.toString()));
    Path output = tempDir.resolve("controller-paths.yaml");
    task.getOutputYaml().set(output.toFile());
    task.generate();

    String yaml = Files.readString(output);
    assertTrue(!yaml.contains("/hidden"));
  }
}
