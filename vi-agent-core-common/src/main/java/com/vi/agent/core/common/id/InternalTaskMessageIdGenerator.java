package com.vi.agent.core.common.id;

import java.util.Locale;
import java.util.UUID;

/**
 * internal task prompt message id generator.
 */
public class InternalTaskMessageIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return "itaskmsg-" + UUID.randomUUID();
    }

    public String nextId(String role) {
        return "itaskmsg-" + normalize(role) + "-" + UUID.randomUUID();
    }

    private String normalize(String role) {
        if (role == null || role.isBlank()) {
            return "unknown";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }
}
