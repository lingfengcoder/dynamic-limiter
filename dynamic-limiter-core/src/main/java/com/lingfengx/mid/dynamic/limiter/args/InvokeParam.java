package com.lingfengx.mid.dynamic.limiter.args;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.lang.reflect.Method;

@Setter
@Getter
@ToString
@Accessors(chain = true)
public class InvokeParam {

    private Class<?> clazz;

    private Method method;

    private Object[] args;

    private Object target;


}
