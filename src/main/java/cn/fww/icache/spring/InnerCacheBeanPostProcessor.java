package cn.fww.icache.spring;

import cn.fww.icache.annotation.InnerCacheable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 找出InnerCacheable注解的方法，并开放出去。
 * @author: Wen
 * @Date: create in 2017/11/29 17:53
 */
@Component
public class InnerCacheBeanPostProcessor implements BeanPostProcessor {

    private Map<Method, Object> annotationBean = new HashMap<>();

    private Map<Method, String> annotationBeanName = new HashMap<>();

    @Autowired
    private SpringExt springExt;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(bean.getClass());
        if (methods != null) {
            for (Method method : methods) {
                InnerCacheable innerCacheable = method.getAnnotation(InnerCacheable.class);
                if (innerCacheable != null) {
                    System.err.println("找到注入InnerCache的方法了" + method.getName() + bean.getClass().getName());
                    annotationBean.put(method, bean);
                    annotationBeanName.put(method, beanName);
                }
            }
        }
        return bean;
    }

    public Map<Method, Object> getAnnotationBean() {
        return annotationBean;
    }

    public Map<Method, String> getAnnotationBeanName() {
        return annotationBeanName;
    }
}
