package com._apparel.ecomm_inventory_movement.service;

import com._apparel.ecomm_inventory_movement.entity.InventoryLi;
import com._apparel.ecomm_inventory_movement.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository repository;

    public List<InventoryLi> getAllInventory() {
        return repository.findAll();
    }

    public List<InventoryLi> findByStyle(String style) {
        return repository.findByStyle(style);
    }
}
