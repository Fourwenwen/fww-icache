package cn.fww.icache.spring.reload;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * @description: 一个监听器接口，很简单，当bean注册的时候程序会调用beanRegistered
 * @author: Wen
 * @date: create in 2018/1/6 15:55
 */
public interface RegisterBeanDefinitionListener {

    public void beanRegistered(String beanName, BeanDefinition beanDefinition);

}
