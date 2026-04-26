package com.fan.lazyday.infrastructure.helper;

import com.fan.lazyday.infrastructure.context.SpringContext;
import org.springframework.context.MessageSource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.util.Lazy;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * <p>描述: [类型描述] </p>
 * <p>创建时间: 2024/9/25 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/25 16:31 fan 创建
 */
public class LazyBeanHelper {
    public static final Lazy<R2dbcEntityTemplate> R2DBC_TEMPLATE_LAZY = SpringContext.getLazyBean(R2dbcEntityTemplate.class);
    public static final Lazy<TransactionalOperator> TRANSACTIONAL_OPERATOR_LAZY = SpringContext.getLazyBean(TransactionalOperator.class);
    public static final Lazy<MessageSource> MESSAGE_SOURCE_LAZY = SpringContext.getLazyBean(MessageSource.class);
}
