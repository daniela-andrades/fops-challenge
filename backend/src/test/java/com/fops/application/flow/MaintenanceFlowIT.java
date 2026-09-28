package com.fops.application.flow;

import com.fops.application.inventory.InventoryService;
import com.fops.application.item.ItemService;
import com.fops.application.user.UserService;
import com.fops.domain.enums.MovementType;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceInUseException;
import com.fops.domain.model.InventoryMovement;
import com.fops.domain.model.Item;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.InventoryMovementRepository;
import com.fops.infrastructure.persistence.ItemRepository;
import com.fops.infrastructure.persistence.UserRepository;
import com.fops.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Updates and deletions of users, items and movements through the real services: master data can be
 * corrected, but nothing that other records or the stock ledger depend on can disappear.
 */
class MaintenanceFlowIT extends IntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;

    @Test
    void userCanBeRenamedKeepingItsOwnEmail() {
        User user = fixtures.user();

        User updated = userService.updateUser(user.getId(), "Renamed", user.getEmail().toUpperCase());

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void userCannotTakeAnotherUsersEmail() {
        User first = fixtures.user();
        User second = fixtures.user();

        assertThatThrownBy(() -> userService.updateUser(second.getId(), "Second", first.getEmail()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void userWithoutOrdersIsDeletedButOneWithOrdersIsKept() {
        User idle = fixtures.user();
        User buyer = fixtures.user();
        fixtures.order(buyer, fixtures.item(5), 1);

        userService.deleteUser(idle.getId());

        assertThat(userRepository.existsById(idle.getId())).isFalse();
        assertThatThrownBy(() -> userService.deleteUser(buyer.getId())).isInstanceOf(ResourceInUseException.class);
        assertThat(userRepository.existsById(buyer.getId())).isTrue();
    }

    @Test
    void itemEditKeepsItsStockAndOnlyUnusedItemsCanBeDeleted() {
        Item stocked = fixtures.item(8);
        Item unused = fixtures.item(0);

        Item renamed = itemService.updateItem(stocked.getId(), "Renamed", "new-sku-" + stocked.getId());

        assertThat(renamed.getSku()).isEqualTo("NEW-SKU-" + stocked.getId());
        assertThat(itemRepository.findById(stocked.getId()).orElseThrow().getStockOnHand()).isEqualTo(8);
        assertThatThrownBy(() -> itemService.deleteItem(stocked.getId())).isInstanceOf(ResourceInUseException.class);

        itemService.deleteItem(unused.getId());
        assertThat(itemRepository.existsById(unused.getId())).isFalse();
    }

    @Test
    void wrongIncomingMovementIsDeletedAndTheLedgerStaysExact() {
        Item item = fixtures.item(5);
        InventoryMovement mistake = fixtures.incoming(item, 20);

        inventoryService.deleteMovement(mistake.getId());

        assertThat(movementRepository.existsById(mistake.getId())).isFalse();
        assertThat(stockOf(item)).isEqualTo(5).isEqualTo(ledgerOf(item));
    }

    @Test
    void incomingMovementThatFedAnOrderCannotBeDeleted() {
        Item item = fixtures.item(0);
        fixtures.order(fixtures.user(), item, 2);
        InventoryMovement delivery = fixtures.incoming(item, 10);

        assertThatThrownBy(() -> inventoryService.deleteMovement(delivery.getId())).isInstanceOf(ResourceInUseException.class);
        assertThat(stockOf(item)).isEqualTo(8).isEqualTo(ledgerOf(item));
    }

    @Test
    void incomingMovementWhoseUnitsWereLaterConsumedCannotBeDeleted() {
        Item item = fixtures.item(0);
        InventoryMovement delivery = fixtures.incoming(item, 10);
        fixtures.order(fixtures.user(), item, 7);

        assertThatThrownBy(() -> inventoryService.deleteMovement(delivery.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only 3 of its 10 units are still in stock");
        assertThat(stockOf(item)).isEqualTo(3).isEqualTo(ledgerOf(item));
    }

    @Test
    void allocationsAreNeverDeletedDirectly() {
        Item item = fixtures.item(4);
        var order = fixtures.order(fixtures.user(), item, 4);
        InventoryMovement allocation = movementRepository.findByOrderIdChronological(order.getId()).get(0);

        assertThatThrownBy(() -> inventoryService.deleteMovement(allocation.getId())).isInstanceOf(ResourceInUseException.class);
    }

    @Test
    void movementReasonIsEditableWithoutTouchingTheLedger() {
        Item item = fixtures.item(0);
        InventoryMovement delivery = fixtures.incoming(item, 6);

        inventoryService.updateMovementReason(delivery.getId(), "Supplier B, invoice 1234");

        InventoryMovement reloaded = inventoryService.findMovement(delivery.getId());
        assertThat(reloaded.getReason()).isEqualTo("Supplier B, invoice 1234");
        assertThat(reloaded.getQuantity()).isEqualTo(6);
        assertThat(stockOf(item)).isEqualTo(6);
    }

    private int stockOf(Item item) {
        return itemRepository.findById(item.getId()).orElseThrow().getStockOnHand();
    }

    private int ledgerOf(Item item) {
        return movementRepository.findByItemIdChronological(item.getId()).stream()
                .mapToInt(m -> m.getMovementType() == MovementType.IN ? m.getQuantity() : -m.getQuantity())
                .sum();
    }
}
