package cn.fww.icache.spring.reload;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description:
 * @author: Wen
 * @date: create in 2018/1/6 16:05
 */
public class DefaultRegisterBeanDefinitionListener implements RegisterBeanDefinitionListener {

    private static DefaultRegisterBeanDefinitionListener listener = null;

    private Map<FileDesc, List> fdBeanNameListMap;
    private Map<String, FileDesc> beanNameFdMap;

    private DefaultRegisterBeanDefinitionListener() {
        fdBeanNameListMap = new HashMap<FileDesc, List>();
        beanNameFdMap = new HashMap();
    }

    public static synchronized DefaultRegisterBeanDefinitionListener getInstance() {
        if (listener == null)
            listener = new DefaultRegisterBeanDefinitionListener();
        return listener;
    }

    @Override
    public void beanRegistered(String beanName, BeanDefinition beanDefinition) {
        if (!(beanDefinition instanceof AbstractBeanDefinition)) {
            return;
        }
        AbstractBeanDefinition beanDef = (AbstractBeanDefinition) beanDefinition;
        Resource res = beanDef.getResource();

        String fileName;
        long lastTm;
        try {
            fileName = res.getFile().getAbsolutePath();
            lastTm = res.getFile().lastModified();
        } catch (Throwable e) {
            fileName = ((ClassPathResource) res).getPath();
            lastTm = 0;
        }
        FileDesc fd = new FileDesc(fileName, lastTm);
        List list = fdBeanNameListMap.get(fd);
        if (list == null) {
            list = new ArrayList();
        }
        list.add(beanName);
        fdBeanNameListMap.put(fd, list);
        beanNameFdMap.put(beanName, fd);

    }

    public void updateFileDesc(FileDesc oldfd, FileDesc newfd, String beanName) {
        List list = fdBeanNameListMap.get(oldfd);
        fdBeanNameListMap.remove(oldfd);
        fdBeanNameListMap.put(newfd, list);
        beanNameFdMap.remove(oldfd);
        beanNameFdMap.put(beanName, newfd);
    }

    public FileDesc getFileDescByBeanName(String beanName) {
        return beanNameFdMap.get(beanName);
    }
}