package com.vi.agent.core.runtime.skill;

import java.util.Optional;

/**
 * 技能注册表接口（Phase 1 仅预留）。
 */
public interface SkillRegistry {

    /**
     * 根据意图查找技能。
     *
     * @param intent 意图
     * @return 技能描述
     */
    Optional<String> findByIntent(String intent);
}
