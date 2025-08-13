package com.lingfengx.mid.dynamic.limiter.args;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

@Getter
@Setter
@Accessors(chain = true)
public class MethodAndArgs implements Serializable {
    private String clazz;
    private String methodName;
    private boolean isMethodStatic;
    private TreeMap<Integer, Args> args;
    //重试次数 注意分布式锁需要规避重试次数字段
    private int limitedRetryCount;
    //方法真实真行的时间
    private long realExecuteTime;
    //方法执行次数
    private int funcExecuteCount;


    public String generateSignature() {
        StringBuilder sb = new StringBuilder();

        // 按照字段名的字典序拼接属性
        sb.append("clazz=").append(clazz).append("&");
        sb.append("methodName=").append(methodName).append("&");
        sb.append("isMethodStatic=").append(isMethodStatic).append("&");

        //note: 签名时忽略 limitedRetryCount funcExecuteCount 字段 防止干扰分布式锁
        // sb.append("limitedRetryCount=").append(limitedRetryCount).append("&");
//        sb.append("funcExecuteCount=").append(funcExecuteCount).append("&");

        sb.append("realExecuteTime=").append(realExecuteTime).append("&");

        // 对 args 进行签名
        for (Map.Entry<Integer, Args> entry : args.entrySet()) {
            sb.append("args[").append(entry.getKey()).append("]=").append(entry.getValue().generateSignature()).append("&");
        }
        // 去掉最后一个 "&"
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }
}
