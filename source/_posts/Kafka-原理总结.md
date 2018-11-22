title: Kafka 原理总结
author: xdkxlk
tags: []
categories:
  - Kafka
date: 2018-11-20 16:27:00
---
# 整体架构
![upload successful](/img/F177wfgo2UQ4d8n1mb92.png)
## 名词解释
- Broker  
消息中间件处理节点，一个Kafka节点就是一个broker，一个或者多个Broker可以组成一个Kafka集群
- Controller  
控制器其实就是一个broker，除了具有一般的broker的功能之外，还负责分区首领的选举
- Topic  
Kafka根据topic对消息进行归类，发布到Kafka集群的每条消息都需要指定一个topic
- Producer  
消息生产者，向Broker发送消息的客户端
- Consumer  
消息消费者，从Broker读取消息的客户端
- ConsumerGroup  
每个Consumer属于一个特定的Consumer Group，一条消息可以发送到多个不同的Consumer Group，但是一个Consumer Group中只能有一个Consumer能够消费该消息
- Partition  
物理上的概念，一个topic可以分为多个partition，每个partition内部是有序的
- Segment  
在物理上，一个partition的保存是由很多个segment文件组成的

## 保证
- 生产者发送到**特定topic partition** 的消息将按照发送的顺序处理。 也就是说，如果记录M1和记录M2由相同的生产者发送（**成功发送到了broker**），并先发送M1记录，那么M1的偏移比M2小，并在日志中较早出现（但这也意味着，如果有多个partition，这多个partiton之间的消息的顺序并不保证，**而且实际情况下，由于可能出现的网络延迟和重试机制，需要一定的配置才能保证顺序**）
- 一个消费者实例按照日志中的顺序查看记录
- 对于具有N个副本的主题，我们最多容忍N-1个服务器故障，从而保证不会丢失任何提交到日志中的记录（ISR）

## Topic & Partition
![upload successful](/img/04aEjfXXGRKbPbSCF8Ij.png)
对于每一个topic， Kafka集群都会维持一个分区日志，每个分区都是有序且顺序不可变的记录集，并且不断地追加到结构化的commit log文件。追加是直接追加到partition的尾部，顺序写入，所以，写入的速度是很快的。  
每一条消息被发送到broker中，会根据partition规则选择被存储到哪一个partition。如果partition规则设置的合理，所有消息可以均匀分布到不同的partition里，这样就实现了**水平扩展**。（如果一个topic对应一个文件，那这个文件所在的机器I/O将会成为这个topic的性能瓶颈，而partition解决了这个问题）  
默认的partition的数目在 `server.properties` 配置，默认为1
```
# The default number of log partitions per topic. More partitions allow greater
# parallelism for consumption, but this will also result in more files across
# the brokers.
num.partitions=1
```

# 生产者
## 重要配置

## 元数据请求
生产者要自己负责把消息发送到正确的broker上面。直接发送数据到主分区的服务器上，不需要经过任何中间路由。位置通过**元数据请求**获得，客户端一般会将这些信息缓存起来，并时不时的刷新元数据（通过 `metadata.max.age.ms` 配置）。如果客户端收到了“非首领”错误，那么在重新尝试发送的时候，会先刷新元数据。
## 顺序保证
kafka支持消息的重试，由 producer的 `retries` 控制，默认为0。若设置大于0的值，则客户端会将发送失败的记录重新发送，尽管这些记录有可能是暂时性的错误。**请注意，这种 retry 与客户端收到错误信息之后重新发送记录并无区别。**  
还有一个配置是 `max.in.flight.requests.per.connection` 默认为5。代表在发生阻塞之前，客户端的一个连接上允许出现未确认请求的最大数量。  
所以，如果在某些场景下，要求消息是有序的，那么这种场景下消息是否写入成功也很关键。所以，`retries` 不建议设为0。又由于重试的原因，`max.in.flight.requests.per.connection` 应该设为1，保证不会在重试的时候，后面的消息先发送成功了。**但是这样的配置会严重影响生产者的吞吐量**  
最终配置
```
retries = 3 #设为大于0的数字
max.in.flight.requests.per.connection = 1
```
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

## 分区和消费者
在一个群组中，一个分区最多对应一个消费者，所以如果同一个群组中的消费者大于分区的数量，那么就会有消费者处于闲置状态，所以，**不要让消费者数量超过主题分区的数量**
## 再均衡
分区的所有权由一个消费者转移到另一个消费者，这样的被称为再均衡。再均衡期间，整个群组会有一小段时间不可用，同时，分区重新分配给另一个消费者之后，消费者当前的读取状态会丢失（要做到安全的再均衡，offset提交）
### 触发条件
- 组成员发生变更(新consumer加入组、已有consumer主动离开组或已有consumer崩溃了——这两者的区别后面会谈到)
- 订阅主题数发生变更——这当然是可能的，如果你使用了正则表达式的方式进行订阅，那么新建匹配正则表达式的topic就会触发rebalance
- 订阅主题的分区数发生变更

### 加入过程
消费者想群组协调器（它其实就是一个broker）发送 `JoinGroup` 请求，第一个加入群组的消费者成为“群主”。群主从协调器那儿获得成员的列表，并负责给每一个消费者分配分区。  
kafka将分区的分配的权利下放给消费者，提高了扩展性。
### 分配策略
由 `partition.assignment.strategy` 指定，默认是 `range`  还有一个 `roundRobin` 策略。  
- range  
C1、C2订阅T1、T2，每个主题又3个分区。那么C1可能被分为T1、T2的0、1两个分区，C2分为T1、T2的2分区
- roundRobin  
这个就是把主题的分区逐个分配给消费者，各个消费者的分区数量都会差不多（最多差一个分区）

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
**但是如要实现幂等性和事务还需要其他的配置**
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
**而选择哪个作为新的leader是由controller来决定的**
## 复制原理和同步方式
对于每个partition，有HW，和 LEO
![upload successful](/img/siSKSfk2OXigsdkkbbV0.png)
LEO，LogEndOffset的缩写，表示每个partition的log最后一条Message的位置。HW是HighWatermark的缩写，是指consumer能够看到的此partition的位置。    
对于leader新写入的消息，consumer不能立刻消费，leader会等待该消息被所有ISR中的replicas同步后更新HW，此时消息才能被consumer消费。这样就保证了如果leader所在的broker失效，该消息仍然可以从新选举的leader中获取。对于来自内部broker的读取请求，没有HW的限制。
![upload successful](/img/rOwA62vm5zO1bdYZ8x0w.png)
由此可见，Kafka的复制机制既不是完全的同步复制，也不是单纯的异步复制。事实上，同步复制要求所有能工作的follower都复制完，这条消息才会被commit，这种复制方式极大的影响了吞吐率。而异步复制方式下，follower异步的从leader复制数据，数据只要被leader写入log就被认为已经commit，这种情况下如果follower都还没有复制完，落后于leader时，突然leader宕机，则会丢失数据。而Kafka的这种使用ISR的方式则很好的均衡了确保数据不丢失以及吞吐率。
## Unclean leader 选举: 如果节点全挂了？
`unclean.leader.election.enable`默认为false  
**请注意，Kafka 对于数据不会丢失的保证，是基于至少一个节点在保持同步状态，一旦分区上的所有备份节点都挂了，就无法保证了。**  
- 等待一个 ISR 的副本重新恢复正常服务，并选择这个副本作为领 leader （它有极大可能拥有全部数据）。
- 选择第一个重新恢复正常服务的副本（不一定是 ISR 中的）作为leader。 

## 分区分配
- kafka要尽量保证在broker之间平均的分配副本
- 确保每个分区的副本在不同的broker上
- 如果为broker指定了机架，那么尽量把每一个分区副本放在不同的机架上

![upload successful](/img/q7p04qULlCcCCcu7kQIx.png)
现有6个broker，打算创建一个包含10个分区的主题，且复制系数为3，那么kafka就有30个分区副本。  
### 没有配置机架的情况
- 先随机选择一个broker(4)
- 使用轮询方式给每个broker分配分区来确定首领分区的位置  
首领分区0 -> broker4，首领分区1 -> broker5，首领分区2 -> broker0，等等
- 从首领分区开始，依次分配跟随着副本。  
分区0首领在broker4上，那么follower0-0在broker5，follower0-1在broker0，等等。

### 配置了机架的情况
假设 broker0、1、2在同一个机架上，broker3、4、5在同一个机架上。也是用轮序的方式分配，只不过轮序的顺序是按照机架交错的，0、3、1、4、2、5
# 控制器
控制器其实就是一个broker，只不过在一般broker之外，还有分区首领选择的功能。  
## controller的选择
利用zookeeper，创建一个 /controller 的临时节点让自己成为控制器。如果挂掉了，那么其他broker会收到通知，又再一次创建 /controller 节点，竞争成为新的控制器
## 脑裂
通过 `controller_epoch` 解决。  
broker收到了较旧的 epoch，就会忽略它们
## broker离开
当发现有broker离开集群，那么控制器就会找出失去了首领的那些分区，并遍历这些分区，确定新的首领副本，发送首领变更的请求
## broker加入
当新的broker加入的时候，控制器检查新加入的broker是否有现有分区的副本，如果有，就通知其他broker,并且新加入的broker开始复制消息
# 可靠性和持久性保证
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
这将确保如果大多数副本没有写入producer则抛出异常（NotEnoughReplicasExceptoin）。
## 高可靠性配置
- topic的配置：replication.factor>=3, 即副本数至少是3个；2<=min.insync.replicas<=replication.factor
- broker的配置：leader的选举条件 unclean.leader.election.enable=false
- producer的配置：request.required.acks=-1(all)

## acks=1
producer发送数据到leader，leader写本地日志成功，返回客户端成功；此时ISR中的副本还没有来得及拉取该消息，leader就宕机了，那么此次发送的消息就会丢失。
![upload successful](/img/PR93bnGMlE2Uwf4mydnJ.png)
## acks=-1
replication.factor>=2 且 min.insync.replicas>=2的情况下，不会丢失数据
![upload successful](/img/Bqw6z5trOBOrP5u8K6R2.png)

![upload successful](/img/Zx4cGzpeOYzvTMnk51tG.png)
## HW的进一步探讨
![upload successful](/img/j10By0LNRPButTzXPdMh.png)
如上图，某个topic的某partition有三个副本，分别为A、B、C。A作为leader肯定是LEO最高，B紧随其后，C机器由于配置比较低，网络比较差，故而同步最慢。这个时候A机器宕机，这时候如果B成为leader，假如没有HW，在A重新恢复之后会做同步(makeFollower)操作，在宕机时log文件之后直接做追加操作，而假如B的LEO已经达到了A的LEO，会产生数据不一致的情况，所以使用HW来避免这种情况。     
**A在做同步操作的时候，先将log文件截断到之前自己的HW的位置，即3，之后再从B中拉取消息进行同步。**  
如果失败的follower恢复过来，它首先将自己的log文件截断到上次checkpointed时刻的HW的位置，之后再从leader中同步消息。leader挂掉会重新选举，新的leader会发送“指令”让其余的follower截断至自身的HW的位置然后再拉取新的消息。
# 文件存储
~~个人觉得Kafka官方文档没有更新到最新的版本。因为它说存储的文件是 .kafka 结尾的，然而，我实际上看见的文件格式是 .index .log~~  
物理上一个topic由多个partition组成（如果设置了多个partition），一个partition就是一个文件夹，partition的名称规则为：topic名称+有序序号，第一个序号从0开始计。partition是实际物理上的概念，而topic是逻辑上的概念。  
而partition又是以segment存储的，segment是一个个小的文件。
## 相关配置
```
# 单个日志段文件最大大小
log.segment.bytes = 1073741824
# 从文件系统中删除一个日志段文件前的保留时间
log.segment.delete.delay.ms = 60000

# 新日志段轮转时间间隔
log.roll.hours = 168
log.roll.ms = null
```
## segment存储
segment文件由两部分组成，分别为 “.index” 文件和 “.log” 文件，分别表示为segment索引文件和数据文件。这两个文件的命令规则为：partition全局的第一个segment从0开始，后续每个segment文件名为上一个segment文件最后一条消息的offset值，数值大小为64位，20位数字字符长度，没有数字用0填充，如下：
```
00000000000000000000.index
00000000000000000000.log
00000000000000170410.index
00000000000000170410.log
00000000000000239430.index
00000000000000239430.log
```
他们之间的关系如下
![upload successful](/img/fZi2uULN2c3j4WmwrxWq.png)
如上图，“.index” 索引文件存储大量的元数据，“.log” 数据文件存储大量的消息，索引文件中的元数据指向对应数据文件中message的物理偏移地址。其中以“.index”索引文件中的元数据 \[3, 348\] 为例，在“.log”数据文件表示第3个消息，即在全局partition中表示 170410+3=170413 个消息，该消息的物理偏移地址为348。  
那么如何从partition中通过offset查找message呢？   
以上图为例，读取offset=170418的消息
- 首先查找segment文件，其中00000000000000000000.index为最开始的文件，第二个文件为00000000000000170410.index（起始偏移为170410+1=170411），而第三个文件为00000000000000239430.index（起始偏移为239430+1=239431），所以这个offset=170418就落到了第二个文件之中。其他后续文件可以依次类推，以其实偏移量命名并排列这些文件
- 然后根据二分查找法就可以快速定位到具体文件位置。其次根据00000000000000170410.index文件中的 \[8,1325\] 定位到00000000000000170410.log文件中的1325的位置进行读取。

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
```
## 清理策略
压缩策略由 `log.cleanup.policy` 指定。清理策略分为 `delete`和 `compact`
### delete
 `delete` 就是单纯的删除。是默认的策略，跟它相关的配置有
```
log.retention.bytes 默认 -1
log.retention.ms / log.retention.hours / log.retention.minutes

# 从文件系统中删除一个日志段文件前的保留时间
log.segment.delete.delay.ms = 60000

# 日志清理器检查是否有日志符合删除的频率
log.retention.check.interval.ms = 300000
```
删除的时候，会先将segment标记为 .delete ，然后在 `log.segment.delete.delay.ms` 之后，由另外一个定时器线程删除

### compact
`compact` 是对于数据进行压缩清理
![upload successful](/img/VcgYvQYp9oxafJ8W2iIk.png)
日志分为两个部分，log tail是已经压缩了的日志，log head是还没有压缩的日志。对于上面的这个，36、37、38都是指的是38这个位置。  
`compact` 其实就是保留最新的 k-v 键值对，示意图如下
![upload successful](/img/oHk32SnCWrIETyNMXUot.png)
如果我们要删除一个数据，那么要经过下面几个过程：
- 程序发送包含该键且值为null的消息
- 进行常规清理，只保留为null的消息（墓碑消息，tomestone）
- 墓碑消息会保留一段时间
- 假如消费者在通过kafka进行数据处理的时候，发现key为null，就应该知道，这个数据被删除了
- 到时间之后，移除墓碑消息

墓碑移除的时间由 `log.cleaner.delete.retention.ms` 控制，默认24小时  
清理的时候，**清理线程会选择污浊率较高的分区进行清理**（log head占总分区的比例）。`log.cleaner.min.compaction.lag.ms` 默认为0，所以默认不会清理最后一个活动的segment。故，可以通过配置 `log.cleaner.min.compaction.lag.ms`，保证消息在配置的时长内不被压缩。**活动的 segment 是不会被压缩的，即使它保存的消息的滞留时长已经超过了配置的最小压缩时间长。**扫描的频率由 `log.cleaner.backoff.ms` 控制
# 一个生产环境服务器配置示例
```properties
# ZooKeeper
zookeeper.connect=[list of ZooKeeper servers]
 
# Log configuration
num.partitions=8
default.replication.factor=3
log.dir=[List of directories. Kafka should have its own dedicated disk(s) or SSD(s).]
 
# Other configurations
broker.id=[An integer. Start with 0 and increment by 1 for each new broker.]
listeners=[list of listeners]
auto.create.topics.enable=false
min.insync.replicas=2
queued.max.requests=[number of concurrent requests]
```

# 参考 
[kafka1.0 中文文档](http://kafka.apachecn.org/documentation.html)  
[kafka数据可靠性深度解读](https://blog.csdn.net/u013256816/article/details/71091774)  
[Kafka设计解析（八）- Kafka事务机制与Exactly Once语义实现原理](https://www.zybuluo.com/tinadu/note/949867)  
[Kafka日志清理之Log Compaction](https://blog.csdn.net/u013256816/article/details/80487758)  
[Kafka配置消息保存时间的方法](https://my.oschina.net/abcfy2/blog/634973)  
[Kafka日志删除源码分析](https://www.iteblog.com/archives/1616.html)  
[Kafka源码分析-Content Table](https://www.jianshu.com/p/aa274f8fe00f)  
[Kafka消费组(consumer group)](http://www.cnblogs.com/huxi2b/p/6223228.html)  
[Matt's Blog (挺厉害的，对于kafka理解很深)](http://matt33.com/)