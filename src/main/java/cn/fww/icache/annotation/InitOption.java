package cn.fww.icache.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Description: 初始化相关的一些注解
 * @author: Wen
 * @Date: create in 2017/12/1 12:14
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface InitOption {

    String[] initParam() default {};
}
