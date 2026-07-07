package com.opsonapi.atomic;

/**
 * One resolved atomic operation: full operation id (e.g. {@code categories.addMembers}) and mapped
 * domain entity. Modeled after zeq {@code AtomicDTO}; dispatch uses OpenAPI {@code x-service} /
 * {@code x-atomic-operation-services} instead of a hardcoded service map.
 */
public record OpsonApiAtomicDTO(String operationId, Object entity) {}