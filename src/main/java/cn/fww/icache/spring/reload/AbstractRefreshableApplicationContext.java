/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.fww.icache.spring.reload;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base class for {@link ApplicationContext}
 * implementations which are supposed to support multiple calls to {@link #refresh()},
 * creating a new internal bean factory instance every time.
 * Typically (but not necessarily), such a context will be driven by
 * a set of config locations to load bean definitions from.
 * <p>
 * <p>The only method to be implemented by subclasses is {@link #loadBeanDefinitions},
 * which gets invoked on each refresh. A concrete implementation is supposed to load
 * bean definitions into the given
 * {@link DefaultListableBeanFactory},
 * typically delegating to one or more specific bean definition readers.
 * <p>
 * <p><b>Note that there is a similar base class for WebApplicationContexts.</b>
 * {@link org.springframework.web.context.support.AbstractRefreshableWebApplicationContext}
 * provides the same subclassing strategy, but additionally pre-implements
 * all context functionality for web environments. There is also a
 * pre-defined way to receive config locations for a web context.
 * <p>
 * <p>Concrete standalone subclasses of this base class, reading in a
 * specific bean definition format, are {@link ClassPathXmlApplicationContext}
 * and {@link FileSystemXmlApplicationContext}, which both derive from the
 * common {@link AbstractXmlApplicationContext} base class;
 * {@link org.springframework.context.annotation.AnnotationConfigApplicationContext}
 * supports {@code @Configuration}-annotated classes as a source of bean definitions.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see #loadBeanDefinitions
 * @see DefaultListableBeanFactory
 * @see org.springframework.web.context.support.AbstractRefreshableWebApplicationContext
 * @see AbstractXmlApplicationContext
 * @see ClassPathXmlApplicationContext
 * @see FileSystemXmlApplicationContext
 * @see org.springframework.context.annotation.AnnotationConfigApplicationContext
 * @since 1.1.3
 */
public abstract class AbstractRefreshableApplicationContext extends AbstractApplicationContext {

    private Boolean allowBeanDefinitionOverriding;

    private Boolean allowCircularReferences;

    /**
     * Bean factory for this context
     */
    private DefaultListableBeanFactory beanFactory;

    /**
     * Synchronization monitor for the internal BeanFactory
     */
    private final Object beanFactoryMonitor = new Object();

    private RegisterBeanDefinitionListener listener;


    /**
     * Create a new AbstractRefreshableApplicationContext with no parent.
     */
    public AbstractRefreshableApplicationContext() {
        setRegisterBeanDefinitionListener(DefaultRegisterBeanDefinitionListener.getInstance());
    }

    /**
     * Create a new AbstractRefreshableApplicationContext with the given parent context.
     *
     * @param parent the parent context
     */
    public AbstractRefreshableApplicationContext(ApplicationContext parent) {
        super(parent);
    }

    public void setRegisterBeanDefinitionListener(RegisterBeanDefinitionListener listener) {
        this.listener = listener;
    }

    /**
     * Set whether it should be allowed to override bean definitions by registering
     * a different definition with the same name, automatically replacing the former.
     * If not, an exception will be thrown. Default is "true".
     *
     * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
     */
    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
    }

    /**
     * Set whether to allow circular references between beans - and automatically
     * try to resolve them.
     * <p>Default is "true". Turn this off to throw an exception when encountering
     * a circular reference, disallowing them completely.
     *
     * @see DefaultListableBeanFactory#setAllowCircularReferences
     */
    public void setAllowCircularReferences(boolean allowCircularReferences) {
        this.allowCircularReferences = allowCircularReferences;
    }


    /**
     * This implementation performs an actual refresh of this context's underlying
     * bean factory, shutting down the previous bean factory (if any) and
     * initializing a fresh bean factory for the next phase of the context's lifecycle.
     */
    @Override
    protected final void refreshBeanFactory() throws BeansException {
        if (hasBeanFactory()) {
            destroyBeans();
            closeBeanFactory();
        }
        try {
            DefaultListableBeanFactory beanFactory = createBeanFactory();
            if (this.listener != null) {
                //beanFactory.setRegisterBeanDefinitionListener(this.listener);
            }
            beanFactory.setSerializationId(getId());
            customizeBeanFactory(beanFactory);
            loadBeanDefinitions(beanFactory);
            synchronized (this.beanFactoryMonitor) {
                this.beanFactory = beanFactory;
            }
        } catch (IOException ex) {
            throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
        }
    }

    @Override
    protected void cancelRefresh(BeansException ex) {
        synchronized (this.beanFactoryMonitor) {
            if (this.beanFactory != null)
                this.beanFactory.setSerializationId(null);
        }
        super.cancelRefresh(ex);
    }

    @Override
    protected final void closeBeanFactory() {
        synchronized (this.beanFactoryMonitor) {
            this.beanFactory.setSerializationId(null);
            this.beanFactory = null;
        }
    }

    /**
     * Determine whether this context currently holds a bean factory,
     * i.e. has been refreshed at least once and not been closed yet.
     */
    protected final boolean hasBeanFactory() {
        synchronized (this.beanFactoryMonitor) {
            return (this.beanFactory != null);
        }
    }

    @Override
    public final ConfigurableListableBeanFactory getBeanFactory() {
        synchronized (this.beanFactoryMonitor) {
            if (this.beanFactory == null) {
                throw new IllegalStateException("BeanFactory not initialized or already closed - " +
                        "call 'refresh' before accessing beans via the ApplicationContext");
            }
            return this.beanFactory;
        }
    }

    /**
     * Overridden to turn it into a no-op: With AbstractRefreshableApplicationContext,
     * {@link #getBeanFactory()} serves a strong assertion for an active context anyway.
     */
    @Override
    protected void assertBeanFactoryActive() {
    }

    /**
     * Create an internal bean factory for this context.
     * Called for each {@link #refresh()} attempt.
     * <p>The default implementation creates a
     * {@link DefaultListableBeanFactory}
     * with the {@linkplain #getInternalParentBeanFactory() internal bean factory} of this
     * context's parent as parent bean factory. Can be overridden in subclasses,
     * for example to customize DefaultListableBeanFactory's settings.
     *
     * @return the bean factory for this context
     * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
     * @see DefaultListableBeanFactory#setAllowEagerClassLoading
     * @see DefaultListableBeanFactory#setAllowCircularReferences
     * @see DefaultListableBeanFactory#setAllowRawInjectionDespiteWrapping
     */
    protected DefaultListableBeanFactory createBeanFactory() {
        return new DefaultListableBeanFactory(getInternalParentBeanFactory());
    }

    /**
     * Customize the internal bean factory used by this context.
     * Called for each {@link #refresh()} attempt.
     * <p>The default implementation applies this context's
     * {@linkplain #setAllowBeanDefinitionOverriding "allowBeanDefinitionOverriding"}
     * and {@linkplain #setAllowCircularReferences "allowCircularReferences"} settings,
     * if specified. Can be overridden in subclasses to customize any of
     * {@link DefaultListableBeanFactory}'s settings.
     *
     * @param beanFactory the newly created bean factory for this context
     * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding
     * @see DefaultListableBeanFactory#setAllowCircularReferences
     * @see DefaultListableBeanFactory#setAllowRawInjectionDespiteWrapping
     * @see DefaultListableBeanFactory#setAllowEagerClassLoading
     */
    protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
        if (this.allowBeanDefinitionOverriding != null) {
            beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
        }
        if (this.allowCircularReferences != null) {
            beanFactory.setAllowCircularReferences(this.allowCircularReferences);
        }
    }

    /**
     * Load bean definitions into the given bean factory, typically through
     * delegating to one or more bean definition readers.
     *
     * @param beanFactory the bean factory to load bean definitions into
     * @throws BeansException if parsing of the bean definitions failed
     * @throws IOException    if loading of bean definition files failed
     * @see org.springframework.beans.factory.support.PropertiesBeanDefinitionReader
     * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
     */
    protected abstract void loadBeanDefinitions(DefaultListableBeanFactory beanFactory)
            throws BeansException, IOException;

    @Override
    public Object getBean(String name, Class requiredType) throws BeansException {
        checkAndRefreshBean(name);
        return super.getBean(name, requiredType);
    }

    @Override
    public Object getBean(String name, Object[] args) throws BeansException {
        checkAndRefreshBean(name);
        return super.getBean(name, args);
    }

    @Override
    public Object getBean(String name) throws BeansException {
        checkAndRefreshBean(name);
        return super.getBean(name);
    }

    public void checkAndRefreshBean(String name) {
        System.out.println(this.getClass().getCanonicalName() + "check file");
        DefaultRegisterBeanDefinitionListener listener = DefaultRegisterBeanDefinitionListener.getInstance();
        FileDesc fd = listener.getFileDescByBeanName(name);
        File currentFile = new File(fd.getPath());
        if (fd.getTm() != 0 && fd.getTm() != currentFile.lastModified()) {
            //TODO 增加别名处理
            long currentTm = currentFile.lastModified();
            DefaultListableBeanFactory defaultListableBeanFactory = ((DefaultListableBeanFactory) this.getBeanFactory());
            SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
            FileSystemResource isr = (new FileSystemResource(new File(fd.getPath())));
            reader.loadBeanDefinitions(new EncodedResource(isr));
            List propertyPlaceholderConfigurerList = updateBeans(defaultListableBeanFactory, registry);
            updateFileDesc(name, listener, fd, currentTm);
            processBeanFactory(defaultListableBeanFactory, propertyPlaceholderConfigurerList);
            finishBeanFactoryInitialization(defaultListableBeanFactory);
        }
    }

    private List updateBeans(DefaultListableBeanFactory defaultListableBeanFactory,
                             SimpleBeanDefinitionRegistry registry) {
        String[] beanNames = registry.getBeanDefinitionNames();
        List propertyPlaceholderConfigurerList = new ArrayList();
        for (String n : beanNames) {
            try {
                defaultListableBeanFactory.removeBeanDefinition(n);
            } catch (NoSuchBeanDefinitionException e) {
                //ignore
            }
            String className = registry.getBeanDefinition(n).getBeanClassName();
            //TODO BeanPostProcessors
            //http://static.springsource.org/spring/docs/2.5.x/reference/beans.html#beans-factory-extension-bpp
            //http://static.springsource.org/spring/docs/2.5.x/reference/beans.html#context-introduction-ctx-vs-beanfactory
            if (className != null && className.equals("org.springframework.beans.factory.config.PropertyPlaceholderConfigurer")) {
                propertyPlaceholderConfigurerList.add(n);
            }
            defaultListableBeanFactory.registerBeanDefinition(n, registry.getBeanDefinition(n));
        }
        return propertyPlaceholderConfigurerList;
    }

    private void processBeanFactory(DefaultListableBeanFactory defaultListableBeanFactory,
                                    List propertyPlaceholderConfigurerList) {
        for (Iterator it = propertyPlaceholderConfigurerList.listIterator(); it.hasNext(); ) {
            String beanName = it.next().toString();
            PropertyPlaceholderConfigurer configurer = (PropertyPlaceholderConfigurer) getBeanFactory().getBean(beanName);
            configurer.postProcessBeanFactory(defaultListableBeanFactory);
        }
    }

    private void updateFileDesc(String name, DefaultRegisterBeanDefinitionListener listener, FileDesc fd, long currentTm) {
        FileDesc newfd = new FileDesc(fd.getPath(), currentTm);
        listener.updateFileDesc(fd, newfd, name);
    }


}
