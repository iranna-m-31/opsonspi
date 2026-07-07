package com.opsonapi.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OpsonAPI.
 */
@ConfigurationProperties(prefix = "opsonapi")
public class OpsonApiProperties {

  /** When false, OpsonAPI autoconfiguration is disabled. */
  private boolean enabled = true;

  /** Classpath or filesystem location of resolved OpenAPI JSON. */
  private String location = "classpath:openapi/openapi.json";

  /** Package containing domain entity classes mapped from wire format. */
  private String entityPackage = "com.example.model";

  /** Ant-style URL patterns handled by the OpsonAPI dispatcher. */
  private List<String> dispatcherPaths = new ArrayList<>(List.of("/api/**"));

  /** Classpath location pattern for entity schema YAML files. */
  private String entitySchemasLocation = "classpath:opsonapi/schemas/";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getEntityPackage() {
    return entityPackage;
  }

  public void setEntityPackage(String entityPackage) {
    this.entityPackage = entityPackage;
  }

  public List<String> getDispatcherPaths() {
    return dispatcherPaths;
  }

  public void setDispatcherPaths(List<String> dispatcherPaths) {
    this.dispatcherPaths = dispatcherPaths;
  }

  /** Used by SpEL in {@link com.opsonapi.web.OpsonApiDispatcherController}. */
  public String[] getDispatcherPathsArray() {
    return dispatcherPaths.toArray(new String[0]);
  }

  public String getEntitySchemasLocation() {
    return entitySchemasLocation;
  }

  public void setEntitySchemasLocation(String entitySchemasLocation) {
    this.entitySchemasLocation = entitySchemasLocation;
  }
}