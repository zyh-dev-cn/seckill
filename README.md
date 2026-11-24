# 高并发秒杀系统

一个从「朴素实现」逐步演进到「Redis 预扣 + MQ 异步下单」的秒杀系统，完整记录了超卖问题的产生、暴露与解决过程。

## 功能

- **秒杀下单**：Redis Lua 脚本原子扣减库存，抢到的请求才进入下单流程
- **防超卖**：库存判断与扣减合并为原子操作，并发下不会超卖
- **防重复下单**：Redis Set 拦截 + 数据库唯一索引双保险
- **MQ 异步建单**：接口只负责扣库存和发消息，立即返回订单号，建单由消费者完成
- **库存预热**：启动时把商品信息和库存加载到 Redis，请求不依赖数据库
- **订单状态查询**：前端凭订单号轮询结果
- **前端页面**：商品卡片、库存进度条、抢购、结果轮询

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3.5 |
| 数据库 | MySQL 8 + MyBatis-Plus 3.5.7 |
| 缓存 | Redis（StringRedisTemplate + Lua 脚本） |
| 消息队列 | RabbitMQ（Spring AMQP，Jackson JSON 序列化） |
| 前端 | Vue 3（CDN 引入，无需构建） |

## 核心设计

### 1. 超卖是怎么产生的

最初版本按直觉写成「查库存 → 判断 > 0 → 扣减 → 建单」四步。200 个并发请求下，结果是这样的：

| 指标 | 数值 |
| --- | --- |
| 初始库存 | 10 |
| 成功下单 | 200 |
| 最终库存 | 9 |

每个线程都读到 `stock=10`，全部通过判断，各自算出一个 9 写回去，后写的覆盖先写的。200 人抢到了，库存只掉了 1。

### 2. 四种实现的对比

| 版本 | 做法 | 200 并发结果 | 耗时 |
| --- | --- | --- | --- |
| 朴素版 | 读、判断、写回分离 | 超卖 200 单，库存错乱 | — |
| 数据库原子扣减 | `UPDATE ... SET stock = stock - 1 WHERE id = ? AND stock > 0`，看影响行数 | 10 单，库存 0 | 2738ms |
| Redis + Lua | 库存判断与扣减放进 Lua 脚本，Redis 单线程原子执行 | 10 单，库存 0 | 2047ms |
| Redis + Lua + MQ | 扣减成功后发消息即返回，建单异步完成 | 10 单，库存 0 | 1461ms |

数据库原子扣减解决了正确性，但所有请求仍然要抢同一行记录的行锁；Redis 预扣把判断挪到内存，把失败请求挡在数据库之前；MQ 异步化让接口不再等待建单，进一步压缩响应时间。

### 3. Lua 脚本

```lua
-- KEYS[1] = 库存 key, KEYS[2] = 已购用户集合 key
-- ARGV[1] = userId
-- 返回: 1 抢到, 0 库存不足, -1 库存未预热, -2 重复下单

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

local stock = redis.call('GET', KEYS[1])
if not stock then
    return -1
end

if tonumber(stock) <= 0 then
    return 0
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
```

「查是否重复购买、查库存、扣库存、记录用户」四步在一个脚本里完成，Redis 执行 Lua 期间不会插入其他命令，天然原子。

### 4. 异步下单链路

```
请求 → 读 Redis 商品信息 → Lua 原子扣减 → 发 MQ 消息 → 立即返回订单号
                                                    ↓
                                    消费者：DB 扣库存 → 插入订单
                                                    ↓
                                    前端轮询 /api/order/{orderNo}
```

数据库在这一层只承担「最终落库」的职责，不再参与高并发争抢。

### 5. 一致性兜底

- Redis 扣减成功但发消息失败 → 回滚 Redis 库存和已购标记
- 消费者处理时数据库库存不足 → 记录告警日志，丢弃消息（正常流程下不会发生）
- 订单号唯一索引 → 消息重复投递时插入失败，不会产生重复订单

## 项目结构

```
src/main/java/com/zyh/seckill
├── config/          # Redis 脚本注册、RabbitMQ 队列声明、库存预热
├── controller/      # 秒杀、商品查询、订单查询接口
├── dto/             # 请求响应与消息体
├── entity/          # 商品、订单实体
├── listener/        # MQ 消费者（异步建单）
├── mapper/          # MyBatis-Plus Mapper（含原子扣减 SQL）
├── service/         # 秒杀核心逻辑
└── vo/              # 订单状态视图对象
src/main/resources
├── static/index.html    # 单文件前端页面
├── lua/seckill.lua      # 库存扣减脚本
├── application.yml      # 公共配置（不含密钥）
├── application-local.yml# 本机私密配置（已 gitignore）
└── schema.sql           # 建表与初始化数据
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8+
- Redis 6+
- RabbitMQ 3.9+（含 Erlang）

### 2. 初始化数据库

创建 `seckill` 库，表结构由 `schema.sql` 在服务启动时自动执行。

### 3. 本地配置

在 `src/main/resources/` 下新建 `application-local.yml`（已被 `.gitignore` 忽略）：

```yaml
spring:
  datasource:
    password: "你的数据库密码"
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 4. 启动

```bash
mvn spring-boot:run
```

或直接在 IDEA 中运行 `SeckillApplication`。

浏览器访问 <http://localhost:8082>。

## 接口说明

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/seckill/{goodsId}?userId=` | 发起秒杀，成功返回订单号 |
| GET | `/api/goods/{goodsId}` | 查询商品与库存 |
| GET | `/api/order/{orderNo}` | 查询订单是否已生成 |
| GET | `/ping` | 健康检查 |

秒杀接口返回码：

| code | 含义 |
| --- | --- |
| 0 | 抢购成功，订单生成中 |
| 1 | 商品不存在 |
| 2 | 活动未开始 |
| 3 | 活动已结束 |
| 4 | 库存不足 |
| 5 | 请勿重复下单 |
| 6 | 系统繁忙 |
| 7 | 库存未预热 |

## 已知限制

- 消息发送采用「失败回滚 Redis」的补偿方式，没有引入本地消息表或事务消息，极端情况下仍可能不一致
- 未做接口限流，真实场景需要在网关或应用层加令牌桶
- 库存预热以数据库为准，重启会覆盖 Redis 中的库存
- 单机部署，未涉及分布式锁与分库分表
