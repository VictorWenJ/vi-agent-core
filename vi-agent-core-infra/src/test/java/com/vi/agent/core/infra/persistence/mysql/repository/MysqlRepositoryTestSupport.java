package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * MySQL repository 单元测试辅助方法。
 */
final class MysqlRepositoryTestSupport {

    private MysqlRepositoryTestSupport() {
    }

    /** 初始化 MyBatis-Plus entity 元数据，避免 lambda wrapper 在纯单测中缺少表信息。 */
    static void initTableInfoIfAbsent(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
            entityClass
        );
    }

    /** 通过反射设置 repository 私有字段。 */
    static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
