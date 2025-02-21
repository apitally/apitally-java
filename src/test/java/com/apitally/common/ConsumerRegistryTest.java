package com.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.apitally.common.dto.Consumer;

public class ConsumerRegistryTest {
    private ConsumerRegistry consumerRegistry;

    @BeforeEach
    void setUp() {
        consumerRegistry = new ConsumerRegistry();
    }

    @Test
    void testAddOrUpdateConsumer() {
        Consumer consumer;
        consumer = ConsumerRegistry.consumerFromStringOrObject("test");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumer = new Consumer("test", "Test 1", "Group 1");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumer = new Consumer("test", null, "Group 2");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumer = new Consumer("test", "Test 2", null);
        consumerRegistry.addOrUpdateConsumer(consumer);

        List<Consumer> consumers = consumerRegistry.getAndResetConsumers();
        assertEquals(1, consumers.size());
        assertEquals("test", consumers.get(0).getIdentifier());
        assertEquals("Test 2", consumers.get(0).getName());
        assertEquals("Group 2", consumers.get(0).getGroup());

        consumer = new Consumer("test", "Test 2", "Group 2");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumers = consumerRegistry.getAndResetConsumers();
        assertEquals(0, consumers.size());
    }
}
