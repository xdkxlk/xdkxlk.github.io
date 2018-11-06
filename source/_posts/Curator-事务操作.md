title: Curator 事务操作
author: xdkxlk
tags:
  - Curator
categories:
  - ZooKeeper
date: 2018-11-06 16:11:00
---
网上很多Curator事务操作使用的是`client.inTransaction()`，但现在已经废弃了。我使用的Curator版本为`4.0.1`
# 使用方法
```scala
object Transaction extends App {

  val retryPolicy = new ExponentialBackoffRetry(1000, 3)
  val client = CuratorFrameworkFactory.builder()
    .connectString(ZOOKEEPER_HOST)
    .sessionTimeoutMs(5000)
    .connectionTimeoutMs(1000)
    .retryPolicy(retryPolicy)
    .build()
  client.start()

  val transaction = client.transaction()
  val option = new util.ArrayList[CuratorOp]()
  for (i <- 1 to 3) {
    option.add(client.transactionOp().create().forPath(s"/path$i"))
  }
  try {
    val result = transaction.forOperations(option)
    result.forEach(r =>
      println(s"{type: ${r.getType}, forPath: ${r.getForPath}," +
        s" resultPath: ${r.getResultPath}, resultStat: ${r.getResultStat}}"))
  } catch {
    case NonFatal(e) => System.err.print(e.getMessage)
  }
}
```
```
{type: CREATE, forPath: /path1, resultPath: /path1, resultStat: null}
{type: CREATE, forPath: /path2, resultPath: /path2, resultStat: null}
{type: CREATE, forPath: /path3, resultPath: /path3, resultStat: null}
```
# 存在问题
`TransactionOp`的`delete`没有`deletingChildrenIfNeeded`，个人觉得很不方便，不知道有什么办法处理这个问题