<img align="center" width="300" alt="logo" src="https://gitee.com/lingfengx/static/raw/master/logo.png">

中文
# 动态限流中间件

[![GitHub](https://img.shields.io/github/stars/lingfengcoder/dynamic-limiter?color=5470c6)](https://github.com/lingfengcoder/dynamic-limiter)
 [![codecov](https://codecov.io/gh/lingfengcoder/dynamic-limiter/branch/develop/graph/badge.svg?token=WBUVJN107I)](https://codecov.io/gh/lingfengcoder/dynamic-limiter)

-------

## 什么是 dynamic-x系列
目前有：
- dynamic-limiter
- dynamic-config

dynamic-x 系列 使用足够简单的轻量级代码实现多种中间件能力，比如：动态配置、动态限流等。

# dynamic-limiter 
基于redis的动态限流中间件，支持分布式限流，支持动态调整限流参数，支持多种限流算法，支持黑白名单过滤，支持SPEL表达式。
提供以下功能支持：

- 方法级限流 - 支持方法级别的限流。
- 动态调整 - 应用运行时动态变更限流参数，（滑动窗口举例）包括不限于：窗口大小、最大通过数、黑白名单等。
- 限流算法 - 内置三种限流算法：滑动窗口、令牌桶、漏桶算法。
- 黑白名单 - 支持黑白名单过滤。
- SPEL支持 - 支持SPEL表达式，可根据方法参数动态限流

## 快速开始
spring 环境可以直接引入starter
```xml
<dependency>
    <groupId>io.github.lingfengcoder</groupId>
    <artifactId>dynamic-limiter-starter</artifactId>
    <version>${lastest.version}</version>
</dependency>
```
启动类增加开启注解
```java
@EnableDynamicLimiter(namespace="demo-appname")
public class ServletInitializer extends SpringBootServletInitializer {
 public static void main(String[] args) {
  SpringApplication.run(ServletInitializer.class, args);
 }
}
```
在需要限流的方法上增加注解
```java
class Test{
 //   
 @RdsLimit(key = "demoLimit(#userId,#tenantId)", configBean = LimitConfig.class, fallBack = "demoLimitFallback", autoRelease = false)
 public Boolean demoLimit(String userId, String tenantId) {
  //通过限流
  return true;
 }
 //demoLimitFallback限流降级方法
  public Boolean demoLimitFallback(String userId, String tenantId) {
  //限流降级
  return false;
  }
 
}
```

配置文件(需要是bean对象)
```java

//动态配置文件
@DynamicValConfigMap(file = "limit.properties", prefix = "limit.something")
public class LimitConfig  extends AbstractRdsLimitConfig {
 @Value("true")
 private boolean enable;
 //#滑动窗口的大小
 @Value("10")
 private int boxLen;
 //#滑动窗口的时间
 @Value("10000")
 private long boxTime;


 @Override
 public Function<Map<String, Object>, DLimiter> getDynamicLimiter() {
  return map -> {
   Object tenantId = findParam(map, "tenantId");
   Object paramA = findParam(map, "paramA");
   StringBuilder key = new StringBuilder("xxxLimitConfig");
   if (tenantId != null) {
    key.append("_").append(tenantId);
   }
   if (qaBotId != null) {
    key.append("_").append(paramA);
   }
   DLimiter limiter = new DLimiter();
   limiter.setKey(key.toString())
           .setLimited( enable).setBoxLen(boxLen).setBoxTime(boxTime);
   return limiter;
  };
 }
}
```
## 接入登记

更多接入的公司，欢迎在 [登记地址]() 登记，登记仅仅为了产品推广。

## 联系我

## 友情链接

## 贡献者

感谢所有为项目作出贡献的开发者。如果有意贡献，参考 [good first issue]()。
