package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.*;

public interface CoffeeCatalogService {
    CoffeeStoreVO currentStore();

    CoffeeMenuVO menu(Long storeId);

    CoffeeAdminContextVO context(Long adminId);

    CoffeeStoreVO adminStore(Long adminId, Long storeId);

    CoffeeStoreVO saveStore(Long adminId, Long storeId, CoffeeStoreSaveDTO dto);

    CoffeeAdminMenuVO adminMenu(Long adminId, Long storeId);

    CoffeeAdminMenuVO.Category saveCategory(
            Long adminId, Long storeId, Long id, CoffeeCategorySaveDTO dto);

    CoffeeAdminProductVO saveProduct(Long adminId, Long storeId, Long id, CoffeeProductSaveDTO dto);
}
