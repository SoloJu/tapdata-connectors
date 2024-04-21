package io.tapdata.connector.empty;

import io.tapdata.connector.mysql.MysqlJdbcContextV2;
import io.tapdata.connector.mysql.config.MysqlConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MysqlJdbcContextTest {
    @Nested
    class GetHikariDataSourceTest {
        @Test
        void testNormal() {
            MysqlConfig config = new MysqlConfig();
            config.setDbType("mysql");
            config.setJdbcDriver("com.mysql.cj.jdbc.Driver");
            config.setHost("localhost");
            config.setPort(3306);
            config.setDatabase("test");
            config.setUser("root");
            config.setPassword("password");
            config.setTimezone("+08:00");
            try (
                    MysqlJdbcContextV2 mysqlJdbcContextV2 = new MysqlJdbcContextV2(config)
            ) {
                Assertions.assertEquals(config.getTimezone(), "GMT+08:00");
            }
        }
    }
}
