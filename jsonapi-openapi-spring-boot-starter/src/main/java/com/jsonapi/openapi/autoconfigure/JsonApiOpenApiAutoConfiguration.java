package com.jsonapi.openapi.autoconfigure;

import com.jsonapi.openapi.atomic.JsonApiAtomicProcessor;
import com.jsonapi.openapi.context.JsonApiServiceContextFactory;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.support.JsonApiEntityMapper;
import com.jsonapi.openapi.support.JsonApiRequestValidator;
import com.jsonapi.openapi.support.JsonApiResponseAssembler;
import com.jsonapi.openapi.support.ServiceInvoker;
import com.jsonapi.openapi.web.JsonApiDispatcherController;
import com.jsonapi.openapi.web.JsonApiExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "jsonapi.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
  OpenApiSpecRegistry.class,
  JsonApiRequestValidator.class,
  JsonApiServiceContextFactory.class,
  JsonApiEntityMapper.class,
  ServiceInvoker.class,
  JsonApiResponseAssembler.class,
  JsonApiAtomicProcessor.class,
  JsonApiDispatcherController.class,
  JsonApiExceptionHandler.class
})
public class JsonApiOpenApiAutoConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "jsonapi.openapi")
  public JsonApiOpenApiProperties jsonApiOpenApiProperties() {
    return new JsonApiOpenApiProperties();
  }
}
