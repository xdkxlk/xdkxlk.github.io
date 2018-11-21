title: Kafka 原理总结
author: xdkxlk
tags: []
categories:
  - Kafka
date: 2018-11-20 16:27:00
---
# 生产者
## 重要配置

## 元数据请求
生产者要自己负责把消息发送到正确的broker上面。直接发送数据到主分区的服务器上，不需要经过任何中间路由。位置通过**元数据请求**获得，客户端一般会将这些信息缓存起来，并时不时的刷新元数据（通过 `metadata.max.age.ms` 配置）。如果客户端收到了“非首领”错误，那么在重新尝试发送的时候，会先刷新元数据。
# 消费者
## 重要配置
## pull & push
kafka采用的策略是：producer 把数据 push 到 broker，然后 consumer 从 broker 中 pull 数据。  
所以，消费者要不停的进行轮询  
消费者采用pull的原因：
- 当消费速率低于生产速率时，push会使consumer不堪重负（本质上类似于拒绝服务攻击）
- pull-based 系统有一个很好的特性， 那就是当 consumer 速率落后于 producer 时，可以在适当的时间赶上来
- push-based producer拿不准消费者能处理多大的数据。是一次发送一条数据呢，还是一次性发多条数据呢
- pull-based consumer 总是将所有可用的（或者达到配置的最大长度）消息 

# 消息交付语义
- At most once——消息可能会丢失但绝不重传。
- At least once——消息可以重传但绝不丢失。
- Exactly once——这正是人们想要的, 每一条消息只被传递一次。

**在kafka中分为生产者和消费者两个部分**
## 生产者
如果一个 producer 在试图发送消息的时候发生了网络故障， 则不确定网络错误发生在消息提交之前还是之后。  
0.11.0.0 之前的版本中, 如果 producer 没有收到表明消息已经被提交的响应, 那么 producer 除了将消息重传之外别无选择。 这里提供的是 `at-least-once` 的消息交付语义。  
从 0.11.0.0 版本开始，broker 给每个 producer 都分配了一个 ID ，并且 producer 给每条被发送的消息分配了一个序列号来避免产生重复的消息（`幂等性`）。（`Exactly once`）  
同时，也是从 0.11.0.0 版本开始, producer 新增了使用类似事务性的语义将消息发送到多个 topic partition 的功能，要么所有的消息都被成功的写入到了 log，要么一个都没写进去（`Exactly once`）
## 消费者
Consumer 先读取消息，然后将它的位置保存到 log 中，最后再对消息进行处理。（`at-most-once`）  
Consumer 可以先读取消息，然后处理消息，最后再保存它的位置。（`at-least-once`）在许多应用场景中，消息都设有一个主键，所以更新操作是`幂等`的（相同的消息接收两次时，第二次写入会覆盖掉第一次写入的记录）。  
### 如何实现 exactly once ?
- 当从一个 kafka topic 中消费并输出到另一个 topic  
可以使用事务型 producer
- 写入外部系统的应用场景  
解决这一问题的经典方法是在 consumer offset 的存储和 consumer 的输出结果的存储之间引入 two-phase commit。~~（这个后面再写写代码研究）~~

# Replication
创建副本的单位是 topic 的 partition ，正常情况下， 每个partition都有一个 leader 和零或多个 followers 。  
分为**首领副本(leader)** 和 **跟随者副本(followers)**  
## 首领副本
- 处理所有的读写操作
- 维护ISR列表（搞清楚哪些跟随着跟自己的数据是同步的）

## 跟随者副本
像普通的 consumer 那样从 leader 节点那里拉取消息并保存在自己的日志文件中，且这些请求是有序的。例如，先请求消息1，接着请求消息2，在收到2这个消息的请求之前，是不会发送消息3的。
## 判断节点是否存活
- 节点必须可以维护和 ZooKeeper 的连接，Zookeeper 通过心跳机制检查每个节点的连接。
- 如果节点是个 follower ，它必须能及时的同步 leader 的写操作，并且延时不能太久。

如果有节点挂掉了, 或是写超时, 或是心跳超时, leader 就会把它从同步副本列表（ISR）中移除。 同步超时和写超时的时间由 `replica.lag.time.max.ms` 配置确定。
## Quorums，ISR，首领副本（Leader）选举
### Quorum
如果选择写入时候需要保证一定数量的副本写入成功，读取时需要保证读取一定数量的副本，读取和写入之间有重叠。这样的读写机制称为 Quorum。  
这种权衡的一种常见方法是对提交决策和 leader 选举使用多数投票机制。**Kafka 没有采取这种方式**，但是我们还是要研究一下这种投票机制，来理解其中蕴含的权衡。假设我们有2f + 1个副本，如果在 leader 宣布消息提交之前必须有f+1个副本收到 该消息，并且如果我们从这至少f+1个副本之中，有着最完整的日志记录的 follower 里来选择一个新的 leader，那么在故障次数少于f的情况下，选举出的 leader 保证具有所有提交的消息。这是因为在任意f+1个副本中，至少有一个副本一定包含 了所有提交的消息。该副本的日志将是最完整的，因此将被选为新的 leader。这个算法都必须处理许多其他细节（例如精确定义怎样使日志更加完整，确保在 leader down 掉期间, 保证日志一致性或者副本服务器的副本集的改变），但是现在我们将忽略这些细节。(Zookeeper)  
**这种大多数投票方法有一个非常好的优点：**延迟是取决于最快的服务器。也就是说，如果副本数是3，则备份完成的等待时间取决于最快的 Follwer 。  
**“少数服从多数”的方式也有一些劣势**，为了保证leader选举的正常进行，它所能容忍的失败的follower数比较少，如果要容忍1个follower挂掉，那么至少要3个以上的副本，如果要容忍2个follower挂掉，必须要有5个以上的副本。在一个系统中，仅仅靠冗余来避免单点故障是不够的，但是每写5次，对磁盘空间需求是5倍， 吞吐量下降到 1/5，这对于处理海量数据问题是不切实际的。**这可能是为什么 quorum 算法更常用于共享集群配置（如 ZooKeeper ）， 而不适用于原始数据存储的原因**。
### ISR
Kafka 动态维护了一个同步状态的备份的集合 （a set of in-sync replicas）， 简称 ISR ，在这个集合中的节点都是和 leader 保持高度一致的，只有这个集合的成员才有资格被选举为 leader（`unclean.leader.election.enable=false`）。 在这种模式下，对于f+1个副本，一个Kafka topic能在保证不丢失已经commit消息的前提下容忍f个副本的失败。   
## Unclean leader 选举: 如果节点全挂了？
`unclean.leader.election.enable`默认为false  
**请注意，Kafka 对于数据不会丢失的保证，是基于至少一个节点在保持同步状态，一旦分区上的所有备份节点都挂了，就无法保证了。**  
- 等待一个 ISR 的副本重新恢复正常服务，并选择这个副本作为领 leader （它有极大可能拥有全部数据）。
- 选择第一个重新恢复正常服务的副本（不一定是 ISR 中的）作为leader。 

## 可用性和持久性保证
producer 设置 `ack`:
- `ack = 0` 不等待broker返回确认消息
- `ack = 1` leader保存成功返回
- `ack = -1(all)` 所有备份都保存成功返回

这里面要注意的是 `所有备份都保存成功返回` 并不意味着所有的副本都写入了消息。这个跟 broker 的 `min.insync.replicas` 有关，其默认为1。那么有可能ISR列表中有些副本没有写入。  
这里有一个经典的配置方法：  
```
topic 的 replication = 3
min.insync.replicas = 2
producer 的 ack = -1
```
这将确保如果大多数副本没有写入producer则抛出异常。
# 日志清理
## 几个重要的配置
```
# 下面的都是默认值
log.cleaner.enable = true 
log.cleanup.policy = delete
# 日志中脏数据清理比例
log.cleaner.min.cleanable.ratio = 0.5
# 消息在日志中保持未压缩的最短时间。 仅适用于正在压缩的日志
log.cleaner.min.compaction.lag.ms = 0
# 墓碑的保留时间，默认24小时
log.cleaner.delete.retention.ms = 86400000
```
## 清理策略
压缩策略由 `log.cleanup.policy` 指定。  
默认的 `delete` 就是单纯的删除。  
还有一个策略是 `compact`，下面讲下 `compact`  
![upload successful](/img/VcgYvQYp9oxafJ8W2iIk.png)
日志分为两个部分，log tail是已经压缩了的日志，log head是还没有压缩的日志。对于上面的这个，36、37、38都是指的是38这个位置。  
`compact` 其实就是保留最新的 k-v 键值对，示意图如下
![upload successful](/img/oHk32SnCWrIETyNMXUot.png)
如果我们要删除一个数据，那么要经过下面几个过程：
- 程序发送包含该键且值为null的消息
- 进行常规清理，只保留为null的消息（墓碑消息）
- 墓碑消息会保留一段时间
- 假如消费者在通过kafka进行数据处理的时候，发现key为null，就应该知道，这个数据被删除了
- 到时间之后，移除墓碑消息

**清理线程会选择污浊率较高的分区进行清理**（log head占总分区的比例）。`log.cleaner.min.compaction.lag.ms` 默认为0，所以默认不会清理最后一个活动的segment。故，可以通过配置 `log.cleaner.min.compaction.lag.ms`，保证消息在配置的时长内不被压缩。**活动的 segment 是不会被压缩的，即使它保存的消息的滞留时长已经超过了配置的最小压缩时间长。**

# 参考 
[kafka1.0 中文文档](http://kafka.apachecn.org/documentation.html)  
[kafka数据可靠性深度解读](https://blog.csdn.net/u013256816/article/details/71091774)  
[Kafka设计解析（八）- Kafka事务机制与Exactly Once语义实现原理](https://www.zybuluo.com/tinadu/note/949867)  
[Kafka日志清理之Log Compaction](https://blog.csdn.net/u013256816/article/details/80487758)