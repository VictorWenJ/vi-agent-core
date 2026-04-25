package com.vi.agent.core.model.context.block;

import java.util.Collections;
import java.util.List;

/**
 * WorkingContext 的 block 集合。
 */
public class ContextBlockSet {

    /** WorkingContext 中的 block 列表。 */
    private final List<ContextBlock> blocks;

    private ContextBlockSet(List<ContextBlock> blocks) {
        this.blocks = blocks == null || blocks.isEmpty() ? List.of() : List.copyOf(blocks);
    }

    public static ContextBlockSet of(List<ContextBlock> blocks) {
        return new ContextBlockSet(blocks);
    }

    public List<ContextBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int size() {
        return blocks.size();
    }
}
