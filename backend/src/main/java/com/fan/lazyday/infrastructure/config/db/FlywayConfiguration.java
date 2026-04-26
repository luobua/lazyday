package com.fan.lazyday.infrastructure.config.db;

import com.fan.lazyday.infrastructure.config.db.postgresql.PostgresqlDatabase;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.sql.SQLException;


@Import({FlywayConfiguration.FlywayDataSourceProperties.class})
public class FlywayConfiguration {
    private final FlywayDataSourceProperties properties;

    @PostConstruct
    public void init() throws ClassNotFoundException, SQLException {
        if (this.properties.init) {
            PostgresqlDatabase.createDatabase(new PostgresqlDatabase.Option(this.properties.host, this.properties.port, this.properties.dbname, this.properties.username, this.properties.password));
        }

    }

    public FlywayConfiguration(FlywayDataSourceProperties properties) {
        this.properties = properties;
    }

    public FlywayDataSourceProperties getProperties() {
        return this.properties;
    }



    @Component
    @ConfigurationProperties(
            prefix = "database"
    )
    public static class FlywayDataSourceProperties {
        private String host;
        private int port;
        private String dbname;
        private String username;
        private String password;
        private boolean init;

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public void setDbname(String dbname) {
            this.dbname = dbname;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public void setInit(boolean init) {
            this.init = init;
        }
    }
}
