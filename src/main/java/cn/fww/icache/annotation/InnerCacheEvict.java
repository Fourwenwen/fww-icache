package cn.fww.icache.annotation;

import java.lang.annotation.*;

/**
 * @Description: 更新缓存注解
 * @author: Wen
 * @Date: create in 2017/12/6 12:02
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface InnerCacheEvict {

    String keyName();

}
