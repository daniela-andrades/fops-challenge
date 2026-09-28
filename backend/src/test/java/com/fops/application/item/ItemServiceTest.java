package com.fops.application.item;

import com.fops.application.inventory.InventoryService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.model.Item;
import com.fops.infrastructure.persistence.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ItemService itemService;

    @Test
    void normalizesSkuAndBooksInitialStockAsIncomingMovement() {
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 5L);
            return item;
        });

        Item item = itemService.createItem(" Laptop ", " lap-001 ", 25);

        assertThat(item.getName()).isEqualTo("Laptop");
        assertThat(item.getSku()).isEqualTo("LAP-001");
        verify(inventoryService).registerIncomingInventory(5L, 25, "Initial stock");
    }

    @Test
    void createsItemWithoutMovementWhenThereIsNoInitialStock() {
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.createItem("Laptop", "LAP-001", 0);
        itemService.createItem("Mouse", "MOU-001", null);

        verifyNoInteractions(inventoryService);
    }

    @Test
    void rejectsDuplicateSku() {
        when(itemRepository.existsBySkuIgnoreCase("LAP-001")).thenReturn(true);

        assertThatThrownBy(() -> itemService.createItem("Laptop", "lap-001", 0))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsNegativeInitialStockAndMissingFields() {
        assertThatThrownBy(() -> itemService.createItem("Laptop", "LAP-001", -1)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> itemService.createItem(" ", "LAP-001", 0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> itemService.createItem("Laptop", null, 0)).isInstanceOf(BusinessRuleException.class);
        verify(itemRepository, never()).save(any());
    }
}
