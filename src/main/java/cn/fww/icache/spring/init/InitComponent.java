package cn.fww.icache.spring.init;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import cn.fww.icache.InnerCacheTemplate;
import cn.fww.icache.annotation.InitOption;
import cn.fww.icache.annotation.InnerCacheable;
import cn.fww.icache.spring.InnerCacheBeanPostProcessor;
import cn.fww.icache.spring.SpringExt;
import cn.fww.icache.spring.core.DynamicClassLoader;
import cn.fww.icache.spring.interceptor.CacheEvaluationContext;
import cn.fww.icache.spring.interceptor.CacheExpressionRootObject;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
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
            /*ApplicationContext applicationContext1 = springExt.getApplicationContext();
            ApplicationContext applicationContext = event.getApplicationContext();
            ConfigurableWebApplicationContext context = (XmlWebApplicationContext) applicationContext;
            ConfigurableListableBeanFactory configurableListableBeanFactory = context.getBeanFactory();
            configurableListableBeanFactory.getMergedBeanDefinition("");
            BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) context.getBeanFactory();*/

            ApplicationContext applicationContext = springExt.getApplicationContext();
            ConfigurableApplicationContext context = (ConfigurableApplicationContext) applicationContext;
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;

            Map<Method, Object> annotationBeanMap = innerCacheBeanPostProcessor.getAnnotationBean();
            Map<Method, String> annotationBeanName = innerCacheBeanPostProcessor.getAnnotationBeanName();
            for (Map.Entry<Method, Object> o : annotationBeanMap.entrySet()) {
                Object bean = o.getValue();
                String beanName = annotationBeanName.get(o.getKey());

                DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
                RootBeanDefinition beanDefinition = (RootBeanDefinition) beanFactory.getMergedBeanDefinition(beanName);
                try {
                    CtClass ctClazz = changeBeanClass(bean);
                    byte[] bytes = ctClazz.toBytecode();
                    DynamicClassLoader loader = new DynamicClassLoader(bean.getClass().getClassLoader());
                    Class clazz = loader.loadByte(bytes, ctClazz.getName());
                    beanDefinition.setBeanClass(clazz);
                    beanDefinitionRegistry.removeBeanDefinition(beanName);
                    beanDefinitionRegistry.registerBeanDefinition(beanName, beanDefinition);
                    /*PropertyPlaceholderConfigurer configurer = (PropertyPlaceholderConfigurer) beanFactory.getBean(beanName);
                    configurer.postProcessBeanFactory(defaultListableBeanFactory);*/
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

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

    private CtClass changeBeanClass(Object bean) throws Exception {
        Class clazz = bean.getClass();
        ClassPool classPool = ClassPool.getDefault();
        ClassClassPath classPath = new ClassClassPath(clazz);
        classPool.insertClassPath(classPath);
        System.err.println(clazz.getCanonicalName() + "---" + clazz.getSimpleName());
        CtClass ctClass = classPool.get(clazz.getName().split("\\$\\$")[0]);
        //获取类中的方法
        CtMethod[] cms = ctClass.getDeclaredMethods();
        for (CtMethod cm : cms) {
            MethodInfo methodInfo = cm.getMethodInfo();
            AnnotationsAttribute attribute = (AnnotationsAttribute) methodInfo
                    .getAttribute(AnnotationsAttribute.visibleTag);
            if (attribute != null) {
                Annotation[] annotations = attribute.getAnnotations();
                Annotation annotation = attribute.getAnnotation("cn.mindmedia.jeemind.inner.cache.annotation.InnerCacheable");
                if (annotation != null) {
                    System.err.println(cm + "获取类的注解：" + annotations.length + "获取注解" + annotation.getTypeName());
                    InnerCacheable innerCacheable = (InnerCacheable) cm.getAnnotation(InnerCacheable.class);
                    System.err.println("获取innerCacheable注解：" + innerCacheable);

                    ConstPool constPool = methodInfo.getConstPool();
                    Annotation cacheableAnnotation = new Annotation("org.springframework.cache.annotation.Cacheable", constPool);
                    cacheableAnnotation.addMemberValue("key", new StringMemberValue(innerCacheable.keyName(), constPool));
                    Cacheable cacheable = innerCacheable.CACHEABLE();
                    Class cacheableClazz = cacheable.getClass();
                    Method[] methods = cacheableClazz.getDeclaredMethods();
                    for (Method method : methods) {
                        if (!"key".equalsIgnoreCase(method.getName())) {
                            System.err.println(method.getName() + "----" + method.getReturnType());
                            boolean isIgnore = false;
                            if ("toString".equalsIgnoreCase(method.getName())) {
                                isIgnore = true;
                            } else if ("hashCode".equalsIgnoreCase(method.getName())) {
                                isIgnore = true;
                            }
                            MemberValue memberValue = null;
                            Object methodResult = null;
                            try {
                                methodResult = method.invoke(cacheable);
                            } catch (IllegalArgumentException e) {
                                isIgnore = true;
                            }
                            if (isIgnore) {
                                System.err.println("忽略该方法");
                                continue;
                            }
                            switch (method.getReturnType().toString()) {
                                case "class [Ljava.lang.String;":
                                    String[] sources = (String[]) methodResult;
                                    MemberValue[] sourcesMv = new MemberValue[sources.length];
                                    for (int i = 0; i < sources.length; i++) {
                                        sourcesMv[i] = new StringMemberValue(sources[i], constPool);
                                    }
                                    memberValue = new ArrayMemberValue(constPool);
                                    ((ArrayMemberValue) memberValue).setValue(sourcesMv);
                                    break;
                                case "class java.lang.String":
                                    memberValue = new StringMemberValue((String) methodResult, constPool);
                                    break;
                                case "class java.lang.Class":
                                    memberValue = new ClassMemberValue(((Class) methodResult).getName(), constPool);
                                    break;
                                case "boolean":
                                    memberValue = new BooleanMemberValue((Boolean) methodResult, constPool);
                                    break;
                                case "int":
                                    memberValue = new IntegerMemberValue((Integer) methodResult, constPool);
                                    break;
                                case "byte":
                                    memberValue = new ByteMemberValue((Byte) methodResult, constPool);
                                    break;
                                default:
                            }
                            cacheableAnnotation.addMemberValue(method.getName(), memberValue);
                        }
                    }
                    attribute.addAnnotation(cacheableAnnotation);
                    methodInfo.addAttribute(attribute);
                }
            }
        }
        ctClass.writeFile();
        return ctClass;
    }

}
