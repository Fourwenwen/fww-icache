package cn.fww.icache.spring.init;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import cn.fww.icache.InnerCacheTemplate;
import cn.fww.icache.annotation.InitOption;
import cn.fww.icache.annotation.InnerCacheable;
import cn.fww.icache.spring.InnerCacheBeanPostProcessor;
import cn.fww.icache.spring.SpringExt;
import cn.fww.icache.spring.interceptor.CacheEvaluationContext;
import cn.fww.icache.spring.interceptor.CacheExpressionRootObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @Description: 初始化执行方法
 * @author: Wen
 * @Date: create in 2017/11/24 10:57
 */
@Component
public class InitComponent implements ApplicationListener<ContextRefreshedEvent> {

    private Logger logger = LoggerFactory.getLogger(InitComponent.class);

    @Autowired
    private InnerCacheBeanPostProcessor innerCacheBeanPostProcessor;

    @Autowired
    private InnerCacheTemplate innerCacheTemplate;

    @Autowired
    private SpringExt springExt;

    @Autowired
    private ActorSystem actorSystem;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // root容器启动完成执行
        if (event.getApplicationContext().getParent() == null) {
            Map<Method, Object> annotationBeanMap = innerCacheBeanPostProcessor.getAnnotationBean();
            for (Map.Entry<Method, Object> o : annotationBeanMap.entrySet()) {
                Method method = o.getKey();
                Object beanObj = o.getValue();
                InnerCacheable innerCacheable = method.getAnnotation(InnerCacheable.class);
                if (innerCacheable == null) {
                    continue;
                }
                // 初始化工作
                if (innerCacheable.isInit()) {
                    if (innerCacheable.initOption().length > 0) {
                        for (InitOption item : innerCacheable.initOption()) {
                            String[] params = item.initParam();
                            Object[] objParam = new Object[params.length];
                            Class[] paramsClass = method.getParameterTypes();
                            for (int i = 0; i < params.length; i++) {
                                String classType = paramsClass[i].getName();
                                System.err.println(classType);
                                if ("int".equals(classType)) {
                                    objParam[i] = Integer.valueOf(params[i]);
                                }
                            }
                            putResultToInnerCache(method, beanObj, innerCacheable, objParam);
                        }
                    } else {
                        putResultToInnerCache(method, beanObj, innerCacheable, new Object[]{});
                    }
                }
                // actor注册
                String actorBean = innerCacheable.actorBean();
                if (!"".equals(actorBean.trim())) {
                    try {
                        ActorRef actorRef = actorSystem.actorOf(springExt.props(actorBean), actorBean);
                        logger.info("actor注册{}", actorBean);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 动态调用方法，并把结果存到一级缓存
     *
     * @param method
     * @param beanObj
     * @param innerCacheable
     * @param objParam
     */
    private void putResultToInnerCache(Method method, Object beanObj, InnerCacheable innerCacheable, Object[] objParam) {
        try {
            Object result = method.invoke(beanObj, objParam);
            if (result != null) {
                ExpressionParser parser = new SpelExpressionParser();
                Class targetClass = beanObj.getClass();
                CacheExpressionRootObject rootObject = new CacheExpressionRootObject(
                        method, objParam, beanObj, targetClass);
                CacheEvaluationContext evaluationContext = new CacheEvaluationContext(
                        rootObject, AopUtils.getMostSpecificMethod(method, targetClass), objParam, new DefaultParameterNameDiscoverer());
                String key = parser.parseExpression(innerCacheable.keyName()).getValue(evaluationContext, String.class);
                logger.info("InnerCache 初始化缓存key{}", key);
                innerCacheTemplate.put(key, result, innerCacheable.ttl(), innerCacheable.isVersion());
                innerCacheTemplate.putActorPath(key, innerCacheable.actorBean());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

}
