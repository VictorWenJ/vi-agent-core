package com.vi.agent.core.infra.persistence.mysql.convertor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class MysqlTimeConvertor {

    private MysqlTimeConvertor() {
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toInstant(ZoneOffset.UTC);
    }
}
