package com.fan.lazyday.infrastructure.config.db.postgresql;


import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.*;

public class PostgresqlDatabase {
    private static final Logger log = LoggerFactory.getLogger(PostgresqlDatabase.class);
    private static final String DB_URL = "jdbc:postgresql://{host}:{port}/postgres";
    private static final String QUERY_DATA_NAME_SQL = "SELECT u.datname  FROM pg_catalog.pg_database u where u.datname='{datname}';";
    private static final String CREATE_DATA_BASE = "create database \"{datname}\" ENCODING = 'UTF8';";
    private static final String DRIVER_CLASS_NAME = "org.postgresql.Driver";
    private static final String DB_NAME_COLUMN = "datname";

    private PostgresqlDatabase() {
    }

    public static void createDatabase(Option option) throws ClassNotFoundException, SQLException {
        Class<?> clazz = Class.forName("org.postgresql.Driver");
        log.debug(clazz.getName());

        try (Connection connection = DriverManager.getConnection(UriResolvePlaceholders.expandUriComponent("jdbc:postgresql://{host}:{port}/postgres", StandardCharsets.UTF_8, new Object[]{option.host, option.port}), option.username, option.password)) {
            Statement statement = connection.createStatement();
            Throwable var5 = null;

            try {
                ResultSet result = statement.executeQuery(UriResolvePlaceholders.expandUriComponent("SELECT u.datname  FROM pg_catalog.pg_database u where u.datname='{datname}';", StandardCharsets.UTF_8, new Object[]{option.dbname}));
                Throwable var7 = null;

                try {
                    String dataName;
                    for(dataName = null; result.next(); dataName = result.getString("datname")) {
                    }

                    if (dataName == null) {
                        statement.executeUpdate(UriResolvePlaceholders.expandUriComponent("create database \"{datname}\" ENCODING = 'UTF8';", StandardCharsets.UTF_8, new Object[]{option.dbname}));
                    }
                } catch (Throwable var51) {
                    var7 = var51;
                    throw var51;
                } finally {
                    if (result != null) {
                        if (var7 != null) {
                            try {
                                result.close();
                            } catch (Throwable var50) {
                                var7.addSuppressed(var50);
                            }
                        } else {
                            result.close();
                        }
                    }

                }
            } catch (Throwable var53) {
                var5 = var53;
                throw var53;
            } finally {
                if (statement != null) {
                    if (var5 != null) {
                        try {
                            statement.close();
                        } catch (Throwable var49) {
                            var5.addSuppressed(var49);
                        }
                    } else {
                        statement.close();
                    }
                }

            }
        }

    }

    public static class Option {
        private final String host;
        private final int port;
        private final String dbname;
        private final String username;
        private final String password;

        public Option(@Nullable String host, int port, @Nullable String dbname, @Nullable String username, @Nullable String password) {
            this.host = host;
            this.port = port;
            this.dbname = dbname;
            this.username = username;
            this.password = password;
        }
    }
}