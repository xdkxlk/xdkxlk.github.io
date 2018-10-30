title: Curator Barrier
author: xdkxlk
tags:
  - Curator
  - 源码阅读
categories:
  - ZooKeeper
date: 2018-10-30 17:18:00
---
Curator中有两种Barrier的实现。
- DistributedBarrier 在子线程中等待阻塞，主线程中统一放行
- DistributedDoubleBarrier 跟主线程就没有太大的联系了，子线程统一进入 barrier，统一离开 barrier

# DistributedBarrier
## 使用方法
```scala
object CuratorBarrier01 extends App {

  val PATH = "/barrier01"

  curatorContext { client =>
    val barrier = new DistributedBarrier(client, PATH)

    for (i <- 0 until 5) {
      new Thread(() => {
        println(Thread.currentThread().getName)

        barrier.setBarrier()
        barrier.waitOnBarrier()
        println(s"${Thread.currentThread().getName} start")
      }).start()
    }

    Thread.sleep(2000)
    barrier.removeBarrier()

    Thread.sleep(100)
  }

}
```
## 实现思路
DistributedBarrier 的实现比较简单，整体思路是所有子线程监听同一个节点路径，主线程删除了这个节点之后，子线程收到删除事件，就实现了统一的放行。  
代码很短，就不再赘述了。
# DistributedDoubleBarrier
## 使用方法
```scala
object CuratorBarrier02 extends App {

  val PATH = "/barrier01"

  curatorContext { client =>

    for (i <- 0 until 5) {
      new Thread(() => {
        val barrier = new DistributedDoubleBarrier(client, PATH, 5)

        Thread.sleep(Random.nextInt(5) * 1000)

        println(s"${Thread.currentThread().getName} enter")
        barrier.enter()

        println(s"${Thread.currentThread().getName} start ...")
        Thread.sleep(Random.nextInt(5) * 1000)

        barrier.leave()
        println(s"${Thread.currentThread().getName} leave")
      }).start()
    }

    while (true) {}
  }
}
```
```
Thread-1 enter
Thread-3 enter
Thread-2 enter
Thread-5 enter
Thread-4 enter
Thread-2 start ...
Thread-4 start ...
Thread-1 start ...
Thread-5 start ...
Thread-3 start ...
Thread-5 leave
Thread-3 leave
Thread-4 leave
Thread-2 leave
Thread-1 leave
```
## 实现思路
### 构造函数
```java
public DistributedDoubleBarrier(CuratorFramework client, String barrierPath, int memberQty) {
    Preconditions.checkState(memberQty > 0, "memberQty cannot be 0");

    this.client = client;
    this.barrierPath = PathUtils.validatePath(barrierPath);
    this.memberQty = memberQty;
    ourPath = ZKPaths.makePath(barrierPath, UUID.randomUUID().toString());
    readyPath = ZKPaths.makePath(barrierPath, READY_NODE);
}
```
- ourPath 是每个子线程都独有的节点
- readyPath 当全部节点都enter之后，会创建这个节点，代表子线程都已经调用了enter可以继续运行了。其值是 {barrierPath}/ready

### enter
```java
public boolean enter(long maxWait, TimeUnit unit) throws Exception {
    long startMs = System.currentTimeMillis();
    boolean hasMaxWait = (unit != null);
    long maxWaitMs = hasMaxWait ? TimeUnit.MILLISECONDS.convert(maxWait, unit) : Long.MAX_VALUE;
    
    // 判断readyPath是否已经存在了，并监听
    boolean readyPathExists = (client.checkExists().usingWatcher(watcher).forPath(readyPath) != null);
    // 创建当前线程的节点
    client.create().creatingParentContainersIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(ourPath);
    
    // internalEnter会阻塞，进入等待
    boolean result = (readyPathExists || internalEnter(startMs, hasMaxWait, maxWaitMs));
    if (connectionLost.get()) {
        throw new KeeperException.ConnectionLossException();
    }

    return result;
}
```
### internalEnter
是enter方法实际上阻塞的地方
```java
private synchronized boolean internalEnter(long startMs, boolean hasMaxWait, long maxWaitMs) throws Exception {
    boolean result = true;
    do {
        // 获得子节点列表
        List<String> children = getChildrenForEntering();
        int count = (children != null) ? children.size() : 0;
        if (count >= memberQty) {
            // 如果数量已经达到了barrier的数量，则创建ready节点
            try {
                client.create().forPath(readyPath);
            } catch (KeeperException.NodeExistsException ignore) {
                // ignore
            }
            break;
        }

        if (hasMaxWait && !hasBeenNotified.get()) {
            long elapsed = System.currentTimeMillis() - startMs;
            long thisWaitMs = maxWaitMs - elapsed;
            if (thisWaitMs <= 0) {
                result = false;
            } else {
                wait(thisWaitMs);
            }

            if (!hasBeenNotified.get()) {
                result = false;
            }
        } else {
            wait();
        }
    } while (false); 
    // 不知道为什么这里既然是false为什还要用 do while

    return result;
}
```
整体上的思路并不复杂
- 获得子节点列表
- 判断子节点数目是否达到了barrier要求的放行数目
- 如果达到了，则创建 ready 节点，函数返回；如果没有达到则 wait
- 由于在 enter 里面监听了 ready ，那么当某个子线程发现达到数目要求并创建 ready 节点的时候，会触发 watcher，通知所有子线程，函数结束返回。

存在几一个疑点
- 不知道 do while(false) 有什么用

### leave、internalLeave
leave里面就是简单的处理了以下参数，然后调用了 internalLeave ，所以直接看 internalLeave。
```java
private boolean internalLeave(long startMs, boolean hasMaxWait, long maxWaitMs) throws Exception {
    String ourPathName = ZKPaths.getNodeFromPath(ourPath);
    boolean ourNodeShouldExist = true;
    boolean result = true;
    for (; ; ) {
        if (connectionLost.get()) {
            throw new KeeperException.ConnectionLossException();
        }

        List<String> children;
        try {
            // 获得所有子节点（这里面有ready节点）
            children = client.getChildren().forPath(barrierPath);
        } catch (KeeperException.NoNodeException dummy) {
            children = Lists.newArrayList();
        }
        // 把ready节点过滤掉并根据字典排序
        children = filterAndSortChildren(children);
        if ((children == null) || (children.size() == 0)) {
        	// 没有子节点了，不再阻塞
            break;
        }

        // 当前线程的子节点位于子节点列表中的哪个位置
        int ourIndex = children.indexOf(ourPathName);
        if ((ourIndex < 0) && ourNodeShouldExist) {
            // 如果发现神奇的不存在，那么就说明出现问题了
            if (connectionLost.get()) {
                break;  // connection was lost but we've reconnected. However, our ephemeral node is gone
            } else {
                throw new IllegalStateException(String.format("Our path (%s) is missing", ourPathName));
            }
        }

        // 如果现在只剩下一个节点了
        if (children.size() == 1) {
            if (ourNodeShouldExist && !children.get(0).equals(ourPathName)) {
                // 如果发现神奇的不存在，那么就说明出现问题了
                throw new IllegalStateException(String.format("Last path (%s) is not ours (%s)", children.get(0), ourPathName));
            }
            // 根据存在的状态将这个节点删除
            checkDeleteOurPath(ourNodeShouldExist);
            break;
        }

        Stat stat;
        boolean IsLowestNode = (ourIndex == 0);
        if (IsLowestNode) {
            // 如果是第一个节点，那么检查最后的一个节点是否存在，并监听checkExists
            String highestNodePath = ZKPaths.makePath(barrierPath, children.get(children.size() - 1));
            stat = client.checkExists().usingWatcher(watcher).forPath(highestNodePath);
        } else {
            // 不是第一个节点，或者当前节点已经不存在了，那么检查第一个节点是否存在，并监听checkExists
            String lowestNodePath = ZKPaths.makePath(barrierPath, children.get(0));
            stat = client.checkExists().usingWatcher(watcher).forPath(lowestNodePath);
            // 然后根据存在的状态删除这个线程的节点
            checkDeleteOurPath(ourNodeShouldExist);
            ourNodeShouldExist = false;
        }

        if (stat != null) {
            // 监听的节点存在
            // 进入阻塞等待
            if (hasMaxWait) {
                long elapsed = System.currentTimeMillis() - startMs;
                long thisWaitMs = maxWaitMs - elapsed;
                if (thisWaitMs <= 0) {
                    result = false;
                } else {
                    wait(thisWaitMs);
                }
            } else {
                wait();
            }
        }
    }

    try {
        // 删除ready节点
        client.delete().forPath(readyPath);
    } catch (KeeperException.NoNodeException ignore) {
        // ignore
    }

    return result;
}
```
以一个例子来说明处理的流程
<img src="/img/ifpSThLBLkI1v59BfiTO.png" width="60%" />
现在有 L1、L2、L3、L4 4个子线程的节点。
- 首先，L1 leave，因为是子节点列表中的第一个节点，所以就单纯的监听列表中的最后一个节点，最后阻塞。
- L4 leave，不是首个节点，删除自己，并监听第一个节点，置ourNodeShouldExist为false。触发了 L1 的 watcher，notifyAll 阻塞的子线程（L1，L4)。L1重新监听现在的最后的一个节点 L3，最后再一次阻塞。L4 由于ourNodeShouldExist为false，所以实际上并不执行什么实际上的逻辑，监听第一个节点，再一次阻塞。
- L2 leave，不是首个节点，删除自己，并监听第一个节点，置ourNodeShouldExist为false。最后阻塞。
- L3 leave，不是首个节点，删除自己，并监听第一个节点，置ourNodeShouldExist为false。触发了 L1 的 watcher，notifyAll 阻塞的子线程（L1，L2，L3，L4)，除了L1，其他的ourNodeShouldExist都为false，所以没有什么操作。L1 的子线程发现现在只有自己了，则删除自己，L1 子线程继续执行。L1 删除触发 watcher，其他子线程发现子节点列表为空，不再阻塞，子线程继续执行。
