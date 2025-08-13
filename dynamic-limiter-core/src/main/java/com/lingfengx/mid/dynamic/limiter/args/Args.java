package com.lingfengx.mid.dynamic.limiter.args;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class Args  implements Serializable {
    private String clazz;
    private String value;


    public String generateSignature() {
        return "clazz=" + clazz + "&value=" + value;
    }
}
