title: Curator InterProcessMutex
author: xdkxlk
tags:
  - Curator
  - 源码阅读
categories:
  - ZooKeeper
date: 2018-10-29 11:25:00
---
# InterProcessMutex的使用
## Lock.scala
```scala
object Lock extends App {

  val LOCK_PATH = "/lock"

  curatorContext { client =>
    val mutex = new InterProcessMutex(client, LOCK_PATH)

    for (i <- 0 until 10) {
      new Thread(() => {
        try {
          println(s"Thread ${Thread.currentThread().getName}")
          mutex.acquire()
        } catch {
          case NonFatal(e) =>
        }

        println(s"Thread ${Thread.currentThread().getName} start")

        val sdf = new SimpleDateFormat("HH:mm:ss|SSS")
        println(s"Order No. : ${sdf.format(new Date)}")

        try {
          mutex.release()
          println(s"Thread ${Thread.currentThread().getName} finish")
        } catch {
          case NonFatal(e) =>
        }
      }).start()
    }

    while (true) {}
  }

}
```
## 效果
```
Thread Thread-1
Thread Thread-9
Thread Thread-5
Thread Thread-10
Thread Thread-8
Thread Thread-7
Thread Thread-2
Thread Thread-6
Thread Thread-3
Thread Thread-4
Thread Thread-2 start
Order No. : 10:05:15|616
Thread Thread-2 finish
Thread Thread-7 start
Order No. : 10:05:15|636
Thread Thread-7 finish
Thread Thread-10 start
Order No. : 10:05:15|659
Thread Thread-10 finish
Thread Thread-4 start
Order No. : 10:05:15|666
Thread Thread-4 finish
Thread Thread-3 start
Order No. : 10:05:15|675
Thread Thread-3 finish
Thread Thread-8 start
Order No. : 10:05:15|683
Thread Thread-8 finish
Thread Thread-9 start
Order No. : 10:05:15|695
Thread Thread-9 finish
Thread Thread-5 start
Order No. : 10:05:15|701
Thread Thread-5 finish
Thread Thread-6 start
Order No. : 10:05:15|727
Thread Thread-6 finish
Thread Thread-1 start
Order No. : 10:05:15|742
Thread Thread-1 finish
```
# 概念
## 分布式锁
![upload successful](/img/ZErSvMf3sCcyUW3h9IC0.png)
各个方案的优缺点
![upload successful](/img/YV2r2NnTUmrq01P9uZRD.png)
## Curator的分布式锁介绍
Curator主要提供了几种分布式锁，类图如下
![upload successful](/img/8USvczyLZsZxe1bA00w0.png)
## 可重入锁和不可重入锁
广义上的可重入锁指的是可重复可递归调用的锁，在外层使用锁之后，在内层仍然可以使用，并且不发生死锁（前提得是同一个对象或者class），这样的锁就叫做可重入锁。Java中ReentrantLock和synchronized是可重入锁。
所谓不可重入锁，即若当前线程执行某个方法已经获取了该锁，那么在方法中尝试再次获取锁时，就会获取不到而被阻塞。
**InterProcessMutex是可重入锁**
# InterProcessMutex执行过程
## 创建
创建其实没有什么，只不过这里面默认的有一个<code>StandardLockInternalsDriver</code>，这个是默认的驱动器,这个是做什么的后面再说。  
- client：curator实现的zookeeper客户端
- path：要在zookeeper加锁的路径，即后面创建临时节点的父节点  
  
```java
public InterProcessMutex(CuratorFramework client, String path) {
    this(client, path, new StandardLockInternalsDriver());
}

public InterProcessMutex(CuratorFramework client, String path, LockInternalsDriver driver) {
    this(client, path, LOCK_NAME, 1, driver);
}
```
最终调用的是一个可见范围为包的构造函数
```java
InterProcessMutex(CuratorFramework client, String path, String lockName, int maxLeases, LockInternalsDriver driver) {
        basePath = PathUtils.validatePath(path);
        internals = new LockInternals(client, driver, path, lockName, maxLeases);
    }
```
注意，这里的<code>maxLeases</code>为1，<code>LOCK_NAME</code>为<code>lock-</code>  
<code>new LockInternals</code>也是一个可见范围为包的构造函数，里面就是对于参数进行了值的初始化和检查
```java
LockInternals(CuratorFramework client, LockInternalsDriver driver, String path, String lockName, int maxLeases) {
        this.driver = driver;
        this.lockName = lockName;
        this.maxLeases = maxLeases;

        this.client = client.newWatcherRemoveCuratorFramework();
        this.basePath = PathUtils.validatePath(path);
        this.path = ZKPaths.makePath(path, lockName);
    }
```
## acquire加锁
调用 acquire 方法里面没有什么，其实调用的是 internalLock
```java
@Override
public void acquire() throws Exception {
    if (!internalLock(-1, null)) {
        throw new IOException("Lost connection while trying to acquire lock: " + basePath);
    }
}

@Override
public boolean acquire(long time, TimeUnit unit) throws Exception {
    return internalLock(time, unit);
}
```
## internalLock
**实现了锁的可重入**
```java
private boolean internalLock(long time, TimeUnit unit) throws Exception {
    /*
       Note on concurrency: a given lockData instance
       can be only acted on by a single thread so locking isn't necessary
       一个lockData仅仅只会在一个线程中访问（因为是根据线程进行保存的），所以没必要加锁
    */
    Thread currentThread = Thread.currentThread();

    LockData lockData = threadData.get(currentThread);
    if (lockData != null) {
        // 可重入锁
        // 如果任意线程在获取到锁之后，再次获取该锁而不会被该锁所阻塞
        // 关联一个线程持有者+计数器，重入意味着锁操作的颗粒度为“线程”
        // re-entering
        lockData.lockCount.incrementAndGet();
        return true;
    }

    // 开始竞争获取锁
    String lockPath = internals.attemptLock(time, unit, getLockNodeBytes());
    if (lockPath != null) {
    	// 拿到了锁
        LockData newLockData = new LockData(currentThread, lockPath);
        threadData.put(currentThread, newLockData);
        return true;
    }

    return false;
}
```
这段代码里面，实现了锁的可重入。每个 InterProcessMutex 实例，都会持有一个 ConcurrentMap 类型的 threadData 对象，以线程对象作为 Key，以 LockData 作为 Value 值。通过判断当前线程 threadData 是否有值，如果有，则表示线程可以重入该锁，于是将 lockData 的 lockCount 进行累加；如果没有，则进行锁的抢夺。  
internals.attemptLock 方法返回 lockPath!=null 时，表明了该线程已经成功持有了这把锁，于是乎 LockData 对象被 new 了出来，并存放到 threadData 中。  
这里为什么要用一个 ConcurrentMap 来保存呢？回顾开头的 InterProcessMutex的使用 ，这10个线程其实用的是同一个 InterProcessMutex 对象，所以需要 ConcurrentMap 保存。
## 锁的竞争
### attemptLock
```java
String attemptLock(long time, TimeUnit unit, byte[] lockNodeBytes) throws Exception {
    final long startMillis = System.currentTimeMillis();
    final Long millisToWait = (unit != null) ? unit.toMillis(time) : null;
    final byte[] localLockNodeBytes = (revocable.get() != null) ? new byte[0] : lockNodeBytes;
    int retryCount = 0;

    String ourPath = null;
    boolean hasTheLock = false;
    boolean isDone = false;
    while (!isDone) {
        isDone = true;

        try {
            // 这个就是单纯的创建一个临时节点，返回路径
            // /lock/_c_c29bfdef-d575-4930-9140-39befca73e42-lock-0000000060
            ourPath = driver.createsTheLock(client, path, localLockNodeBytes);

            hasTheLock = internalLockLoop(startMillis, millisToWait, ourPath);
        } catch (KeeperException.NoNodeException e) {
            // gets thrown by StandardLockInternalsDriver when it can't find the lock node
            // this can happen when the session expires, etc. So, if the retry allows, just try it all again
            if (client.getZookeeperClient().getRetryPolicy().allowRetry(retryCount++, System.currentTimeMillis() - startMillis, RetryLoop.getDefaultRetrySleeper())) {
                isDone = false;
            } else {
                throw e;
            }
        }
    }

    if (hasTheLock) {
        return ourPath;
    }

    return null;
}
```
- while循环正常来说，会在下一次结束。但是当出现NoNodeException异常时，会根据zookeeper客户端的重试策略，进行有限次数的重新获取锁。
- driver.createsTheLock 创建锁，其实就是单纯的创建一个临时序列节点的方法。
- internalLockLoop 是一个阻塞的方法，当它正常返回的时候，就意味着已经拿到锁了  

### driver.createsTheLock
创建一个临时序列节点作为锁，并返回创建的路径。注意，创了一个并不代表获得了锁。也可以注意到，创建的这个节点是带<code>withProtection()</code>的。  
<code>InterProcessMutex</code>的<code>lockNodeBytes</code>为<code>null</code>
```java
public String createsTheLock(CuratorFramework client, String path, byte[] lockNodeBytes) throws Exception {
    String ourPath;
    if (lockNodeBytes != null) {
        ourPath = client.create()
                .creatingParentContainersIfNeeded()
                .withProtection()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(path, lockNodeBytes);
    } else {
        ourPath = client.create()
                .creatingParentContainersIfNeeded()
                .withProtection()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(path);
    }
    return ourPath;
}
```
### driver.internalLockLoop
判断自身是否能够持有锁。如果不能，进入wait，等待被唤醒。正常情况下，internalLockLoop 只有在超时或者拿到了锁才返回。
```java
private boolean internalLockLoop(long startMillis, Long millisToWait, String ourPath) throws Exception {
    // ourPath /lock/_c_c29bfdef-d575-4930-9140-39befca73e42-lock-0000000060
    boolean haveTheLock = false;
    boolean doDelete = false;
    try {
        if (revocable.get() != null) {
            client.getData().usingWatcher(revocableWatcher).forPath(ourPath);
        }

        while ((client.getState() == CuratorFrameworkState.STARTED) && !haveTheLock) {
            // 获取到所有子节点列表，并且从小到大根据节点名称排序
            List<String> children = getSortedChildren();
            // +1 to include the slash
            // _c_c29bfdef-d575-4930-9140-39befca73e42-lock-0000000060
            String sequenceNodeName = ourPath.substring(basePath.length() + 1);

            // maxLeases 在 InterProcessMutex 为 1
            // 判断是否可以持有锁，判断规则：
            // 当前创建的节点(sequenceNodeName)是否在上一步获取到的子节点列表(children)的第maxLeases位置
            PredicateResults predicateResults = driver.getsTheLock(client, children, sequenceNodeName, maxLeases);
            if (predicateResults.getsTheLock()) {
                haveTheLock = true;
            } else {
                // pathToWatch 是前maxLeases个节点
                // 对于InterProcessMutex，是指前面一个节点
                String previousSequencePath = basePath + "/" + predicateResults.getPathToWatch();

                synchronized (this) {
                    try {
                        // use getData() instead of exists() to avoid leaving unneeded watchers which is a type of resource leak
                        // watcher 会在监视的节点删除、更新（释放锁用的是删除，所以不会触发更新）的时候被触发
                        // 然后watcher里面会调用 notifyAll() 唤醒线程
                        client.getData().usingWatcher(watcher).forPath(previousSequencePath);
                        if (millisToWait != null) {
                            millisToWait -= (System.currentTimeMillis() - startMillis);
                            startMillis = System.currentTimeMillis();
                            if (millisToWait <= 0) {
                                // timed out - delete our node
                                doDelete = true;
                                break;
                            }

                            wait(millisToWait);
                        } else {
                            wait();
                        }
                    } catch (KeeperException.NoNodeException e) {
                        // it has been deleted (i.e. lock released). Try to acquire again
                    }
                }
            }
        }
    } catch (Exception e) {
        ThreadUtils.checkInterrupted(e);
        doDelete = true;
        throw e;
    } finally {
        if (doDelete) {
            deleteOurPath(ourPath);
        }
    }
    return haveTheLock;
}
```
### while 循环
如果没有设置超时间，那么这个 while 是一个死循环。如果设置了，那么会 wait 一段时间，如果超时了,那么会进入下一个 while 循环，然后判断超时 break 出去。如果没有设置超时，会在被唤醒之后，进入下一个循环，直到 haveTheLock 为 true 之后才退出循环。
### getSortedChildren
这个方法比较简单，就是获取到所有子节点列表，并且从小到大根据节点名称后10位数字进行排序。在上面提到了，创建的是序列节点。
### driver.getsTheLock
```java
public PredicateResults getsTheLock(CuratorFramework client, List<String> children, String sequenceNodeName, int maxLeases) throws Exception {
    int ourIndex = children.indexOf(sequenceNodeName);
    // 判断 index 是否合法
    // index 是否大于0，大于0合法
    // 小于0说明出现了异常，创建的锁节点不见了
    validateOurIndex(sequenceNodeName, ourIndex);
    
    // 这里是判断是否成功拿到锁
    boolean getsTheLock = ourIndex < maxLeases;
    String pathToWatch = getsTheLock ? null : children.get(ourIndex - maxLeases);

    return new PredicateResults(pathToWatch, getsTheLock);
}
```
### 锁获取重试的规则
对于 InterProcessMutex ，由于 maxLeases=1 。所以是判断当前节点是不是在子节点列表的第一个。如果是第一个，则获得锁成功。如果获取失败，则监听前一个节点，调用<code>wait</code>，线程交出cpu的占用，进入等待状态，等到被唤醒，监听删除的触发事件 watcher（getData 的 watcher）。watcher 被触发时调用 <code>notifyAll()</code>
## release释放锁
```java
public void release() throws Exception {
    /*
        Note on concurrency: a given lockData instance
        can be only acted on by a single thread so locking isn't necessary
     */

    Thread currentThread = Thread.currentThread();
    LockData lockData = threadData.get(currentThread);
    if (lockData == null) {
        throw new IllegalMonitorStateException("You do not own the lock: " + basePath);
    }

    // 因为是可重入锁，所以，计数减1
    int newLockCount = lockData.lockCount.decrementAndGet();
    if (newLockCount > 0) {
    	// 没有减到0，则直接返回
        return;
    }
    if (newLockCount < 0) {
        throw new IllegalMonitorStateException("Lock count has gone negative for lock: " + basePath);
    }
    try {
        internals.releaseLock(lockData.lockPath);
    } finally {
        threadData.remove(currentThread);
    }
}
```
- 减少重入锁的计数，直到变成0。
- 释放锁，即移除移除 watchers & 删除创建的节点
- 从 threadData 中，删除自己线程的缓存  
 
## 锁驱动类
锁驱动类有3个方法，通过这个方法，我们可以自定义一些过程。
- getsTheLock：判断是够获取到了锁
- createsTheLock：在zookeeper的指定路径上，创建一个临时序列节点。
- fixForSorting：修改lock节点的路径字符串以进行排序，在StandardLockInternalsDriver的实现中，即获取到临时节点的最后序列数，进行排序。

# 总结
多个线程竞争锁，这些线程都在同一个ZooKeeper路径下创建临时的递增的子节点序列，如果某一个线程创建的节点位于所有子节点序列的第一个，则获得到锁。如果不是，则失败，监听前面的一个节点的的getData，当前面一个节点被删除（即锁完全释放），则被重新唤醒，重新进行锁的竞争。  
对于锁的重入，如果已经拿到了锁，就简单的将内部的 ConcurrentMap 的当前线程的计数加一。

# 参考
[curator笔记-分布式锁的实现与原理](https://www.jianshu.com/p/6618471f6e75)