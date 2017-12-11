package cn.fww.icache.spring.interceptor;

import org.springframework.expression.EvaluationException;

/**
 * @Description: 变量不可用错误
 * @author: Wen
 * @Date: create in 2017/12/6 11:15
 */
public class VariableNotAvailableException extends EvaluationException {

    private final String name;

    public VariableNotAvailableException(String name) {
        super("Variable '" + name + "' is not available");
        this.name = name;
    }


    public String getName() {
        return name;
    }
}