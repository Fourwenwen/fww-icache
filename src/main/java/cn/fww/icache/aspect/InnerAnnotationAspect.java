package cn.fww.icache.aspect;

import cn.fww.icache.InnerCacheTemplate;
import cn.fww.icache.annotation.InnerCacheEvict;
import cn.fww.icache.annotation.InnerCacheable;
import cn.fww.icache.spring.interceptor.CacheEvaluationContext;
import cn.fww.icache.spring.interceptor.CacheExpressionRootObject;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @Description: 根据不同注解实现切面编程
 * @author: Wen
 * @Date: create in 2017/12/5 14:57
 */
@Component
@Aspect
public class InnerAnnotationAspect implements Ordered {

    private Logger logger = LoggerFactory.getLogger(InnerAnnotationAspect.class);

    @Autowired
    private InnerCacheTemplate innerCacheTemplate;

    private ExpressionParser parser = new SpelExpressionParser();

    @Override
    public int getOrder() {
        return 0;
    }

    @Around(value = "@annotation(cn.fww.icache.annotation.InnerCacheable) && @annotation(innerCacheable)")
    public Object around(ProceedingJoinPoint point, InnerCacheable innerCacheable) {
        Object beanObj = point.getTarget();
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        Object[] args = point.getArgs();
        Class targetClass = beanObj.getClass();
        String key = getKey(innerCacheable.keyName(), beanObj, method, args, targetClass);
        logger.info("{}{}先读取内部缓存key={}", targetClass, method.getName(), key);
        Object result = null;
        try {
            result = innerCacheTemplate.get(key);
            if (result == null) {
                result = point.proceed();
                innerCacheTemplate.put(key, result, innerCacheable.ttl(), innerCacheable.isVersion());
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return result;
    }

    @AfterReturning(value = "@annotation(cn.fww.icache.annotation.InnerCacheEvict) && @annotation(innerCacheEvict)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, InnerCacheEvict innerCacheEvict, Object result) {
        Object beanObj = joinPoint.getTarget();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        Class targetClass = beanObj.getClass();
        String key = getKey(innerCacheEvict.keyName(), beanObj, method, args, targetClass);
        innerCacheTemplate.remove(key);
        long version = innerCacheTemplate.updateVersion(key);
        logger.info("{}{}数据要更新了，缓存将要更新，先把缓存的版本更新。key={},updateVersion={}", targetClass, method.getName(), key, version);

    }

    /**
     * 通过spel获取key字符串
     *
     * @param spelKeyName
     * @param beanObj
     * @param method
     * @param args
     * @param targetClass
     * @return
     */
    private String getKey(String spelKeyName, Object beanObj, Method method, Object[] args, Class targetClass) {
        CacheExpressionRootObject rootObject = new CacheExpressionRootObject(
                method, args, beanObj, targetClass);
        CacheEvaluationContext evaluationContext = new CacheEvaluationContext(
                rootObject, AopUtils.getMostSpecificMethod(method, targetClass), args, new DefaultParameterNameDiscoverer());
        return parser.parseExpression(spelKeyName).getValue(evaluationContext, String.class);
    }
}
