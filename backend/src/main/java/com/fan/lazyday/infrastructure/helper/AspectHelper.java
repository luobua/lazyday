package com.fan.lazyday.infrastructure.helper;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.core.KotlinDetector;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;

public class AspectHelper {

    public static <T extends Annotation> Tuple3<String,String,T> getAnnotation(ProceedingJoinPoint joinPoint, Class<T> clazz) throws NoSuchMethodException {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        Class<?> classTarget = joinPoint.getTarget().getClass();
        Class<?>[] par = ((MethodSignature) joinPoint.getSignature()).getParameterTypes();
        Method objMethod = ReflectionUtils.findMethod(classTarget, methodName, par);
        assert objMethod != null;
        T  annotation=objMethod.getAnnotation(clazz);
        if( annotation == null){
            return null;
        }
        return Tuples.of(className,methodName,annotation);
    }

    @Slf4j
    public static class CglibMethodInvocation extends ReflectiveMethodInvocation {

        @Nullable
        private final MethodProxy methodProxy;

        public CglibMethodInvocation(Object proxy, @Nullable Object target, Method method,
                                     Object[] arguments, @Nullable Class<?> targetClass,
                                     List<Object> interceptorsAndDynamicMethodMatchers, MethodProxy methodProxy) {

            super(proxy, target, method, arguments, targetClass, interceptorsAndDynamicMethodMatchers);

            // Only use method proxy for public methods not derived from java.lang.Object
            this.methodProxy = (isMethodProxyCompatible(method) ? methodProxy : null);
        }

        @Override
        @Nullable
        public Object proceed() throws Throwable {
            try {
                return super.proceed();
            }
            catch (RuntimeException ex) {
                throw ex;
            }
            catch (Exception ex) {
                if (ReflectionUtils.declaresException(getMethod(), ex.getClass()) ||
                        KotlinDetector.isKotlinType(getMethod().getDeclaringClass())) {
                    // Propagate original exception if declared on the target method
                    // (with callers expecting it). Always propagate it for Kotlin code
                    // since checked exceptions do not have to be explicitly declared there.
                    throw ex;
                }
                else {
                    // Checked exception thrown in the interceptor but not declared on the
                    // target method signature -> apply an UndeclaredThrowableException,
                    // aligned with standard JDK dynamic proxy behavior.
                    throw new UndeclaredThrowableException(ex);
                }
            }
        }

        /**
         * Gives a marginal performance improvement versus using reflection to
         * invoke the target when invoking public methods.
         */
        @Override
        protected Object invokeJoinpoint() throws Throwable {
            if (this.methodProxy != null) {
                try {
                    return this.methodProxy.invoke(this.target, this.arguments);
                }
                catch (CodeGenerationException ex) {
                    logFastClassGenerationFailure(this.method);
                }
            }
            return super.invokeJoinpoint();
        }

        static boolean isMethodProxyCompatible(Method method) {
            return (Modifier.isPublic(method.getModifiers()) &&
                    method.getDeclaringClass() != Object.class && !AopUtils.isEqualsMethod(method) &&
                    !AopUtils.isHashCodeMethod(method) && !AopUtils.isToStringMethod(method));
        }

        static void logFastClassGenerationFailure(Method method) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to generate CGLIB fast class for method: " + method);
            }
        }
    }
}
