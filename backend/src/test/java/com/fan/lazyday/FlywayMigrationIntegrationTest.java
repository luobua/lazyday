package com.fan.lazyday;

import com.fan.lazyday.support.PostgresTestDatabaseSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTest {

    @Test
    @DisplayName("spring-boot:run 等价启动可完成 V3 迁移并创建关键表与分区")
    void applicationStartup_shouldApplyV3Migration() throws Exception {
        String dbName = PostgresTestDatabaseSupport.randomDatabaseName("lazyday_phase2a_startup");
        PostgresTestDatabaseSupport.createDatabase(dbName);

        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(LazydayApplication.class)
                .run(
                        "--database.host=" + PostgresTestDatabaseSupport.HOST,
                        "--database.port=" + PostgresTestDatabaseSupport.PORT,
                        "--database.dbname=" + dbName,
                        "--database.username=" + PostgresTestDatabaseSupport.USERNAME,
                        "--database.password=" + PostgresTestDatabaseSupport.PASSWORD,
                        "--spring.datasource.url=" + PostgresTestDatabaseSupport.jdbcUrl(dbName),
                        "--spring.datasource.username=" + PostgresTestDatabaseSupport.USERNAME,
                        "--spring.datasource.password=" + PostgresTestDatabaseSupport.PASSWORD,
                        "--spring.r2dbc.url=" + PostgresTestDatabaseSupport.r2dbcUrl(dbName),
                        "--spring.r2dbc.username=" + PostgresTestDatabaseSupport.USERNAME,
                        "--spring.r2dbc.password=" + PostgresTestDatabaseSupport.PASSWORD,
                        "--spring.flyway.url=" + PostgresTestDatabaseSupport.jdbcUrl(dbName),
                        "--spring.flyway.user=" + PostgresTestDatabaseSupport.USERNAME,
                        "--spring.flyway.password=" + PostgresTestDatabaseSupport.PASSWORD,
                        "--lazyday.partition.bootstrap.enabled=false",
                        "--server.port=0"
                )) {

            LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
            LocalDate nextMonth = currentMonth.plusMonths(1);

            assertThat(tableExists(dbName, "t_quota_plan")).isTrue();
            assertThat(tableExists(dbName, "t_tenant_quota")).isTrue();
            assertThat(tableExists(dbName, "t_call_log")).isTrue();
            assertThat(tableExists(dbName, partitionName(currentMonth))).isTrue();
            assertThat(tableExists(dbName, partitionName(nextMonth))).isTrue();
            assertThat(countRows(dbName, "t_tenant_quota")).isEqualTo(countRows(dbName, "t_tenant"));
            assertThat(platformTenantPlanName(dbName)).isEqualTo("Free");
        }
    }

    private boolean tableExists(String dbName, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                PostgresTestDatabaseSupport.jdbcUrl(dbName),
                PostgresTestDatabaseSupport.USERNAME,
                PostgresTestDatabaseSupport.PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='" + tableName + "'")) {
            return resultSet.next();
        }
    }

    private long countRows(String dbName, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                PostgresTestDatabaseSupport.jdbcUrl(dbName),
                PostgresTestDatabaseSupport.USERNAME,
                PostgresTestDatabaseSupport.PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String platformTenantPlanName(String dbName) throws Exception {
        String sql = """
                SELECT plan.name
                FROM t_tenant tenant
                JOIN t_tenant_quota tenant_quota ON tenant_quota.tenant_id = tenant.id
                JOIN t_quota_plan plan ON plan.id = tenant_quota.plan_id
                WHERE tenant.name = 'Lazyday Platform'
                ORDER BY plan.id
                LIMIT 1
                """;
        try (Connection connection = DriverManager.getConnection(
                PostgresTestDatabaseSupport.jdbcUrl(dbName),
                PostgresTestDatabaseSupport.USERNAME,
                PostgresTestDatabaseSupport.PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private String partitionName(LocalDate monthStart) {
        return "t_call_log_" + monthStart.getYear() + "_" + String.format("%02d", monthStart.getMonthValue());
    }
}
