package com.fan.lazyday.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public final class PostgresTestDatabaseSupport {

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 5432;
    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "postgres";
    public static final String OPTIONS = "?useUnicode=true&&stringtype=unspecified&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowMultiQueries=true&allowPublicKeyRetrieval=true";

    private PostgresTestDatabaseSupport() {
    }

    public static String randomDatabaseName(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    public static void createDatabase(String dbName) {
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection connection = DriverManager.getConnection(adminJdbcUrl(), USERNAME, PASSWORD);
                 Statement statement = connection.createStatement()) {
                boolean exists;
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'")) {
                    exists = resultSet.next();
                }
                if (!exists) {
                    statement.execute("CREATE DATABASE " + dbName);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create PostgreSQL test database: " + dbName, e);
        }
    }

    public static String jdbcUrl(String dbName) {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + dbName + OPTIONS;
    }

    public static String r2dbcUrl(String dbName) {
        return "r2dbc:postgresql://" + HOST + ":" + PORT + "/" + dbName + OPTIONS;
    }

    private static String adminJdbcUrl() {
        return jdbcUrl("postgres");
    }
}
