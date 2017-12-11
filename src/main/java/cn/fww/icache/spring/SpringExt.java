package cn.fww.icache.spring;

import akka.actor.Extension;
import akka.actor.Props;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @Description: spring 和 akka结合的扩张
 * @author: Wen
 * @Date: create in 2017/11/29 12:00
 */
@Component
public class SpringExt implements Extension, ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * Create a Props for the specified actorBeanName using the
     * SpringActorProducer class.
     *
     * @param actorBeanName The name of the actor bean to create Props for
     * @return a Props that will create the named actor bean using Spring
     */
    public Props props(String actorBeanName, Object... args) {
        return Props.create(SpringActorProducer.class, applicationContext, actorBeanName, args);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
