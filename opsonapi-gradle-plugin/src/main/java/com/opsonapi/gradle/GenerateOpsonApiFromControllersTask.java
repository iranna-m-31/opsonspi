package com.opsonapi.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Scans spec-anchor controller source files for Spring MVC mapping annotations and emits a paths
 * YAML fragment. Controllers are the source of truth for API routes.
 */
@CacheableTask
public abstract class GenerateOpsonApiFromControllersTask extends DefaultTask {

  private static final String MAPPING_PKG =
      "(?:org\\.springframework\\.web\\.bind\\.annotation\\.)?";
  private static final Pattern CLASS_REQUEST_MAPPING =
      Pattern.compile(
          "@"
              + MAPPING_PKG
              + "RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']+)");
  private static final Pattern METHOD_MAPPING =
      Pattern.compile(
          "@"
              + MAPPING_PKG
              + "(Get|Post|Patch|Put|Delete)Mapping\\s*\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']+)[\"']");
  private static final Pattern REQUEST_MAPPING_METHOD =
      Pattern.compile(
          "@"
              + MAPPING_PKG
              + "RequestMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']+)[\"'][^)]*method\\s*=\\s*"
              + MAPPING_PKG
              + "RequestMethod\\.(\\w+)",
          Pattern.DOTALL);

  @Input
  public abstract ListProperty<String> getControllerSourceDirs();

  @OutputFile
  public abstract RegularFileProperty getOutputYaml();

  @TaskAction
  public void generate() throws Exception {
    Map<String, Map<String, String>> paths = new LinkedHashMap<>();
    for (String dir : getControllerSourceDirs().get()) {
      Path root = Path.of(dir);
      if (!Files.isDirectory(root)) continue;
      Files.walk(root)
          .filter(p -> p.toString().endsWith("SpecAnchor.java"))
          .forEach(p -> parseController(p, paths));
    }
    File out = getOutputYaml().getAsFile().get();
    out.getParentFile().mkdirs();
    Files.writeString(out.toPath(), renderYaml(paths));
    getLogger().lifecycle("Generated {} controller paths -> {}", paths.size(), out);
  }

  private void parseController(Path file, Map<String, Map<String, String>> paths) {
    try {
      String source = Files.readString(file);
      String classBase = "";
      Matcher classMatcher = CLASS_REQUEST_MAPPING.matcher(source);
      if (classMatcher.find()) {
        classBase = normalizePath(classMatcher.group(1).split(",")[0].trim().replace("\"", ""));
      }

      Matcher methodMatcher = METHOD_MAPPING.matcher(source);
      while (methodMatcher.find()) {
        String http = methodMatcher.group(1).toLowerCase();
        String pathValue = methodMatcher.group(2).split(",")[0].trim().replace("\"", "");
        addPath(paths, joinPath(classBase, pathValue), http);
      }

      Matcher rmMatcher = REQUEST_MAPPING_METHOD.matcher(source);
      while (rmMatcher.find()) {
        String pathValue = rmMatcher.group(1).trim().replace("\"", "");
        String http = rmMatcher.group(2).toLowerCase();
        addPath(paths, joinPath(classBase, pathValue), http);
      }
    } catch (Exception e) {
      throw new GradleException("Failed to parse controller spec-anchor " + file + ": " + e.getMessage(), e);
    }
  }

  private static void addPath(Map<String, Map<String, String>> paths, String path, String http) {
    paths.computeIfAbsent(path, k -> new LinkedHashMap<>()).put(http, http);
  }

  private static String joinPath(String base, String segment) {
    if (segment != null && segment.startsWith("/")) {
      return normalizePath(segment);
    }
    if (base == null || base.isEmpty()) return normalizePath(segment);
    if (segment == null || segment.isEmpty()) return normalizePath(base);
    return normalizePath(base) + normalizePath(segment);
  }

  private static String normalizePath(String p) {
    p = p.trim();
    if (!p.startsWith("/")) p = "/" + p;
    return p.replaceAll("//+", "/");
  }

  private String renderYaml(Map<String, Map<String, String>> paths) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "# Auto-generated from spec-anchor controllers. Merge into openapi.yaml; add x-service and schemas.\n");
    sb.append("paths:\n");
    for (Map.Entry<String, Map<String, String>> path : paths.entrySet()) {
      sb.append("  ").append(path.getKey()).append(":\n");
      for (String method : new TreeSet<>(path.getValue().keySet())) {
        sb.append("    ").append(method).append(":\n");
        sb.append("      summary: ").append(method).append(" ").append(path.getKey()).append("\n");
      }
    }
    return sb.toString();
  }
}