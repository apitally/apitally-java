package com.apitally.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.apitally.common.dto.ApitallyConsumer;

public class ConsumerRegistry {
    private final Map<String, ApitallyConsumer> consumers;
    private final Set<String> updated;

    public ConsumerRegistry() {
        this.consumers = new HashMap<>();
        this.updated = new HashSet<>();
    }

    public static ApitallyConsumer consumerFromStringOrObject(Object consumer) {
        if (consumer == null) {
            return null;
        }
        if (consumer instanceof String) {
            String identifier = (String) consumer;
            return identifier.trim().isEmpty() ? null : new ApitallyConsumer(identifier);
        } else if (consumer instanceof ApitallyConsumer c) {
            return c.getIdentifier().trim().isEmpty() ? null : c;
        }
        return null;
    }

    public void addOrUpdateConsumer(ApitallyConsumer consumer) {
        if (consumer == null || (consumer.getName() == null && consumer.getGroup() == null)) {
            return;
        }

        ApitallyConsumer existing = consumers.get(consumer.getIdentifier());
        if (existing == null) {
            consumers.put(consumer.getIdentifier(), consumer);
            updated.add(consumer.getIdentifier());
        } else {
            boolean hasChanges = false;
            String newName = existing.getName();
            String newGroup = existing.getGroup();

            if (consumer.getName() != null && !consumer.getName().equals(existing.getName())) {
                newName = consumer.getName();
                hasChanges = true;
            }
            if (consumer.getGroup() != null && !consumer.getGroup().equals(existing.getGroup())) {
                newGroup = consumer.getGroup();
                hasChanges = true;
            }

            if (hasChanges) {
                consumers.put(consumer.getIdentifier(),
                        new ApitallyConsumer(consumer.getIdentifier(), newName, newGroup));
                updated.add(consumer.getIdentifier());
            }
        }
    }

    public List<ApitallyConsumer> getAndResetUpdatedConsumers() {
        List<ApitallyConsumer> data = new ArrayList<>();
        for (String identifier : updated) {
            ApitallyConsumer consumer = consumers.get(identifier);
            if (consumer != null) {
                data.add(consumer);
            }
        }
        updated.clear();
        return data;
    }
}
