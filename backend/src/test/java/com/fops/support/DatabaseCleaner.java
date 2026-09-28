package com.fops.support;

import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties every table between integration tests, respecting foreign keys, so each test starts from a clean state.
 */
@TestComponent
public class DatabaseCleaner {

    private final EntityManager entityManager;

    public DatabaseCleaner(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void clean() {
        entityManager.createQuery("delete from OrderNotification").executeUpdate();
        entityManager.createQuery("update InventoryMovement m set m.sourceMovement = null").executeUpdate();
        entityManager.createQuery("delete from InventoryMovement").executeUpdate();
        entityManager.createQuery("delete from Order").executeUpdate();
        entityManager.createQuery("delete from Item").executeUpdate();
        entityManager.createQuery("delete from User").executeUpdate();
    }
}
