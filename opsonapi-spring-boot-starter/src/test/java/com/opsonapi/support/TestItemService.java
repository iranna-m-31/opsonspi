package com.opsonapi.support;

import com.opsonapi.context.ServiceContext;
import com.opsonapi.model.OpsonApiResponseEntity;
import com.opsonapi.testmodel.Item;
import java.util.List;
import org.springframework.stereotype.Service;

@Service("itemService")
public class TestItemService {

  public OpsonApiResponseEntity<Item> findAll(ServiceContext context, Item entity) {
    Item item = new Item();
    item.setId("1");
    item.setName("Test Item");
    return OpsonApiResponseEntity.ofMany(List.of(item));
  }

  public OpsonApiResponseEntity<Item> create(ServiceContext context, Item entity) {
    if (entity.getId() == null) {
      entity.setId("new-1");
    }
    return OpsonApiResponseEntity.created(entity);
  }
}
