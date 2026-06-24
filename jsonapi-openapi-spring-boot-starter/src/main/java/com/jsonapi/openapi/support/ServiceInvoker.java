package com.jsonapi.openapi.support;

import com.jsonapi.openapi.context.ServiceContext;
import com.jsonapi.openapi.exception.JsonApiValidationException;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.registry.OperationDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
/**
 * Invokes {@code x-service} beans using the contract {@code JsonApiResponseEntity
 * method(ServiceContext context, Object entity)}.
 */
public class ServiceInvoker {

  private final ApplicationContext applicationContext;

  public ServiceInvoker(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  public JsonApiResponseEntity<?> invoke(
      String serviceRef, ServiceContext context, Object entity, OperationDescriptor operation)
      throws Exception {
    if (serviceRef == null || serviceRef.isBlank() || !serviceRef.contains(".")) {
      throw new JsonApiValidationException(500, "Configuration Error", "Invalid x-service: " + serviceRef);
    }
    String[] parts = serviceRef.split("\\.", 2);
    String beanName = decapitalize(parts[0]);
    if (!applicationContext.containsBean(beanName)) {
      beanName = parts[0];
    }
    if (!applicationContext.containsBean(beanName)) {
      throw new JsonApiValidationException(
          500,
          "Configuration Error",
          "No Spring bean for x-service: " + parts[0] + " (operation " + operation.operationId() + ")");
    }
    Object service = applicationContext.getBean(beanName);
    String methodName = parts[1];

    Method method = findInvokerMethod(service, beanName, methodName, entity);
    if (method != null) {
      Object result = method.invoke(service, context, entity);
      return wrapResult(result);
    }
    throw new NoSuchMethodException(
        "Expected JsonApiResponseEntity "
            + methodName
            + "(ServiceContext, Object) on "
            + beanName
            + " for operation "
            + operation.operationId());
  }

  private Method findInvokerMethod(
      Object service, String beanName, String methodName, Object entity) {
    List<Class<?>> types = new ArrayList<>();
    Class<?> declaredType = resolveBeanType(beanName);
    if (declaredType != null) {
      types.add(declaredType);
    }
    types.add(service.getClass());
    for (Class<?> iface : service.getClass().getInterfaces()) {
      types.add(iface);
    }
    for (Class<?> type : types) {
      for (Method method : type.getMethods()) {
        if (!method.getName().equals(methodName)) {
          continue;
        }
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 2 || !ServiceContext.class.isAssignableFrom(params[0])) {
          continue;
        }
        if (entity != null
            && !params[1].isInstance(entity)
            && params[1] != Object.class
            && !params[1].isAssignableFrom(entity.getClass())) {
          continue;
        }
        return method;
      }
    }
    return null;
  }

  private Class<?> resolveBeanType(String beanName) {
    if (!(applicationContext.getAutowireCapableBeanFactory()
        instanceof org.springframework.beans.factory.support.BeanDefinitionRegistry registry)) {
      return null;
    }
    BeanDefinition definition = registry.getBeanDefinition(beanName);
    return definition.getResolvableType().resolve();
  }

  private JsonApiResponseEntity<?> wrapResult(Object result) {
    if (result instanceof JsonApiResponseEntity<?> response) {
      return response;
    }
    if (result == null) {
      return JsonApiResponseEntity.noContent();
    }
    return JsonApiResponseEntity.of(result);
  }

  private static String decapitalize(String name) {
    if (name == null || name.isEmpty()) return name;
    if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
