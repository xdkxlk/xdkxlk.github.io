title: Curator NodeCache学习
author: xdkxlk
tags:
  - Curator
  - ''
  - 源码阅读
categories:
  - ZooKeeper
date: 2018-10-25 20:38:00
---
# NodeCache的使用
## package.scala
```scala
package object curator {

  def curatorContext(f: CuratorFramework => Unit)(implicit nameSpace: String = "curator"): Unit = {

    val retryPolicy = new ExponentialBackoffRetry(1000, 3)
    val client = CuratorFrameworkFactory.builder()
      .connectString(ZOOKEEPER_HOST)
      .sessionTimeoutMs(5000)
      .connectionTimeoutMs(1000)
      .retryPolicy(retryPolicy)
      .namespace(nameSpace)
      .build()
    client.start()

    f(client)

    client.close()
  }
}
```
## NodeCacheSample.scala
```scala
object NodeCacheSample extends App {

  curatorContext { client =>
    val path = "/c1"

    client.create()
      .creatingParentsIfNeeded()
      .withMode(CreateMode.EPHEMERAL)
      .forPath(path, "init".getBytes)
    val cache = new NodeCache(client, path, false)
    cache.start(false)
    cache.getListenable.addListener(() => {
      println(s"Node data changed new data is " +
        s"${new String(cache.getCurrentData.getData)}")
    })

    client.setData().forPath(path, "d1".getBytes)

    Thread.sleep(1000)

    client.setData().forPath(path, "d2".getBytes)

    Thread.sleep(5000)
  }
}
```
# NodeCache的执行过程
NodeCache的代码不长，逻辑不是很复杂 ~~（虽然我还是看了好久）~~
## 几个关键的成员变量
```java
// 保存最新的节点数据
private final AtomicReference<ChildData> data = new AtomicReference<ChildData>(null);
// NodeCache的状态，LATENT（未启动），STARTED（正在运行），CLOSED（已关闭）
private final AtomicReference<State> state = new AtomicReference<State>(State.LATENT);
// 保存的Listener
private final ListenerContainer<NodeCacheListener> listeners = new ListenerContainer<NodeCacheListener>();
// Zookeeper连接状态，是否已经连接上了
private final AtomicBoolean isConnected = new AtomicBoolean(true);
```
## 创建
```java
public NodeCache(CuratorFramework client, String path, boolean dataIsCompressed) {
        this.client = client.newWatcherRemoveCuratorFramework();
        this.path = PathUtils.validatePath(path);
        this.dataIsCompressed = dataIsCompressed;
}
```
注意这一行<code>client.newWatcherRemoveCuratorFramework();</code>返回的是一个<code>WatcherRemoveCuratorFramework</code>这个接口在原有的<code>CuratorFramework</code>上多了一个<code>removeWatchers</code>的方法，这个方法可以移除掉所有的<code>watchers</code>
## start
```java
public void start(boolean buildInitial) throws Exception {
    Preconditions.checkState(state.compareAndSet(State.LATENT, State.STARTED), "Cannot be started more than once");

    // 监听client的连接状态
    // 根据连接状态修改isConnected的值
    client.getConnectionStateListenable().addListener(connectionStateListener);

    if (buildInitial) {
        client.checkExists().creatingParentContainersIfNeeded().forPath(path);
        internalRebuild();
    }

    //核心方法
    reset();
}
```
## reset
reset方法是NodeCache的核心方法
```java
private void reset() throws Exception {
    // 判断NodeCache是否已经启动，并且连接已经建立
    if ((state.get() == State.STARTED) && isConnected.get()) {
        // 获得node的Stat，并注册了一个watcher
        // watcher会在节点创建、删除、更新值的时候被触发
        // 并且异步执行exists操作，结果回掉backgroundCallback
        client.checkExists()
                .creatingParentContainersIfNeeded()
                .usingWatcher(watcher)
                .inBackground(backgroundCallback)
                .forPath(path);
    }
}
```
## watcher
NodeCache的核心。watcher实现了watcher的重新注册和监听
```java
private Watcher watcher = new Watcher() {
    @Override
    public void process(WatchedEvent event) {
        try {
            // 调用reset，重新进行监听
            reset();
        } catch (Exception e) {
            ThreadUtils.checkInterrupted(e);
            handleException(e);
        }
    }
};
```
## backgroundCallback
也是NodeCache的核心。其对于event进行处理，根据不同的event进行不同的处理
```java
private final BackgroundCallback backgroundCallback = new BackgroundCallback() {
    @Override
    public void processResult(CuratorFramework client, CuratorEvent event) throws Exception {
        //实际的处理过程在这个方法里面
        processBackgroundResult(event);
    }
};
```
```java
private void processBackgroundResult(CuratorEvent event) throws Exception {
    switch (event.getType()) {
        // 如果是 getData() 的回掉
        case GET_DATA: {
            if (event.getResultCode() == KeeperException.Code.OK.intValue()) {
                // 数据获取成功
                // 更新成员变量 data，并根据 listeners 通知 listener
                ChildData childData = new ChildData(path, event.getStat(), event.getData());
                setNewData(childData);
            }
            break;
        }

        // 如果是 checkExists() 的回掉
        case EXISTS: {
            if (event.getResultCode() == KeeperException.Code.NONODE.intValue()) {
                // 如果节点已经不存在了，则将成员变量 data 设为null
                // 并根据 listeners 通知 listener
                setNewData(null);
            } else if (event.getResultCode() == KeeperException.Code.OK.intValue()) {
                // 节点存在，获取节点数据
                // 则注册watcher，将在节点数据更新或者节点被删除的时候被触发
                // 并异步执行，回掉 backgroundCallback
                if (dataIsCompressed) {
                    client.getData().decompressed()
                            .usingWatcher(watcher)
                            .inBackground(backgroundCallback)
                            .forPath(path);
                } else {
                    client.getData().usingWatcher(watcher)
                            .inBackground(backgroundCallback)
                            .forPath(path);
                }
            }
            break;
        }
    }
}
```
## setNewData
setNewData是将数据的更新事件发送给各个listener。由于ZooKeeper的更新事件是节点数据版本有变化就会触发，所以，有更新事件并不代表节点数据变化了，NodeCache 使用了<code>Objects.equal(previousData, newData)</code> 判断了数据有没有变化，数据有变化才会触发各个listener
```java
private void setNewData(ChildData newData) throws InterruptedException {
    ChildData previousData = data.getAndSet(newData);
    // 如果数据有变化
    if (!Objects.equal(previousData, newData)) {
        // 通知各个listener
        listeners.forEach(
                new Function<NodeCacheListener, Void>() {
                    @Override
                    public Void apply(NodeCacheListener listener) {
                        try {
                            listener.nodeChanged();
                        } catch (Exception e) {
                            ThreadUtils.checkInterrupted(e);
                            log.error("Calling listener", e);
                        }
                        return null;
                    }
                }
        );

        // 下面的代码应该是测试用的代码
        // 但具体有什么用，还不清楚
        if (rebuildTestExchanger != null) {
            try {
                rebuildTestExchanger.exchange(new Object());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```
## getCurrentData
还有个getCurrentData方法，返回节点的数据。但是使用这个方法要注意，这个方法并不保证获得到的数据一定是最新的，但至少是接近最新的。因为这是多线程的情况，有可能刚刚拿到数据节点的数据就被更新了。
```java
public ChildData getCurrentData() {
    return data.get();
}
```
## 调用流程
![upload successful](/img/HNi4ZO1GnhnzvY0VNx7P.png)
这里虽然代码里面的 watcher 使用的是同一个，为了描述清楚，将通过 <code>checkExists</code> 设置的 watcher 和通过 <code>getData</code> 设置的 watcher 分开了。  
下面将执行的流程打印出来
![upload successful](/img/cKe3kdZ2iF5eQK7xVHaj.png)
# 思考
可以发现，代码里面设置了不止一次 watcher ，那为什么只会触发一次呢？  
网上说ZooKeeper里面的 watcher 是存在 HashMap 里面的（[Apache ZooKeeper Watcher 机制源码解释](https://www.ibm.com/developerworks/cn/opensource/os-cn-apache-zookeeper-watcher/index.html) watch2Paths）那么，因为使用的是同一个 watcher ，所以后面设置的会替换掉原来设置的。  

那么如果我设置了两个 watcher 那会发生什么呢？调用的过程如下：
![upload successful](/img/AXlkz9W9Jh0E35M7N5M8.png)
可以看出一个数据的修改会调用两次 <code>setNewData</code> ，原因是 <code>ExistsWatcher</code> 和 <code>DataWatcher</code> 都收到了数据修改的消息，然后都去获取数据，当然，在 <code>setNewData</code> 里面第二次查询就会发现数据没有变，就不会调用listener。同时可以看出，如果注册了两个不同 watcher ，那么都会被调用，而且是按照 watcher 注册的顺序的（这个也是在上面那篇文章说的，客户端 Watcher 回调的过程是一个串行同步的过程）