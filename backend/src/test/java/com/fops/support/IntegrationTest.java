package com.fops.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for full-context integration tests. Every subclass shares one cached Spring context:
 * H2 database, real services and a mocked SMTP sender. Tables are emptied after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({DatabaseCleaner.class, TestFixtures.class})
public abstract class IntegrationTest {

    @MockBean
    protected JavaMailSender mailSender;

    @Autowired
    protected TestFixtures fixtures;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }
}
