package com.fops.application.item;

import com.fops.application.inventory.InventoryService;
import com.fops.domain.enums.OrderStatus;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemDemand;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceDemandTest {

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
    void asksForTheOpenStatusesOnlyAndMapsDemandByItem() {
        when(orderRepository.sumRemainingQuantityByItem(List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED)))
                .thenReturn(List.of(demand(1L, 7L), demand(3L, 120L)));

        assertThat(itemService.findOutstandingDemandByItem()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(1L, 7L, 3L, 120L));
        verify(orderRepository).sumRemainingQuantityByItem(List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FULFILLED));
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void noOpenOrdersMeansAnEmptyMap() {
        when(orderRepository.sumRemainingQuantityByItem(anyList())).thenReturn(List.of());

        assertThat(itemService.findOutstandingDemandByItem()).isEmpty();
    }

    private static ItemDemand demand(Long itemId, Long demand) {
        return new ItemDemand() {
            @Override
            public Long getItemId() {
                return itemId;
            }

            @Override
            public Long getDemand() {
                return demand;
            }
        };
    }
}
