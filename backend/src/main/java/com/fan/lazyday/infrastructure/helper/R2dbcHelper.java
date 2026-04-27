package com.fan.lazyday.infrastructure.helper;

import com.fan.lazyday.infrastructure.context.SpringContext;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.*;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.StatementMapper;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.lang.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fan.lazyday.infrastructure.helper.ReactiveHelper.queuedBatchExec;


/**
 * @author chenbin
 */
@Slf4j
public final class R2dbcHelper {
    private final static String THROWABLE_UNIQUE_CONSTRAINT = "duplicate key value violates unique constraint";

    private final static Map<Class<?>, TableInfo<?>> TABLE_INFO_CACHE = new ConcurrentHashMap<>();

    private final static Function<?, ?> IDENTITY = (Function<Object, Object>) o -> o;

    @FunctionalInterface
    public interface FieldFn<T, R> extends Function<T, R>, Serializable {
    }

    private static final Map<FieldFn<?, ?>, String> FIELD_FN_CACHE = new ConcurrentHashMap<>();

    private R2dbcHelper() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    public static <T> Flux<T> find(Class<T> cls, Criteria criteria) {
        ReactiveDataAccessStrategy dataAccessStrategy = Holder.r2dbcTemplate.getDataAccessStrategy();
        StatementMapper statementMapper = dataAccessStrategy.getStatementMapper();
        StatementMapper.SelectSpec spec = statementMapper.createSelect(dataAccessStrategy.getTableName(cls))
                .withProjection("*")
                .withCriteria(criteria);

        PreparedOperation<?> operation = statementMapper.getMappedObject(spec);
        BiFunction<Row, RowMetadata, T> rowMapper = dataAccessStrategy.getRowMapper(cls);

        return Holder.r2dbcTemplate.getDatabaseClient().sql(operation)
                .map(rowMapper)
                .all();
    }

    public static void ignoreNullProperty(Set<String> keys, OutboundRow row) {
        Iterator<Map.Entry<SqlIdentifier, Parameter>> iterator = row.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SqlIdentifier, Parameter> entry = iterator.next();
            SqlIdentifier key = entry.getKey();
            if (keys.contains(key.getReference())) {
                Parameter parameter = entry.getValue();
                if (!parameter.hasValue()) {
                    iterator.remove();
                }
            }
        }
    }

    public static Query compose(Criteria... criteria) {
        return compose(null, criteria);
    }

    public static Query compose(Pageable pageable, Criteria... criteria) {
        Criteria c = Criteria.from(criteria);
        Query query = Query.query(c);
        if (pageable != null) {
            query = query.with(pageable);
        }
        return query;
    }

    public static boolean isUniqueException(Throwable e) {
        return e.getMessage().contains(THROWABLE_UNIQUE_CONSTRAINT);
    }

    public static Update update(String column, Object value) {
        return Update.update(column, value);
    }

    public static <T> Update update(FieldFn<T, ?> column, Object value) {
        return Update.update(toFieldName(column), value);
    }

    public static <T> Update update(Map<FieldFn<T, ?>, Object> map) {
        Update update = null;
        for (Map.Entry<FieldFn<T, ?>, Object> entry : map.entrySet()) {
            String column = toFieldName(entry.getKey());
            Object value = entry.getValue();

            if (update == null) {
                update = Update.update(column, value);
            } else {
                update = update.set(column, value);
            }
        }
        return update;
    }

    @SuppressWarnings("unchecked")
    public static Mono<Long> update(Object po) {
        MappingContext<? extends RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> mappingContext = Holder.r2dbcTemplate.getConverter().getMappingContext();
        OutboundRow outboundRow = Holder.r2dbcTemplate.getDataAccessStrategy().getOutboundRow(po);

        Class<?> poClass = ClassUtils.getUserClass(po);
        RelationalPersistentEntity<?> persistentEntity = mappingContext.getRequiredPersistentEntity(poClass);

        List<Criteria> criteriaList = new ArrayList<>();
        Map<SqlIdentifier, Object> assignments = new LinkedHashMap<>();
        for (RelationalPersistentProperty property : persistentEntity) {
            SqlIdentifier columnName = property.getColumnName();
            Parameter parameter = outboundRow.get(columnName);

            if (property.isIdProperty()) {
                Objects.requireNonNull(parameter.getValue());
                criteriaList.add(Criteria.where(columnName.getReference()).is(parameter.getValue()));
            } else {
                if (parameter.getValue() != null) {
                    assignments.put(columnName, parameter.getValue());
                }
            }
        }
        if (criteriaList.isEmpty()) {
            throw new IllegalStateException("找不到主键约束");
        }
        Query query = Query.query(Criteria.from(criteriaList));
        Update update = Update.from(assignments);

        return Holder.r2dbcTemplate.update(poClass)
                .matching(query)
                .apply(update);
    }

    @SuppressWarnings("unchecked")
    public static <P> Mono<Boolean> exists(P include, P exclude, Class<P> poClass) {
        if (include == null && exclude == null) {
            return Mono.just(false);
        }

        MappingContext<? extends RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> mappingContext = Holder.r2dbcTemplate.getConverter().getMappingContext();
        RelationalPersistentEntity<?> persistentEntity = mappingContext.getRequiredPersistentEntity(poClass);

        List<Criteria> criteriaList = new ArrayList<>();
        if (include != null) {
            collectCriteria(include, persistentEntity, true, criteriaList);
        }
        if (exclude != null) {
            collectCriteria(exclude, persistentEntity, false, criteriaList);
        }
        Query query = Query.query(Criteria.from(criteriaList));
        return Holder.r2dbcTemplate.count(query, poClass).map(v -> v > 0);
    }

    private static <P> void collectCriteria(P po, RelationalPersistentEntity<?> persistentEntity, boolean include, List<Criteria> collect) {
        OutboundRow outboundRow = Holder.r2dbcTemplate.getDataAccessStrategy().getOutboundRow(po);
        for (RelationalPersistentProperty property : persistentEntity) {
            SqlIdentifier columnName = property.getColumnName();
            Parameter parameter = outboundRow.get(columnName);
            Object value = parameter.getValue();

            if (value != null) {
                Criteria criteria;
                if (include) {
                    if (value instanceof Collection<?>) {
                        criteria = Criteria.where(columnName.getReference()).in(value);
                    } else {
                        criteria = Criteria.where(columnName.getReference()).is(value);
                    }
                } else {
                    if (value instanceof Collection<?>) {
                        criteria = Criteria.where(columnName.getReference()).notIn(value);
                    } else {
                        criteria = Criteria.where(columnName.getReference()).not(value);
                    }
                }
                collect.add(criteria);
            }
        }
    }

    public static <E, P> Mono<Long> insertAll(Collection<E> entities, Class<P> poClass, Function<E, P> converter) {
        if (CollectionUtils.isEmpty(entities)) {
            return Mono.just(0L);
        }
        return Mono.defer(() -> {
            List<P> list = entities.stream()
                    .map(converter)
                    .collect(Collectors.toList());
            return batchInsert(list, poClass);
        });
    }

    public static <P> Mono<Long> insertAll(Collection<P> poCol, Class<P> poClass) {
        if (CollectionUtils.isEmpty(poCol)) {
            return Mono.just(0L);
        }
        return batchInsert(poCol, poClass);
    }

    public static <E, P> Mono<E> findOne(Criteria criteria, Class<P> poClass, Function<P, E> mapper) {
        Query query = Query.query(criteria);
        return Holder.r2dbcTemplate.selectOne(query, poClass)
                .map(mapper);
    }

    public static <E, P> Mono<E> findOne(List<Criteria> criteria, Class<P> poClass, Function<P, E> mapper) {
        Query query = Query.query(Criteria.from(criteria));
        return Holder.r2dbcTemplate.selectOne(query, poClass)
                .map(mapper);
    }

    public static <E, P> Flux<E> findAll(Criteria criteria, Class<P> poClass, Function<P, E> converter) {
        Query query = Query.query(criteria);
        return Holder.r2dbcTemplate.select(query, poClass)
                .map(converter);
    }

    public static <E, P> Flux<E> findAll(Criteria criteria, Sort sort, Class<P> poClass, Function<P, E> mapper) {
        Query query = Query.query(criteria).sort(sort);
        return Holder.r2dbcTemplate.select(query, poClass)
                .map(mapper);
    }

    public static <E, P> Flux<E> findAll(List<Criteria> criteria, Sort sort, Class<P> poClass, Function<P, E> mapper) {
        Query query = Query.query(Criteria.from(criteria)).sort(sort);
        return Holder.r2dbcTemplate.select(query, poClass)
                .map(mapper);
    }

    public static <E, P> Flux<E> findAll(List<Criteria> criteria, Class<P> poClass, Function<P, E> mapper) {
        Query query = Query.query(Criteria.from(criteria));
        return Holder.r2dbcTemplate.select(query, poClass)
                .map(mapper);
    }

    public static <E, P> Flux<E> findAll(Query query, Class<P> poClass, Function<P, E> mapper) {
        return Holder.r2dbcTemplate.select(query, poClass)
                .map(mapper);
    }

    public static <E, P> Mono<Page<E>> findPage(List<Criteria> criteriaList, Pageable pageable, Class<P> poClass, Function<P, E> mapper) {
        pageable = Optional.ofNullable(pageable).orElse(Pageable.unpaged());
        Query query = Query.query(Criteria.from(criteriaList)).with(pageable);
        return findPage(query, poClass, mapper);
    }

    public static <E, P> Mono<Page<E>> findPage(Query query, Class<P> poClass, Function<P, E> mapper) {
        return from(poClass)
                .matching(query)
                .mapTo(mapper)
                .execute(Holder.r2dbcTemplate);
    }

    public static <ID, P> Flux<ID> findExists(Collection<ID> ids, Class<ID> idClass, Class<P> poClass) {
        if (CollectionUtils.isEmpty(ids)) {
            return Flux.empty();
        }

        Criteria criteria = Criteria.where("id").in(ids);

        ReactiveDataAccessStrategy dataAccessStrategy = Holder.r2dbcTemplate.getDataAccessStrategy();
        StatementMapper statementMapper = dataAccessStrategy.getStatementMapper();

        StatementMapper.SelectSpec spec = statementMapper.createSelect(dataAccessStrategy.getTableName(poClass))
                .withProjection("id")
                .withCriteria(criteria);

        PreparedOperation<?> operation = statementMapper.getMappedObject(spec);
        return Holder.r2dbcTemplate.getDatabaseClient().sql(operation)
                .map(r -> Objects.requireNonNull(r.get(0, idClass)))
                .all();
    }


    public static <P> Mono<Long> update(Criteria criteria, Update update, Class<P> poCls) {
        return Holder.r2dbcTemplate.update(poCls)
                .matching(Query.query(criteria))
                .apply(update);
    }

    public static <P> Mono<Long> update(Query query, Update update, Class<P> poCls) {
        return Holder.r2dbcTemplate.update(poCls)
                .matching(query)
                .apply(update);
    }

    public static Criteria like(String search, String... columns) {
        Criteria criteria = Criteria.empty();

        if (StringUtils.hasText(search) && columns.length > 0) {
            StringJoiner joiner = new StringJoiner("%", "%", "%");

            Arrays.stream(search.split("[ |+]]")).filter(StringUtils::hasText).forEach(joiner::add);

            if (joiner.length() > 0) {
                String cond = joiner.toString();

                for (String column : columns) {
                    criteria = criteria.or(column).like(cond);
                }
            }
        }

        return criteria;
    }

    @SafeVarargs
    public static <T> Criteria like(String search, FieldFn<T, ?>... columns) {
        String[] fields = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            fields[i] = toFieldName(columns[i]);
        }
        return like(search, fields);
    }

    public static Criteria isNotTrue(String column) {
        return where(column).isNull().or(where(column).isFalse());
    }

    public static <T> Criteria isNotTrue(FieldFn<T, ?> column) {
        return isNotTrue(toFieldName(column));
    }

    public static <T> QuerySpec<T> from(Class<T> domainType) {
        return new SelectSpecImpl<>(domainType, domainType, Query.empty(), null);
    }

    public static Criteria.CriteriaStep where(String column) {
        return Criteria.where(column);
    }

    public static <T> Criteria.CriteriaStep where(FieldFn<T, ?> fieldFn) {
        return Criteria.where(toFieldName(fieldFn));
    }

    public static <T> Sort.Order asc(FieldFn<T, ?> fieldFn) {
        return Sort.Order.asc(toFieldName(fieldFn));
    }

    public static <T> Sort.Order desc(FieldFn<T, ?> fieldFn) {
        return Sort.Order.desc(toFieldName(fieldFn));
    }

    public static <T> Mono<Long> batchInsert(Collection<T> collection, Class<T> cls) {
        return batchInsert(collection, cls, 50);
    }

    public static <T> Mono<Long> batchInsert(Collection<T> collection, Class<T> cls, int batchSize) {
        if (collection == null || collection.isEmpty()) {
            return Mono.just(0L);
        }
        return queuedBatchExec(collection, batchSize, subList -> batchInsertImpl(subList, cls))
                .reduce(Long::sum)
                .defaultIfEmpty(0L);
    }

    public static <T> Mono<Long> batchInsertImpl(Collection<T> collection, Class<T> cls) {
        if (collection == null || collection.isEmpty()) {
            return Mono.just(0L);
        }
        final List<T> rowList = new ArrayList<>(collection);

        return Mono.defer(() -> {
            DatabaseClient client = SpringContext.getBean(DatabaseClient.class);
            TableInfo<T> tableInfo = getTableInfo(cls);

            StringJoiner columns = new StringJoiner(",");
            StringJoiner allParameters = new StringJoiner(",");
            Map<String, Object> parameterMap = new LinkedHashMap<>();

            Instant now = Instant.now();

            int rowLen = rowList.size();
            for (int i = 0; i < rowLen; i++) {
                T e = rowList.get(i);
                String rowKey = "r" + i;

                StringJoiner parameters = new StringJoiner(",", "(", ")");
                List<ColumnInfo> columnList = tableInfo.columns;
                int colLen = columnList.size();
                for (int j = 0; j < colLen; j++) {
                    ColumnInfo column = columnList.get(j);
                    if (i == 0) {
                        columns.add(column.getQuotedName());
                    }

                    String cellKey = rowKey + "_c" + j;
                    parameters.add(":" + cellKey);

                    Method getter = column.property.getReadMethod();
                    Object value = null;
                    if (getter != null) {
                        try {
                            getter.setAccessible(true);
                            value = getter.invoke(e);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    if (value == null) {
                        if (column.isTimestamp()) {
                            parameterMap.put(cellKey, now);
                        } else {
                            parameterMap.put(cellKey, column.property.getPropertyType());
                        }
                    } else {
                        if (value.getClass().isEnum()) {
                            value = value.toString();
                        }
                        parameterMap.put(cellKey, value);
                    }
                }
                allParameters.add(parameters.toString());
            }

            String sql = String.format("INSERT INTO %s (%s) VALUES %s", tableInfo.tableName, columns, allParameters);
            GenericExecuteSpec spec = client.sql(sql);
            for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Class<?>) {
                    spec = spec.bindNull(entry.getKey(), (Class<?>) value);
                } else {
                    spec = spec.bind(entry.getKey(), value);
                }
            }
            return spec.fetch()
                    .rowsUpdated();
        });
    }

    @SuppressWarnings("unchecked")
    public static <T> TableInfo<T> getTableInfo(Class<T> cls) {
        return (TableInfo<T>) TABLE_INFO_CACHE.computeIfAbsent(cls, c -> {
            String tableName = getTableName(cls);
            List<ColumnInfo> columnList = new ArrayList<>();
            PropertyDescriptor idProperty = null;

            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(cls, Object.class);

                for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                    Field field = ReflectionUtils.findField(cls, pd.getName());
                    if (field != null) {
                        Transient tran = field.getAnnotation(Transient.class);
                        if(tran == null){
                            ColumnInfo columnInfo = getColumnInfo(pd, field);
                            columnList.add(columnInfo);
                            if (idProperty == null && columnInfo.isId()) {
                                idProperty = pd;
                            }
                        }
                    }
                }
            } catch (IntrospectionException e) {
                log.error("", e);
            }
            return new TableInfo<>(cls, tableName, idProperty, columnList);
        });
    }

    private static <T> String getTableName(Class<T> cls) {
        Table annotationTable = AnnotationUtils.findAnnotation(cls, Table.class);
        return annotationTable != null ? annotationTable.value() : cls.getSimpleName();
    }

    private static ColumnInfo getColumnInfo(PropertyDescriptor pd, Field field) {
        boolean isId = false;
        boolean insertable = true;
        boolean updatable = true;

        String name = pd.getName();
        {
            Id id = field.getAnnotation(Id.class);
            if (id != null) {
                isId = true;
            } /*else {
                javax.persistence.Id id2 = field.getAnnotation(javax.persistence.Id.class);
                isId = id2 != null;
            }*/
        }
        {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                name = column.value();
            } /*else {
                javax.persistence.Column column2 = field.getAnnotation(javax.persistence.Column.class);
                if (column2 != null) {
                    insertable = column2.insertable();
                    updatable = column2.updatable();
                    name = column2.name();
                }
            }*/
        }
        boolean isTimestamps = field.isAnnotationPresent(CreatedDate.class) || field.isAnnotationPresent(LastModifiedDate.class);

        return new ColumnInfo(isId, insertable, updatable, isTimestamps, name, pd);
    }

    public static <T> String toFieldName(FieldFn<T, ?> fieldFn) {
        String fieldName = FIELD_FN_CACHE.get(fieldFn);
        if (fieldName != null) {
            return fieldName;
        }

        Exception ex = null;
        try {
            SerializedLambda sLambda = ReflectHelper.invokeDeclaredMethod(fieldFn, "writeReplace");

            // 使用 SerializedLambda 公共 API 获取 capturingClass，避免 Java 21 模块系统限制
            String capturingClassName = sLambda.getCapturingClass().replace('/', '.');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> capturingClass = classLoader.loadClass(capturingClassName);

            Class<?> implClass = classLoader.loadClass(sLambda.getImplClass().replaceAll("/", "."));

            String methodName = sLambda.getImplMethodName();

            if (methodName.startsWith("get") && methodName.length() > 3) {
                fieldName = StringUtils.uncapitalize(methodName.substring(3));
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                fieldName = StringUtils.uncapitalize(methodName.substring(2));
            }

            if (fieldName != null) {
                Field field = ReflectionUtils.findField(implClass, fieldName);
                if (field != null) {
                    Column annotation = AnnotationUtils.getAnnotation(field, Column.class);
                    if (annotation != null) {
                        fieldName = annotation.value();
                    }
                }

                FIELD_FN_CACHE.put(fieldFn, fieldName);
                return fieldName;
            }
        } catch (ReflectiveOperationException e) {
            ex = e;
        }
        throw new IllegalStateException("无法获取字段名", ex);
    }

    @SuppressWarnings("unchecked")
    private static <T, R> Function<T, R> identity() {
        return (Function<T, R>) IDENTITY;
    }

    public static class TableInfo<T> {
        @Getter
        private final Class<T> cls;
        @Getter
        private final String tableName;
        @Getter
        private final PropertyDescriptor idProperty;
        @Getter
        private final List<ColumnInfo> columns;
        @Getter
        private final ColumnInfo idColumn;

        public TableInfo(Class<T> cls, String tableName, PropertyDescriptor idProperty, List<ColumnInfo> columns) {
            this.cls = cls;
            this.tableName = tableName;
            this.idProperty = idProperty;
            this.columns = columns != null ? Collections.unmodifiableList(columns) : Collections.emptyList();

            ColumnInfo idColumn = null;
            for (ColumnInfo column : this.columns) {
                if (column.id) {
                    idColumn = column;
                }
            }
            this.idColumn = idColumn;
        }
    }

    public static class ColumnInfo {
        @Getter
        private final boolean id;
        @Getter
        private final boolean insertable;
        @Getter
        private final boolean updatable;
        @Getter
        private final boolean timestamp;
        @Getter
        private final String name;
        @Getter
        private final PropertyDescriptor property;

        public ColumnInfo(boolean id, boolean insertable, boolean updatable, boolean timestamp, String name,
                          PropertyDescriptor property) {
            this.id = id;
            this.insertable = insertable;
            this.updatable = updatable;
            this.timestamp = timestamp;
            this.name = name;
            this.property = property;
        }

        public Object getPropertyValue(Object obj) {
            Method getter = property.getReadMethod();
            Object value = null;
            if (getter != null) {
                try {
                    getter.setAccessible(true);
                    value = getter.invoke(obj);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return value;
        }

        //TODO 更新Mysql兼容
        public String getQuotedName() {
            return SqlIdentifier.unquoted(name).toString();
            //return SqlIdentifier.quoted(name).toString();
        }
    }

    public interface FromSpec<T> extends QuerySpec<T> {
        <R> QuerySpec<R> as(Class<R> resultType);
    }

    public interface QuerySpec<T> extends TerminatingSpec<T> {
        MappingSpec<T> matching(Query query);

        MappingSpec<T> matching(Criteria criteria);

        MappingSpec<T> matching(Criteria criteria, Pageable pageable);
    }

    public interface MappingSpec<T> extends TerminatingSpec<T> {
        <R> TerminatingSpec<R> mapTo(Function<T, R> mapping);
    }

    public interface TerminatingSpec<T> {
        Mono<Page<T>> execute();

        Mono<Page<T>> execute(R2dbcEntityTemplate template);
    }

    public interface SelectSpec<T> extends FromSpec<T>, QuerySpec<T>, MappingSpec<T> {
    }

    static class SelectSpecImpl<T> implements SelectSpec<T> {
        private final Class<?> domainType;
        private final Class<?> returnType;
        private final Query query;
        @Nullable
        private final Function<?, ?> mapping;

        public SelectSpecImpl(Class<?> domainType, Class<?> returnType, Query query, @Nullable Function<?, ?> mapping) {
            this.domainType = domainType;
            this.returnType = returnType;
            this.query = query;
            this.mapping = mapping;
        }

        @Override
        public <R> QuerySpec<R> as(Class<R> returnType) {
            return new SelectSpecImpl<>(domainType, returnType, query, null);
        }

        @Override
        public MappingSpec<T> matching(Query query) {
            return new SelectSpecImpl<>(domainType, returnType, query, mapping);
        }

        @Override
        public MappingSpec<T> matching(Criteria criteria) {
            return this.matching(Query.query(criteria));
        }

        @Override
        public MappingSpec<T> matching(Criteria criteria, Pageable pageable) {
            if (pageable != null) {
                return this.matching(Query.query(criteria).with(pageable));
            } else {
                return this.matching(Query.query(criteria));
            }
        }

        @Override
        public <R> TerminatingSpec<R> mapTo(Function<T, R> mapping) {
            return new SelectSpecImpl<>(domainType, returnType, query, mapping);
        }

        @Override
        public Mono<Page<T>> execute() {
            return this.execute(SpringContext.getBean(R2dbcEntityTemplate.class));
        }

        @SuppressWarnings("unchecked")
        @Override
        public Mono<Page<T>> execute(R2dbcEntityTemplate template) {
            final boolean unpaged = isUnpaged(query);
            Mono<Long> countMono;
            if (unpaged) {
                countMono = Mono.just(-1L);
            } else {
                Query countQuery = query.getCriteria().map(Query::query).orElse(Query.empty());
                countMono = template.select(domainType).as(returnType).matching(countQuery).count();
            }

            Flux<T> all = (Flux<T>) template.select(domainType).as(returnType).matching(query).all();
            if (mapping != null) {
                Function<Object, T> mapping = (Function<Object, T>) this.mapping;
                all = all.map(mapping);
            }

            return Mono.zip(all.collectList(), countMono).map(tp -> {
                List<T> content = tp.getT1();
                long total = tp.getT2();
                if (unpaged) {
                    total = content.size();
                }

                Pageable pageable = Pageable.unpaged();
                int size = query.getLimit();
                if (size > 0) {
                    int page = (int) (query.getOffset() / size);
                    pageable = PageRequest.of(page, size, query.getSort());
                }

                return new PageImpl<>(content, pageable, total);
            });
        }
    }

    private static boolean isUnpaged(Query query) {
        return query.getLimit() == -1 && query.getOffset() == -1;
    }

    public static <T> Criteria isOrIn(String column, List<T> list) {
        Criteria criteria;
        if (list.size() == 1) {
            criteria = Criteria.where(column)
                    .is(list.get(0));
        } else {
            criteria = Criteria.where(column)
                    .in(list);
        }
        return criteria;
    }

    public static <I, T> Flux<T> findColumnIsOrIn(String column, List<I> list, Class<T> clazz) {
        Query query = Query.query(R2dbcHelper.isOrIn(column, list));
        return Holder.r2dbcTemplate.select(query, clazz);
    }


    public static <T> Flux<T> select(Query query, Class<T> clazz) {
        return Holder.r2dbcTemplate.select(query, clazz);
    }
    public static <I,T> Mono<Long> count(String column,I value,Class<T> clazz) {
        Query query = Query.query(Criteria.where(column).is(value));
        return Holder.r2dbcTemplate.count(query, clazz);
    }

    static class Holder {
        private final static R2dbcEntityTemplate r2dbcTemplate = SpringContext.getBean(R2dbcEntityTemplate.class);
    }
}