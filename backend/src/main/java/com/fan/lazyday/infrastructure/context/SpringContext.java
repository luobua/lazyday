package com.fan.lazyday.infrastructure.context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.util.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Chenbin
 */
@Component
public class SpringContext implements ApplicationContextAware, BeanFactoryAware {
    private static final AtomicReference<ApplicationContext> ctxRef = new AtomicReference<>();
    private static final AtomicReference<DefaultListableBeanFactory> beanFactoryRef = new AtomicReference<>();

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        if (shouldReplaceContext(ctxRef.get())) {
            synchronized (SpringContext.class) {
                if (shouldReplaceContext(ctxRef.get())) {
                    SpringContext.ctxRef.set(applicationContext);
                }
            }
        }
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof DefaultListableBeanFactory defaultListableBeanFactory) {
            SpringContext.beanFactoryRef.set(defaultListableBeanFactory);
        }
    }

    public static ApplicationContext getApplicationContext() {
        return ctxRef.get();
    }

    public static boolean containsBean(String name) {
        return ctxRef.get().containsBean(name);
    }

    public static <T> T getBean(String name, Class<T> requiredType) {
        return ctxRef.get().getBean(name, requiredType);
    }

    public static <T> T getBean(Class<T> requiredType) {
        return ctxRef.get().getBean(requiredType);
    }

    public static <T> Optional<T> getBeanOptional(Class<T> type) {
        return Optional.ofNullable(ctxRef.get())
                .map(ctx -> ctx.getBeanProvider(type))
                .map(ObjectProvider::getIfAvailable);
    }

    public static <T> List<T> getBeans(Class<T> requiredType) {
        return ctxRef.get()
                .getBeanProvider(requiredType)
                .orderedStream()
                .collect(Collectors.toList());
    }

    public static <T> Lazy<T> getLazyBean(Class<T> requiredType) {
        return Lazy.of(() -> getBean(requiredType));
    }

    public static void registerSingleton(String beanName, Object singletonObject) {
        beanFactoryRef.get().registerSingleton(beanName, singletonObject);
    }

    public static void destroySingleton(String beanName) {
        beanFactoryRef.get().destroySingleton(beanName);
    }

    private static boolean shouldReplaceContext(ApplicationContext currentContext) {
        if (currentContext == null) {
            return true;
        }
        if (currentContext instanceof ConfigurableApplicationContext configurableApplicationContext) {
            return !configurableApplicationContext.isActive();
        }
        return false;
    }
}
