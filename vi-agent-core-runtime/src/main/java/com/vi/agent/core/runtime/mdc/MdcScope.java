package com.vi.agent.core.runtime.mdc;

import java.util.Map;

/**
 * MDC scope holder.
 */
public class MdcScope implements AutoCloseable {

    private final Map<String, String> previous;

    public MdcScope(Map<String, String> previous) {
        this.previous = previous;
    }

    @Override
    public void close() {
        org.slf4j.MDC.clear();
        if (previous != null) {
            previous.forEach(org.slf4j.MDC::put);
        }
    }
}
