package com.vi.agent.core.model.context.block;

import java.util.Collections;
import java.util.List;

/**
 * WorkingContext 的 block 集合。
 */
public class ContextBlockSet {

    /** 按最终顺序排列的 block 列表。 */
    private final List<ContextBlock> orderedBlocks;

    private ContextBlockSet(List<ContextBlock> orderedBlocks) {
        this.orderedBlocks = orderedBlocks == null || orderedBlocks.isEmpty() ? List.of() : List.copyOf(orderedBlocks);
    }

    public static ContextBlockSet of(List<ContextBlock> orderedBlocks) {
        return new ContextBlockSet(orderedBlocks);
    }

    public List<ContextBlock> getOrderedBlocks() {
        return Collections.unmodifiableList(orderedBlocks);
    }

    public List<ContextBlock> getBlocks() {
        return getOrderedBlocks();
    }

    public boolean isEmpty() {
        return orderedBlocks.isEmpty();
    }

    public int size() {
        return orderedBlocks.size();
    }
}
