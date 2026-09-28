package com.fops.application.inventory;

import com.fops.application.fulfillment.FulfillmentService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.ResourceInUseException;
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

import static com.fops.support.TestData.*;
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
    void onlyTheReasonOfAMovementCanChange() {
        Item item = item(1, 10);
        InventoryMovement movement = InventoryMovement.incoming(item, 10, "Typo");
        when(inventoryMovementRepository.findById(5L)).thenReturn(Optional.of(movement));

        inventoryService.updateMovementReason(5L, "Supplier A");

        assertThat(movement.getReason()).isEqualTo("Supplier A");
        assertThat(movement.getQuantity()).isEqualTo(10);
        assertThat(item.getStockOnHand()).isEqualTo(10);
    }

    @Test
    void deletesAnUnallocatedIncomingMovementAndTakesItsStockBack() {
        Item item = item(1, 15);
        InventoryMovement movement = InventoryMovement.incoming(item, 10, "Wrong delivery");
        when(inventoryMovementRepository.findById(5L)).thenReturn(Optional.of(movement));
        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        inventoryService.deleteMovement(5L);

        assertThat(item.getStockOnHand()).isEqualTo(5);
        verify(inventoryMovementRepository).delete(movement);
    }

    @Test
    void refusesToDeleteAnIncomingMovementAlreadyAllocatedToOrders() {
        InventoryMovement movement = InventoryMovement.incoming(item(1, 10), 10, "Delivery");
        when(inventoryMovementRepository.findById(5L)).thenReturn(Optional.of(movement));
        when(inventoryMovementRepository.existsBySourceMovementId(5L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.deleteMovement(5L))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessage("Movement 5 was already allocated to orders and cannot be deleted");
        verify(inventoryMovementRepository, never()).delete(any());
    }

    @Test
    void refusesToDeleteAnIncomingMovementWhoseUnitsAreNoLongerInStock() {
        Item item = item(1, 4);
        InventoryMovement movement = InventoryMovement.incoming(item, 10, "Delivery");
        when(inventoryMovementRepository.findById(5L)).thenReturn(Optional.of(movement));
        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.deleteMovement(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Movement 5 cannot be deleted: only 4 of its 10 units are still in stock");
        assertThat(item.getStockOnHand()).isEqualTo(4);
    }

    @Test
    void refusesToDeleteAnAllocation() {
        Item item = item(1, 0);
        InventoryMovement out = InventoryMovement.allocation(item, 3, order(9, user(1), item, 3), null, true, "Allocated");
        when(inventoryMovementRepository.findById(5L)).thenReturn(Optional.of(out));

        assertThatThrownBy(() -> inventoryService.deleteMovement(5L))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("allocation to order 9");
    }

    @Test
    void failsForUnknownItem() {
        when(itemRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.registerIncomingInventory(9L, 3, "x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Item 9 not found");
    }
}
