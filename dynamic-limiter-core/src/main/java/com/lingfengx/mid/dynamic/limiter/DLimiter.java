package com.lingfengx.mid.dynamic.limiter;


import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class DLimiter {
    //需要限流的key
    private String key;
    //需要限流的数量
    private int boxLen;
    //限流的时间窗口
    private int boxTime;
    //是否组要限流
    private boolean limited;
}
