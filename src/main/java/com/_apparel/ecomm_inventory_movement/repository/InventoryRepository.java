package com._apparel.ecomm_inventory_movement.repository;

import com._apparel.ecomm_inventory_movement.entity.InventoryLi;
import com._apparel.ecomm_inventory_movement.entity.InventoryLiId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryRepository
        extends JpaRepository<InventoryLi, InventoryLiId> {

    List<InventoryLi> findByStyle(String style);

}
