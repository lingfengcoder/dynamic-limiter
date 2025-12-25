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
- 限流算法 - 内置四种限流算法：滑动窗口、令牌桶、信号量、信号量+窗口。
- 黑白名单 - 支持黑白名单过滤。
- SPEL支持 - 支持SPEL表达式，可根据方法参数动态限流

## 限流算法详解

### 算法对比

| 算法 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **滑动窗口** | 接口 QPS 限制 | 简单直观，精确控制时间窗口内请求数 | 不能平滑突发流量 |
| **令牌桶** | 允许突发流量的场景 | 平滑限流，允许短时突发 | 配置参数较多 |
| **信号量** | 慢接口并发控制 | 精确控制同时执行数，自适应执行时间 | 不能限制总请求数 |
| **信号量+窗口** | 慢接口 + 总量控制 | 同时控制并发和时间窗口内总量 | 配置参数最多 |

---

### 1. 滑动窗口算法 (SlidingWindow)

**原理**：统计时间窗口内的请求数量，超过阈值则拒绝。

```
|◀──────── boxTime ────────▶|
┌─────────────────────────┐
│ ● ● ● ● ● ● ● ● ● ●    │  ≤ boxLen → 通过
└─────────────────────────┘
```

**使用方式**：
```java
@RdsLimit(
    algo = LimiterAlgo.SlidingWindow,
    boxLen = 100,              // 窗口内最大请求数
    boxTime = 1000,            // 窗口时间（毫秒）
    key = "api-limit(#userId)",
    configBean = MyConfig.class,
    fallBack = "fallbackMethod"
)
public Result myApi(String userId) {
    // 业务逻辑
}
```

**适用场景**：
- 接口 QPS 限制（如：每秒最多 100 次请求）
- 防止接口被恶意调用
- API 调用频率限制

**优点**：
- 配置简单，只需设置窗口大小和时间
- 精确控制时间窗口内的请求数

**缺点**：
- 不能平滑处理突发流量
- 窗口边界可能出现瞬时高峰

---

### 2. 令牌桶算法 (TokenRate)

**原理**：以固定速率生成令牌，请求需要消耗令牌才能通过。

```
     ┌───────────────┐
     │   令牌桶       │ ← 以 tokenRate 速率持续填充
     │  ○ ○ ○ ○ ○    │   (最多 tokenCapacity 个)
     │  ○ ○ ○        │
     └───────┬───────┘
             │ 请求消耗令牌
             ▼
    有令牌 → 通过 ✓
    无令牌 → 拒绝 ✗
```

**使用方式**：
```java
@RdsLimit(
    algo = LimiterAlgo.TokenRate,
    tokenCapacity = 100,       // 桶容量（最大令牌数）
    tokenRate = 10,            // 填充速率（令牌/秒）
    tokenPermits = 1,          // 每次请求消耗的令牌数
    key = "token-limit(#userId)",
    configBean = MyConfig.class,
    fallBack = "fallbackMethod"
)
public Result myApi(String userId) {
    // 业务逻辑
}
```

**适用场景**：
- 允许短时突发流量的场景
- 需要平滑限流的场景
- 不同请求消耗不同资源的场景

**优点**：
- 允许突发流量（突发时可消耗积累的令牌）
- 长期看流量平滑
- 可配置不同请求消耗不同令牌数

**缺点**：
- 配置参数较多
- 需要根据业务合理设置容量和速率

---

### 3. 信号量算法 (Semaphore)

**原理**：控制同时执行的请求数，执行完成后释放槽位。

```
┌─────────────────────────────┐
│  信号量槽位 (semaphorePermits=5) │
├─────────────────────────────┤
│ [执行中] [执行中] [执行中] [空] [空] │
└─────────────────────────────┘
         │                   │
    新请求获取槽位      执行完成释放槽位
```

**使用方式**：
```java
@RdsLimit(
    algo = LimiterAlgo.Semaphore,
    semaphorePermits = 20,     // 最大并发数
    semaphoreTimeout = 60000,  // 超时时间（毫秒，防止死锁）
    key = "slow-api(#userId)",
    configBean = MyConfig.class,
    fallBack = "fallbackMethod"
)
public Result slowApi(String userId) {
    // 执行时间较长的业务逻辑（7-8秒）
}
```

**适用场景**：
- 慢接口并发控制（如：调用第三方 API）
- 资源有限的场景（如：数据库连接数限制）
- 接口执行时间不固定的场景

**优点**：
- 精确控制同时执行的请求数
- 自适应接口执行时间（执行完自动释放）
- 不会因为执行慢而浪费槽位

**缺点**：
- 不能限制时间窗口内的总请求数
- 需要设置合理的超时时间防止死锁

---

### 4. 信号量+窗口算法 (SemaphoreWindow)

**原理**：同时控制最大并发数和时间窗口内的总请求数。

```
┌─────────────────────────────────────────┐
│                 双重限制                      │
├───────────────────┬─────────────────────┤
│  并发限制 (信号量)   │   总量限制 (滑动窗口)   │
│  同时最多20个执行    │   每分钟最多100个请求  │
└───────────────────┴─────────────────────┘
                    │
           两个条件都满足才放行
```

**使用方式**：
```java
@RdsLimit(
    algo = LimiterAlgo.SemaphoreWindow,
    semaphorePermits = 20,         // 最大并发数
    semaphoreTimeout = 60000,      // 并发超时（毫秒）
    semaphoreWindowLen = 100,      // 窗口内最大请求数
    semaphoreWindowTime = 60000,   // 窗口时间（毫秒）
    key = "slow-api(#userId)",
    configBean = MyConfig.class,
    fallBack = "fallbackMethod"
)
public Result slowApi(String userId) {
    // 同时最多 20 个并发执行
    // 且每分钟最多发起 100 个请求
}
```

**适用场景**：
- 慢接口 + 下游有配额限制（如：第三方 API 每分钟最多调用 100 次）
- 防止请求堆积的场景
- 需要同时控制并发和总量的场景

**优点**：
- 精确控制并发数，防止后端压垂
- 同时限制总请求数，保护下游配额
- 窗口统计在 Acquire 时记录，语义清晰

**缺点**：
- 配置参数最多
- 需要合理设置并发数和窗口限制的关系

---

### 算法选择决策树

```
接口执行时间如何？
    │
    ├── 快速接口 (<1s)
    │       │
    │       ├── 允许突发？ → 令牌桶 (TokenRate)
    │       │
    │       └── 不允许突发？ → 滑动窗口 (SlidingWindow)
    │
    └── 慢速接口 (>1s)
            │
            ├── 只控制并发？ → 信号量 (Semaphore)
            │
            └── 同时控制并发+总量？ → 信号量+窗口 (SemaphoreWindow)
```

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
   // 根据不同算法使用对应实现类
   SlidingWindowDLimiter limiter = new SlidingWindowDLimiter();
   limiter.setKey(key.toString())
           .setLimited(enable)
           .setBoxLen(boxLen)
           .setBoxTime(boxTime);
   return limiter;
  };
 }
}

/**
 * 动态限流器实现类说明：
 * - DLimiter: 接口，定义通用方法
 * - SlidingWindowDLimiter: 滑动窗口算法专用参数
 *   - boxLen: 窗口内最大请求数
 *   - boxTime: 时间窗口大小（毫秒）
 * - TokenRateDLimiter: 令牌桶算法专用参数
 *   - tokenCapacity: 令牌桶容量
 *   - tokenRate: 令牌填充速率（令牌/秒）
 *   - tokenPermits: 每次请求消耗的令牌数
 * - SemaphoreDLimiter: 信号量算法专用参数
 *   - semaphorePermits: 最大并发许可数
 *   - semaphoreTimeout: 信号量超时时间（毫秒，防止死锁）
 * - SemaphoreWindowDLimiter: 信号量+窗口算法专用参数
 *   - semaphorePermits: 最大并发许可数
 *   - semaphoreTimeout: 信号量超时时间（毫秒，防止死锁）
 *   - semaphoreWindowLen: 窗口内最大请求数
 *   - semaphoreWindowTime: 窗口时间大小（毫秒）
 */
```
## 接入登记

更多接入的公司，欢迎在 [登记地址]() 登记，登记仅仅为了产品推广。

## 联系我

## 友情链接

## 贡献者

感谢所有为项目作出贡献的开发者。如果有意贡献，参考 [good first issue]()。
