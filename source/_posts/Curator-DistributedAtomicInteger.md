title: Curator DistributedAtomicInteger
author: xdkxlk
tags:
  - Curator
  - 源码阅读
categories:
  - ZooKeeper
date: 2018-10-30 10:52:00
---
DistributedAtomicInteger 提供分布式的计数器
# 使用方法
```scala
object AtomicInt extends App {

  val PATH = "/atomicInt"

  curatorContext { client =>
    val atomicInt = new DistributedAtomicInteger(client,
      PATH, new RetryNTimes(3, 1000))

    val res = atomicInt.add(8)
    println(s"Result: ${res.preValue()} ${res.postValue()}")
  }
}
```
<!-- more -->

# 原理解析
DistributedAtomicInteger 整体来说不难。  
其内部只有一个成员变量
```java
private final DistributedAtomicValue value;
```
其实它的所有操作其实都是调用的<code>DistributedAtomicValue</code>的方法。  
## 值的修改
DistributedAtomicInteger 的 <code>add、subtract、decrement、increment</code> 其实都是调用的 DistributedAtomicInteger 的 <code>worker</code>方法，而在<code>worker</code>里面核心又是调用的 DistributedAtomicValue 的 <code>trySet</code>方法 
```java
private AtomicValue<Integer> worker(final Integer addAmount) throws Exception {
    Preconditions.checkNotNull(addAmount, "addAmount cannot be null");

    MakeValue makeValue = new MakeValue() {
        @Override
        public byte[] makeFrom(byte[] previous) {
            int previousValue = (previous != null) ? bytesToValue(previous) : 0;
            int newValue = previousValue + addAmount;
            return valueToBytes(newValue);
        }
    };

    AtomicValue<byte[]> result = value.trySet(makeValue);
    return new AtomicInteger(result);
}
```
MakeValue 告诉了 DistributedAtomicValue 如何将原来的值变成现在要设置的值，这里实现了加法
### DistributedAtomicValue.trySet
```java
AtomicValue<byte[]> trySet(MakeValue makeValue) throws Exception {
    MutableAtomicValue<byte[]> result = new MutableAtomicValue<byte[]>(null, null, false);
    
    // 试图通过乐观锁设置值（通过版本号）
    tryOptimistic(result, makeValue);
    if (!result.succeeded() && (mutex != null)) {
    	// 如果通过乐观锁设置失败了，且提供了锁
        // 则再次尝试通过在乐观锁的基础上再加一个悲观锁更新值
        tryWithMutex(result, makeValue);
    }

    return result;
}
```
- 首先，试图通过乐观锁设置值（通过版本号）
- 然后，如果通过乐观锁设置失败了，且提供了锁，则再次尝试通过在乐观锁的基础上再加一个悲观锁（代码里面将这种锁成为promoted lock）更新值
- Result 描述了操作是否成功，上次的值和新的值，以及AtomicStats
- AtomicStats 包含了乐观锁的尝试次数，使用时间；提升锁(promoted lock)的尝试次数，使用时间
### DistributedAtomicValue.tryOptimistic（乐观锁修改）
```java
private void tryOptimistic(MutableAtomicValue<byte[]> result, MakeValue makeValue) throws Exception {
    long startMs = System.currentTimeMillis();
    int retryCount = 0;

    boolean done = false;
    while (!done) {
        result.stats.incrementOptimisticTries();
        // 尝试修改一次
        // tryOnce用的是version保证的原子性
        if (tryOnce(result, makeValue)) {
            result.succeeded = true;
            done = true;
        } else {
        	// 判断是否需要重试
            if (!retryPolicy.allowRetry(retryCount++, System.currentTimeMillis() - startMs, RetryLoop.getDefaultRetrySleeper())) {
                done = true;
            }
        }
    }

    result.stats.setOptimisticTimeMs(System.currentTimeMillis() - startMs);
}
```
逻辑很简单，主要是调用的<code>tryOnce</code>进行的值的修改
### DistributedAtomicValue.tryWithMutex（加锁修改）
```java
private void tryWithMutex(MutableAtomicValue<byte[]> result, MakeValue makeValue) throws Exception {
    long startMs = System.currentTimeMillis();
    int retryCount = 0;
    
    // 相对于tryOptimistic多了一个获取锁的操作
    if (mutex.acquire(promotedToLock.getMaxLockTime(), promotedToLock.getMaxLockTimeUnit())) {
        try {
            boolean done = false;
            while (!done) {
                result.stats.incrementPromotedTries();
                if (tryOnce(result, makeValue)) {
                    result.succeeded = true;
                    done = true;
                } else {
                    if (!promotedToLock.getRetryPolicy().allowRetry(retryCount++, System.currentTimeMillis() - startMs, RetryLoop.getDefaultRetrySleeper())) {
                        done = true;
                    }
                }
            }
        } finally {
            mutex.release();
        }
    }

    result.stats.setPromotedTimeMs(System.currentTimeMillis() - startMs);
}
```
### DistributedAtomicValue.tryOnce（真正的值的修改）
```java
private boolean tryOnce(MutableAtomicValue<byte[]> result, MakeValue makeValue) throws Exception {
    Stat stat = new Stat();
    // 将当前值和节点信息获取到result和stat中，并获取是否需要创建节点
    boolean createIt = getCurrentValue(result, stat);

    boolean success = false;
    try {
        // 通过旧值，获得新值
        byte[] newValue = makeValue.makeFrom(result.preValue);
        if (createIt) {
            // 节点不存在，则创建并初始化值
            client.create().creatingParentContainersIfNeeded().forPath(path, newValue);
        } else {
            // 节点存在，通过版本修改值
            client.setData().withVersion(stat.getVersion()).forPath(path, newValue);
        }
        result.postValue = Arrays.copyOf(newValue, newValue.length);
        success = true;
    } catch (KeeperException.NodeExistsException e) {
        // 捕获异常并忽略，让外面的方法根据重试策略重试
    } catch (KeeperException.BadVersionException e) {
        // 捕获异常并忽略，让外面的方法根据重试策略重试
    } catch (KeeperException.NoNodeException e) {
        // 捕获异常并忽略，让外面的方法根据重试策略重试
    }

    return success;
}
```
## 值的获取
值的获取没有什么特别的，仅仅就是通过<code>getData</code>获取而已