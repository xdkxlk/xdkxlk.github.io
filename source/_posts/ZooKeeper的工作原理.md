title: ZooKeeper的工作原理
author: xdkxlk
tags: []
categories:
  - ZooKeeper
date: 2018-11-05 10:28:00
---
# 角色划分
## leader
- 事务请求的唯一调度者和处理者，保证集群事务处理的顺序性
- 集群内部各服务器的调度者

## learner
### follower
- 处理客户端非事务请求，转发事务请求给leader。意味着，如果客户端要读取数据，那么就直接从follower本地拿，速度快。**如果客户端要写数据，那么会将这个请求转发给leader，由leader进行统一的处理。**
- 参与事务请求proposal的投票
- 参与leader选举的投票（可能会成为下一个leader）

### observer 
- 对于客户端请求的处理和follower一样。处理客户端非事务请求，转发事务请求给leader。
- 不参与事务请求proposal的投票（仅仅同步leader的状态）
- 不参与leader的选举投票（不可能成为leader）
- [ZooKeeper增加Observer部署模式提高性能](https://www.cnblogs.com/EasonJim/p/7488484.html)

#### 为什么要有observer
observer是为了系统的扩展性而存在的，可以提高系统读取的效率。  
一句话：在不伤害写性能的情况下扩展Zookeeper  

尽管通过Client直接连接到Zookeeper集群的性能已经非常好了，但是这种架构如果要承受超大规模的Client，就必须增加Zookeeper集群的Server数量，随着Server的增加，Zookeeper集群的写性能必定下降，我们知道Zookeeper的Znode变更是要过半数投票通过，随着机器的增加，由于网络消耗等原因必然导致投票成本增加，从而导致写性能的下降。  
Observer是一种新型的Zookeeper节点，可以帮助解决上述问题，提供Zookeeper的可扩展性。Observer不参与投票，只是简单的接收投票结果，因此我们增加再多的Observer，也不会影响集群的写性能。除了这个差别，其他的和Follower基本上完全一样。例如：Client都可以连接到他们，并且都可以发送读写请求给他们，收到写请求都会上报到Leader。  
Observer有另外一个优势，因为它不参与投票，所以他们不属于Zookeeper集群的关键部位，即使他们Failed，或者从集群中断开，也不会影响集群的可用性。根据Observer的特点，我们可以使用Observer做跨数据中心部署。如果把Leader和Follower分散到多个数据中心的话，因为数据中心之间的网络的延迟，势必会导致集群性能的大幅度下降。使用Observer的话，将Observer跨机房部署，而Leader和Follower部署在单独的数据中心，这样更新操作会在同一个数据中心来处理，并将数据发送的其他数据中心（包含Observer的），然后Client就可以在其他数据中心查询数据了。但是使用了Observer并非就能完全消除数据中心之间的延迟，因为Observer还得接收Leader的同步结果合Observer有更新请求也必须转发到Leader，所以在网络延迟很大的情况下还是会有影响的，它的优势就为了本地读请求的快速响应。
![upload successful](/img/nD2oJaUovfDeX69Oe0Kd.png)
## client
就是客户端，是请求的发起方
# 设计目的
- 最终一致性：client不论连接到哪个Server，展示给它都是同一个视图，这是zookeeper最重要的性能。
- 可靠性：具有简单、健壮、良好的性能，如果消息m被到一台服务器接受，那么它将被所有的服务器接受。
- 实时性：Zookeeper保证客户端将在一个时间间隔范围内获得服务器的更新信息，或者服务器失效的信息。但由于网络延时等原因，Zookeeper不能保证两个客户端能同时得到刚更新的数据，如果需要最新数据，应该在读数据之前调用sync()接口。
- 等待无关（wait-free）：慢的或者失效的client不得干预快速的client的请求，使得每个client都能有效的等待。
- 原子性：更新只能成功或者失败，没有中间状态。
- 顺序性：包括全局有序和偏序两种：全局有序是指如果在一台服务器上消息a在消息b前发布，则在所有Server上消息a都将在消息b前被发布；偏序是指如果一个消息b在消息a后被同一个发送者发布，a必将排在b前面。

# 工作原理
ZooKeeper的核心是ZAB协议（ZooKeeper Atomic Broadcase），并没有完全采用Paxos。ZAB协议有两个基本模式，`崩溃恢复`和`消息广播`。  
## 服务器状态
- LOOKING：寻找leader状态。说明集群中没有leader，因此需要进入leader选举流程
- FOLLOWING：表明当前服务器角色是follower
- LEADING：表明当前服务器角色是leader
- OBSERVING：表明当前服务器角色是observer

## 术语、数据
### SID
SID是一个数字，用来唯一标识一台ZooKeeper集群中的机器，每台机器不能重复，等于myid。myid配置在`zoo.cfg`配置文件的`dataDir`所值的目录下的`myid`文件中
### ZXID
ZXID事务ID，用来唯一标识服务器状态的变更。ZXID是一个64位的数字，其中低32位可以看成是一个简单的单调计数器，针对客户端的每一个事务请求，leader在产生新的proposal的时候，都会对这个计数器进行加一；高32位代表了leader周期的epoch编号，用来标识leader关系是否改变，每次一个leader被选出来，它都会有一个 新的epoch，标识当前属于那个leader的统治时期。  
使用ZXID大大简化了数据恢复的流程。
### Quorum
过半数机器数。ZooKeeper有一个过半数的机制，ZooKeeper中的Quorum有两个作用：
- 集群中最少要Quorum个节点用来选举Leader，保证集群可用
- 通知客户端数据已经安全保存前集群中最少有Quorum个节点已经保存了该数据。一旦这些节点保存了该数据，客户端将被通知已经安全保存了，可以继续其他任务。而集群中剩余的节点将会最终也保存了该数据

而且很多人都说，ZooKeeper的节点数最好是奇数，原因如下：
- 容错  
从成本上来说，使用奇数个节点是最划算的。

```
2台服务器，至少2台正常运行才行（2的半数为1，半数以上最少为2），正常运行1台服务器都不允许挂掉  
3台服务器，至少2台正常运行才行（3的半数为1.5，半数以上最少为2），正常运行可以允许1台服务器挂掉  
4台服务器，至少3台正常运行才行（4的半数为2，半数以上最少为3），正常运行可以允许1台服务器挂掉  
5台服务器，至少3台正常运行才行（5的半数为2.5，半数以上最少为3），正常运行可以允许2台服务器挂掉  
6台服务器，至少3台正常运行才行（6的半数为3，半数以上最少为4），正常运行可以允许2台服务器挂掉  
```
- 防止脑裂（Split-Brain）  
这个是个人觉得是**最重要的作用**。  
Split-Brain问题说的是1个集群如果发生了网络故障，很可能出现1个集群分成了两部分，而这两个部分都不知道对方是否存活，不知道到底是网络问题还是直接机器down了，所以这两部分都要选举1个Leader，而一旦两部分都选出了Leader, 并且网络又恢复了，那么就会出现两个Brain的情况，整个集群的行为就不一致了。  
**这样的方式可以确保leader的唯一性，要么选出唯一的一个leader，要么选举失败。**但是很明显，如果是偶数个节点数，会出现集群不可用的情况。例如

```
一个集群3台服务器，全部运行正常，但是其中1台裂开了，和另外2台无法通讯。3台机器里面2台正常运行过半票可以选出一个leader。  
一个集群4台服务器，全部运行正常，但是其中2台裂开了，和另外2台无法通讯。4台机器里面2台正常工作没有过半票以上达到3，无法选出leader正常运行。  
一个集群5台服务器，全部运行正常，但是其中2台裂开了，和另外3台无法通讯。5台机器里面3台正常运行过半票可以选出一个leader。  
一个集群6台服务器，全部运行正常，但是其中3台裂开了，和另外3台无法通讯。6台机器里面3台正常工作没有过半票以上达到4，无法选出leader正常运行。
```

## 崩溃恢复
### leader选举
发生的时机：  
- 服务器初始化启动
- 服务器运行期间无法同leader保持连接。leader和follower之间都通过心跳检测机制来感知彼此，如果在指定的超时时间内leader无法从过半的follower进程那里接收到心跳，或者TCP连接断开了，那么leader就会终止对当前周期的领导，转换为LOOKING状态。follower也会放弃这个leader，转换为LOOKING状态。

当一台机器进入leader选举的时候，集群可能存在两种状态：
- 已经存在一个leader
- 确实不存在leader

#### 已经存在一个leader
启动的时候，试图去选举leader的时候，会被告知已经存在leader，于是仅仅只需要和leader机器建立连接，同步状态即可。
#### 确实不存在leader
下面是一个简版的例子，实际的流程要复杂很多  
现有SID分别为1、2、3、4、5的机器，ZXID分别为9、9、9、8、8，并且此时SID为2的机器是leader。某一个时刻，1、2所在机器出现故障，于是开始leader选举。选票的格式表示为`(SID, ZXID)`（下面的例子是一个简化版本的，实际上）
- 第一次投票，每台机器都投自己，（3，9），（4，8），（5，8）
- 对于server3，收到（4，8），（5，8），自己的是（3，9），自己的ZXID大于接收到的，不变更投票
- 对于server4，收到（3，9），（5，8），自己的是（4，8），（3，9）的ZXID大于自己的，变更投票，将（3，9）发送给其他机器
- 对于server5，收到（3，9），（4，8），同理，变更投票，将（3，9）发送给其他机器
- 由于server3有超过半数的投票，确定server3为新的leader

通常，哪台服务器数据越新，越有可能成为leader，因为它的ZXID更大。
#### 流程图
<img src="/img/BMk9Ki290k0ttKXPiSpK.png" width="60%" />
这里面有两个关键的pk判断
1. 判断选举轮次
	- 外部投票的选举轮次大于自己的  
    那么就清空已经收到的投票，更新自己的`logicalclock`，使用初始化的投票来判断是否需要更新内部投票，最终再将结果发送出去
    - 外部投票的选举轮次小于自己的
    直接忽略，等待接收下一个外部投票（回到图上的`接受外部投票`）
    - 外部投票的选举轮次等于自己的
    开始选票pk
2. 选票pk
	- 外部投票中推举的leader服务器的选举轮次大于自己的，那么变更投票
    - 选举轮次一致，比较ZXID，ZXID大的获胜
    - ZXID一致，比较SID，SID大的获胜
    
注意，当统计后发现过半数认可了当前选票之后，并不会立即更新服务器状态，而是会等待一段时间（默认200毫秒）来确定是否有新的更优的投票
#### 源码执行流程
对应代码 `org.apache.zookeeper.server.quorum.FastLeaderElection.lookForLeader`  
[注释版本FastLeaderElection.java](/file/2018_11_05_ZooKeeper/FastLeaderElection.java)  
![upload successful](/img/XOlCHwieguL90O93yGBN.png)
#### FastLeaderElection与QuorumCnxManager关系
选leader时消息的收发依赖QuorumCnxManager,它作为server之间的IO管理器
![upload successful](/img/xioCjC3FNXSxBJT30e2h.png)
#### 两个vote的全序大小比较规则总结
依次根据peerEpoch，zxid，sid来（`totalOrderPredicate`）
- peerEpoch代表所处周期，越大则投票越新
- peerEpoch相同时，zxid代表一个周期中的事务记录，越大则投票越新
- peerEpoch，zxid均相同时，sid大的赢（两个投票一样新，只是为了决定leader需要有大小关系）

#### 选举投票leader的验证问题
- 如果消息发送方state是looking，则termPredicate看是否过半即可
- 如果消息发送方state是following或者leading，则ooePredicate看是否过半，且leader机器发出ack知道了自己是leader即可

#### 集群中是否所有机器是否都是网络互通
三台机器ABC，AB网络不通  
但是A，B，C投票都给C  
C收到三张票，过半，自己成为leader  
B知道C得到了两张票，分别是BC投给C（B不知道A投给了C），也过半，自己成为follower  
同理，A也成为follower
#### 是否会出现looking机器和leader机器网络不通，但收了过半的leader投票，因此认定了leader的合理性
```
假设5台机器ABCDE，ABCD已经形成集群，以D为leader
这时E以looking状态进来，收到了ABC以following状态的投票，这时就过半了
E会不会把D当成leader
```
**这就是checkLeader函数的意义**里面会有检查
```java
if(leader != self.getId()){// 自己不为leader
    if(votes.get(leader) == null) predicate = false;// 投票记录中，没有来自leader的投票
    else if(votes.get(leader).getState() != ServerState.LEADING) predicate = false;//leader不知道自己是leader
} else if(logicalclock != electionEpoch) {// 如果大家认为我是leader，但是逻辑时钟不等于选举周期
    predicate = false;
}
```
如果网络不通，那么就会votes.get(leader) == null，因此E不会把D当成leader
#### 竞选leader是"广播"吗？
选举leader不是广播，后续一致性同步才是广播。  
这里就是所有server互相通信完成的
### 数据同步
一旦leader选举完成，就开始进入恢复阶段，就是follower要同步leader上的数据信息  
这里面有几个数据：  
- long lastProcessedZxid  
最后一次commit的事务请求的zxid
- LinkedList committedLog、long maxCommittedLog、long minCommittedLog  
ZooKeeper会保存最近一段时间内执行的事务请求议案，个数限制默认为500个议案。上述committedLog就是用来保存议案的列表，上述maxCommittedLog表示最大议案的zxid，minCommittedLog表示committedLog中最小议案的zxid。

#### 通信初始化  
leader会创建一个ServerSocket，接收follower的连接，leader会为每一个连接会用一个LearnerHandler线程来进行服务
#### 重新为peerEpoch选举出一个新的peerEpoch  
follower会向leader发送一个Leader.FOLLOWERINFO信息，包含自己的peerEpoch信息  
leader的LearnerHandler会获取到上述peerEpoch信息，leader从中选出一个最大的peerEpoch，然后加1作为新的peerEpoch。  
然后leader的所有LearnerHandler会向各自的follower发送一个Leader.LEADERINFO信息，包含上述新的peerEpoch  
follower会使用上述peerEpoch来更新自己的peerEpoch，同时将自己的lastProcessedZxid发给leader  
leader的所有LearnerHandler会记录上述各自follower的lastProcessedZxid，然后根据这个lastProcessedZxid和leader的lastProcessedZxid之间的差异进行同步  
#### 已经处理的事务议案的同步  
判断LearnerHandler中的lastProcessedZxid是否在minCommittedLog和maxCommittedLog之间  
 - LearnerHandler中的lastProcessedZxid和leader的lastProcessedZxid一致，则说明已经保持同步了  
 - 如果lastProcessedZxid在minCommittedLog和maxCommittedLog之间  
从lastProcessedZxid开始到maxCommittedLog结束的这部分议案，重新发送给该LearnerHandler对应的follower，同时发送对应议案的commit命令 。 就是说leader会发送一堆的连续的`PROPOSAL`，`COMMIT`的消息  
上述可能存在一个问题：即lastProcessedZxid虽然在他们之间，但是并没有找到lastProcessedZxid对应的议案，即这个zxid是leader所没有的，此时的策略就是完全按照leader来同步，删除该follower这一部分的事务日志，然后重新发送这一部分的议案，并提交这些议案  
 - 如果lastProcessedZxid大于maxCommittedLog  
 则删除该follower大于部分的事务日志
 - 如果lastProcessedZxid小于minCommittedLog  
 则直接采用快照的方式来恢复  
 
#### 未处理的事务议案的同步  
LearnerHandler还会从leader的toBeApplied数据中将大于该LearnerHandler中的lastProcessedZxid的议案进行发送和提交（toBeApplied是已经被确认为提交的）  
LearnerHandler还会从leader的outstandingProposals中大于该LearnerHandler中的lastProcessedZxid的议案进行发送，但是不提交（outstandingProposals是还没被被确认为提交的）
#### 将LearnerHandler加入到正式follower列表中  
意味着该LearnerHandler正式接受请求。即此时leader可能正在处理客户端请求，leader针对该请求发出一个议案，然后对该正式follower列表才会进行执行发送工作。这里有一个地方就是：  
上述我们在比较lastProcessedZxid和minCommittedLog和maxCommittedLog差异的时候，必须要获取leader内存数据的读锁，即在此期间不能执行修改操作，当欠缺的数据包已经补上之后（先放置在一个队列中，异步发送），才能加入到正式的follower列表，否则就会出现顺序错乱的问题  
同时也说明了，一旦一个follower在和leader进行同步的过程（这个同步过程仅仅是确认要发送的议案，先放置到队列中即可等待异步发送，并不是说必须要发送过去），该leader是暂时阻塞一切写操作的。  
对于快照方式的同步，则是直接同步写入的，写入期间对数据的改动会放在上述队列中的，然后当同步写入完成之后，再启动对该队列的异步写入。
**上述的要理解的关键点就是：既要不能漏掉，又要保证顺序**  

#### LearnerHandler发送Leader.NEWLEADER以及Leader.UPTODATE命令  
该命令是在同步结束之后发的。对于`DIFF`类型的，会在一堆`PROPOSAL`，`COMMIT`之后发送`NEWLEADER`。follower收到该命令之后会执行一次版本快照等初始化操作，如果收到该命令的ACK则说明follower都已经完成同步了并完成了初始化  
之后进入过半等待阶段——leader会同其他learner服务器进行上述同样的数据同步，直到集群中有过半数的机器响应了。  
LearnerHandler向对应的follower发送Leader.UPTODATE，follower接收到之后，终止数据同步流程，集群正式开始对外服务
<img src="/img/MmVbtVpF8zy3OG0FAxA7.png" width="60%" />
## 消息广播
![upload successful](/img/F6y8hatqwOO658hDoaPV.png)
![upload successful](/img/LMk82xeFBdM4i1HFiprq.png)
### 数据字段
- ConcurrentMap outstandingProposals  
Leader拥有的属性，每当提出一个议案，都会将该议案存放至outstandingProposals，一旦议案被过半认同了，就要提交该议案，则从outstandingProposals中删除该议案
- ConcurrentLinkedQueue toBeApplied  
Leader拥有的属性，这个队列中保存了已经完成投票（即commit）的proposal，但是这些proposal还没有应用到本机的内存中（这个工作是由FinalRequestProcessor来完成的）


### 处理流程
- leader针对客户端的事务请求（leader为该请求分配了zxid），创建出一个议案，并将zxid和该议案存放至leader的outstandingProposals中
- leader开始向所有的follower发送该议案（Proposal），如果过半的follower回复OK的话（Ack），则leader认为可以提交该议案，则将该议案从outstandingProposals中删除，然后存放到toBeApplied中
- leader对该议案进行提交（commit），会向所有的follower发送提交该议案的命令，leader自己也开始执行提交过程（传递给FinalRequestProcessor处理）。（FinalRequestProcessor）会将该请求的内容应用到ZooKeeper的内存树中，然后更新lastProcessedZxid为该请求的zxid，同时将该请求的议案存放到上述committedLog，同时更新maxCommittedLog和minCommittedLog
- 从toBeApplied中删除对应的proposal

# 参考
[Zookeeper的Quorum机制-谈谈怎样解决脑裂(split-brain)](https://blog.csdn.net/varyall/article/details/80151205)  
[zookeeper集群为什么要是单数](https://blog.csdn.net/beagreatprogrammer/article/details/78421007)  
[一直对zookeeper的应用和原理比较迷糊，今天看一篇文章，讲得很通透，分享如下](https://blog.csdn.net/gs80140/article/details/51496925)  
[zookeeper中的ZAB协议理解](https://blog.csdn.net/junchenbb0430/article/details/77583955)  
[zk源码阅读30:leader选举:FastLeaderElection源码解析](https://www.jianshu.com/p/3b295d7eccf2)  
[一个还不错的博客——赤子心](https://www.jianshu.com/u/2946c4d3899d)  
[ZAB](https://blog.csdn.net/xiaoqiaxiaoqi/article/details/80543532)