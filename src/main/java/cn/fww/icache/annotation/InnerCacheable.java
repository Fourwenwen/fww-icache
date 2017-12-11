package cn.fww.icache.annotation;

import java.lang.annotation.*;

/**
 * @Description:
 * @author: Wen
 * @Date: create in 2017/11/29 16:44
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface InnerCacheable {

    String keyName() default "";

    boolean isInit() default false;

    InitOption[] initOption() default {};

    int ttl() default -1;

    boolean isVersion() default false;

    String actorBean() default "";


}
