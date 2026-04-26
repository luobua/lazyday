package com.fan.lazyday.infrastructure.config.db;


import com.fan.lazyday.infrastructure.config.db.convert.JsonConverter;
import com.fan.lazyday.infrastructure.config.db.convert.UUIDConverter;
import io.r2dbc.spi.ConnectionFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.reactive.TransactionalOperator;

@EnableR2dbcAuditing
@EnableR2dbcRepositories
@EnableTransactionManagement
public class R2dbcConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public CustomR2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        return R2dbcConfiguration.CustomR2dbcCustomConversions.of(dialect);
    }

    @Bean
    public ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager reactiveTransactionManager) {
        return TransactionalOperator.create(reactiveTransactionManager);
    }

    public static class CustomR2dbcCustomConversions extends R2dbcCustomConversions {
        public final List<Object> CUSTOM_CONVERTERS = new ArrayList();
        private static final Collection<?> DEFAULT_CONVERTERS = Arrays.asList(new JsonConverter(), new UUIDConverter());

        public CustomR2dbcCustomConversions(CustomConversions.StoreConversions storeConversions, Collection<?> converters) {
            super(storeConversions, converters);
            this.CUSTOM_CONVERTERS.clear();
            this.CUSTOM_CONVERTERS.addAll(converters);
        }

        public static CustomR2dbcCustomConversions of(R2dbcDialect dialect, Collection<?> converters) {
            List<Object> storeConverters = new ArrayList(dialect.getConverters());
            storeConverters.addAll(R2dbcCustomConversions.STORE_CONVERTERS);
            CustomConversions.StoreConversions storeConversions = StoreConversions.of(dialect.getSimpleTypeHolder(), storeConverters);
            Object[] converterObjects = new Object[converters.size() + DEFAULT_CONVERTERS.size()];
            AtomicInteger index = new AtomicInteger(0);
            converters.forEach((action) -> converterObjects[index.getAndIncrement()] = action);
            DEFAULT_CONVERTERS.forEach((action) -> converterObjects[index.getAndIncrement()] = action);
            return new CustomR2dbcCustomConversions(storeConversions, Arrays.asList(converterObjects));
        }

        public static CustomR2dbcCustomConversions of(R2dbcDialect dialect, Object... converters) {
            return of(dialect, Arrays.asList(converters));
        }

        public static CustomR2dbcCustomConversions of(R2dbcDialect dialect) {
            return of(dialect, DEFAULT_CONVERTERS);
        }
    }
}
