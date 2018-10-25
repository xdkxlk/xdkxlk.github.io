title: Curator NodeCache学习
author: xdkxlk
tags:
  - curator
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

    client.getConnectionStateListenable().addListener(connectionStateListener);

    if ( buildInitial ) {
      client.checkExists().creatingParentContainersIfNeeded().forPath(path);
      internalRebuild();
    }
    reset();
  }
```
```flow
st=>start: 开始
e=>end: 结束
op=>operation: 我的操作
cond=>condition: 确认？

st->op->cond
cond(yes)->e
cond(no)->op
```