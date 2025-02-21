package com.apitally.spring;

import com.apitally.common.dto.Consumer;

public final class ApitallyConsumer extends Consumer {
    public ApitallyConsumer(String identifier) {
        super(identifier);
    }

    public ApitallyConsumer(String identifier, String name, String group) {
        super(identifier, name, group);
    }
}
