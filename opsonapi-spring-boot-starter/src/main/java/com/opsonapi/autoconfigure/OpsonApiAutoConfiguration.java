package com.opsonapi.autoconfigure;

import com.opsonapi.atomic.OpsonApiAtomicProcessor;
import com.opsonapi.context.OpsonApiServiceContextFactory;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.support.OpsonApiEntityMapper;
import com.opsonapi.support.OpsonApiRequestValidator;
import com.opsonapi.support.OpsonApiResponseAssembler;
import com.opsonapi.support.OpsonApiServiceInvoker;
import com.opsonapi.web.OpsonApiDispatcherController;
import com.opsonapi.web.OpsonApiExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for OpsonAPI.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "opsonapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
  OpsonApiSpecRegistry.class,
  OpsonApiRequestValidator.class,
  OpsonApiServiceContextFactory.class,
  OpsonApiEntityMapper.class,
  OpsonApiServiceInvoker.class,
  OpsonApiResponseAssembler.class,
  OpsonApiAtomicProcessor.class,
  OpsonApiDispatcherController.class,
  OpsonApiExceptionHandler.class
})
public class OpsonApiAutoConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "opsonapi")
  public OpsonApiProperties opsonApiProperties() {
    return new OpsonApiProperties();
  }
}