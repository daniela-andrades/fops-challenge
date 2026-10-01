package com.fops.application.item;

import com.fops.application.inventory.InventoryService;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceInUseException;
import com.fops.domain.model.Item;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.fops.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InventoryMovementRepository movementRepository;
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
    void updatesNameAndSkuButNeverTheStock() {
        Item existing = item(3, 12);
        when(itemRepository.findById(3L)).thenReturn(Optional.of(existing));

        Item updated = itemService.updateItem(3L, " Laptop Pro ", " lap-002 ");

        assertThat(updated.getName()).isEqualTo("Laptop Pro");
        assertThat(updated.getSku()).isEqualTo("LAP-002");
        assertThat(updated.getStockOnHand()).isEqualTo(12);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void updateRejectsASkuUsedByAnotherItem() {
        when(itemRepository.findById(3L)).thenReturn(Optional.of(item(3, 0)));
        when(itemRepository.existsBySkuIgnoreCaseAndIdNot("MOU-001", 3L)).thenReturn(true);

        assertThatThrownBy(() -> itemService.updateItem(3L, "Mouse", "mou-001"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deletesAnItemWithoutHistory() {
        Item existing = item(3, 0);
        when(itemRepository.findById(3L)).thenReturn(Optional.of(existing));

        itemService.deleteItem(3L);

        verify(itemRepository).delete(existing);
    }

    @Test
    void refusesToDeleteAnItemWithOrdersOrMovements() {
        when(itemRepository.findById(3L)).thenReturn(Optional.of(item(3, 0)));

        when(orderRepository.existsByItemId(3L)).thenReturn(true);
        assertThatThrownBy(() -> itemService.deleteItem(3L)).isInstanceOf(ResourceInUseException.class);

        when(orderRepository.existsByItemId(3L)).thenReturn(false);
        when(movementRepository.existsByItemId(3L)).thenReturn(true);
        assertThatThrownBy(() -> itemService.deleteItem(3L))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessage("Item SKU-3 has orders or inventory movements and cannot be deleted");

        verify(itemRepository, never()).delete(any());
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
