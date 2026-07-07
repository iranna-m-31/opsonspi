package com.jsonapi.openapi.support;

import com.jsonapi.openapi.context.ServiceContext;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.testmodel.Item;
import java.util.List;
import org.springframework.stereotype.Service;

@Service("itemService")
public class TestItemService {

  public JsonApiResponseEntity<Item> findAll(ServiceContext context, Item entity) {
    Item item = new Item();
    item.setId("1");
    item.setName("Test Item");
    return JsonApiResponseEntity.ofMany(List.of(item));
  }

  public JsonApiResponseEntity<Item> create(ServiceContext context, Item entity) {
    if (entity.getId() == null) {
      entity.setId("new-1");
    }
    return JsonApiResponseEntity.created(entity);
  }
}
