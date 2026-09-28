package com.fops.application.inventory;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.fops.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @Mock
    private FulfillmentService fulfillmentService;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void addsStockRecordsTheInMovementAndAllocatesIt() {
        Item item = item(1, 2);
        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(inventoryMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMovement movement = inventoryService.registerIncomingInventory(1L, 10, "Supplier A");

        assertThat(item.getStockOnHand()).isEqualTo(12);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.IN);
        assertThat(movement.getQuantity()).isEqualTo(10);
        assertThat(movement.getReason()).isEqualTo("Supplier A");
        verify(fulfillmentService).allocateToOpenOrders(item, movement);
    }

    @Test
    void rejectsInvalidQuantityWithoutLockingTheItem() {
        assertThatThrownBy(() -> inventoryService.registerIncomingInventory(1L, 0, "x")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inventoryService.registerIncomingInventory(1L, null, "x")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(itemRepository);
    }

    @Test
    void failsForUnknownItem() {
        when(itemRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.registerIncomingInventory(9L, 3, "x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Item 9 not found");
    }
}
