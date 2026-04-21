package com.vi.agent.core.runtime.event;

import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Runtime 事件发送器工厂。
 */
@Component
public class RuntimeEventSinkFactory {

    @Resource
    private RuntimeEventFactory runtimeEventFactory;

    public RuntimeEventSink create(RuntimeExecutionContext context) {
        return new RuntimeEventSink(context, runtimeEventFactory, context.getEventConsumer());
    }
}

