title: HDFS学习
author: xdkxlk
tags:
  - HDFS
categories:
  - Hadoop
date: 2018-11-30 15:52:00
---
# 概念
## 数据块
在磁盘中，每个磁盘都有默认的数据块大小，这是磁盘进行数据读/写的最小单位，磁盘块一般为512字节。在分布式文件系统中，数据块一般远大于磁盘块的大小，并且为磁盘块大小的整数倍  
HDFS的块（block）默认大小128M。和磁盘的文件系统一样，HDFS也被划分为多个分块（chunk），作为独立的存储单元。但是，**HDFS中小于一个块大小的文件不会占用整个块的大小。**    
HDFS的块比磁盘的大是为了最小化寻址开销。但也不能太大，MapReduce中map任务通常一次只处理一个块中的数据。

使用数据块的好处：
- 一个文件大小可以大于网络中任意一个磁盘的容量，各个块可以存储在网络上的不同位置
- 使用抽象块大大简化了存储子系统的设计
- 适合进行数据备份和负载均衡

## namenode & datanode
### namenode 
- 管理节点
- 管理文件系统的命名空间
- 维护文件系统树和整棵树所有的文件和目录
- 以文件形式永久保存在本地磁盘上（命名空间镜像文件**fsimage**，编辑日志文件**edit logs**）
- **在内存中**记录每个文件中各个块所在的数据节点信息，但不永久保存，会在启动时根据数据节点信息重建

### datanode
- 文件系统的工作节点
- 根据需要存储并检索数据块（受客户端或者namenode调度）
- 定期向namenode发送所存储的块的列表

### 关系
namenode管理者datanode。没有namenode文件系统无法使用。如果namenode损坏，则会丢失文件数据。**namenode容错很重要**
### namenode容错
1. （方法1）备份文件系统元数据的文件  
HDFS可以配置使namenode在多个文件系统保存元数据的持久状态。**这些写操作实时同步，且是原子的。**例如：将持久状态写入本地磁盘的同时，写入一个远程挂载的网络文件系统
2. （方法2）运行一个辅助namenode（**Secondary NameNode**），但这个namenode不能作为namenode  
	- **重要作用：定期合并编辑日志与命名空间镜像**，以防止编辑日志过大  
	- 一般在另一台单独的物理主机上运行，以防止编辑日志过大  
	- 保存合并之后的fsimage，并在namenode故障之后启用
    - 保存的数据一般滞后，难免丢失数据
    
### 详细分析
fsimage 保存了最新的元数据检查点，包含了整个HDFS文件系统的所有目录和文件的信息。对于文件来说包括了数据块描述信息、修改时间、访问时间等；对于目录来说包括修改时间、访问权限控制信息(目录所属用户，所在组)等。

editlog 主要是在NameNode已经启动情况下对HDFS进行的各种更新操作进行记录，HDFS客户端执行所有的写操作都会被记录到editlog中。

NameNode维护了文件与数据块的映射表以及数据块与数据节点的映射表，什么意思呢？就是一个文件，它切分成了几个数据块，以及这些数据块分别存储在哪些datanode上，namenode一清二楚。Fsimage就是在某一时刻，整个hdfs 的快照，就是这个时刻hdfs上所有的文件块和目录，分别的状态，位于哪些个datanode，各自的权限，各自的副本个数。然后客户端对hdfs所有的更新操作，比如说移动数据，或者删除数据，都会记录在editlog中。  
editlog文件会在集群运行的过程中不断增多，占用更多的存储空间，虽然有合并，但是只有在namenode重启时才会进行。并且在实际工作环境很少重启namenode。所以，就出现了 Secondary NameNode  

secondary namenode会周期性合并fsimage和edits成新的fsimage，新的操作记录会写入新的editlog中，这个周期可以自己设置
```xml
<property>
  <name>dfs.namenode.checkpoint.txns</name>
  <value>1000000</value>
  <description>操作动作次数</description>
</property>

<property>
  <name>dfs.namenode.checkpoint.check.period</name>
  <value>60</value>
  <description> 1分钟检查一次操作次数</description>
</property>
```

![upload successful](/img/wuNCjAD54l20DFdjcEz0.png)
可以很清晰看出:  
1. 将hdfs更新记录写入一个新的文件——edits.new。
2. 将fsimage和editlog通过http协议发送至secondary namenode。
3. 将fsimage与editlog合并，生成一个新的文件——fsimage.ckpt。这步之所以要在secondary namenode中进行，是因为比较耗时，如果在namenode中进行，或导致整个系统卡顿。
4. 将生成的fsimage.ckpt通过http协议发送至namenode。
5. 重命名fsimage.ckpt为fsimage，edits.new为edits。

## 块缓存
对于datanode访问频繁的文件，其对应的块可以显示的缓存在datanode的内存中
## HDFS Federation（联邦HDFS）
由于namenode在内存中保存文件系统中每个文件和每个数据块的引用关系，**这意味着，对于超大的文件系统，内存会成为namenode的瓶颈**，HDFS Federation就是为了解决这个问题而存在的（Hadoop 2.x新增）。**并没有完全解决单点故障问题**   
在HDFS Federation中，每个namenode管理文件系统中的一部分。  
![upload successful](/img/Np0TyWkDWK0zj6ONgxqq.png)
Federation使用了多个独立的Namenode/NameSpace。这些Namenode之间是联合的，也就是说，他们之间**相互独立且不需要互相协调**，各自分工，管理自己的区域，甚至一个namenode失效了也不影响其他namenode维护的命名空间。分布式的datanode被用作通用的数据块存储存储设备。**每个DataNode要向集群中所有的namenode注册**，且周期性的向所有namenode发送心跳和块报告，并执行来自所有namenode的命令。  
- 所谓Block Pool（块池）就是属于单个命名空间的一组block（块）。
- 每一个DataNode为所有的Block Pool存储块。**DataNode是一个物理概念，而Block Pool是一个重新将block划分的逻辑概念。**同一个datanode中可以存着属于多个Block Pool的多个块。 
	- Block Pool允许一个命名空间在不通知其他命名空间的情况下为一个新的block创建Block ID。同时一个Namenode失效不会影响其下Datanode为其他Namenode服务。
	- 每个Block Pool内部自治，也就是说各自管理各自的block，不会与其他Block Pool交流。一个Namenode挂掉了，不会影响其他NameNode。
	- 当DN与NN建立联系并开始会话后自动建立Block Pool。每个block都有一个唯一的表示，这个表示我们称之为扩展块ID,在HDFS集群之间都是惟一的，为以后集群归并创造了条件。
	- DN中的数据结构都通过块池ID索引，即DN中的BlockMap，storage等都通过BPID索引。
	- 某个NN上的NameSpace的元数据和它对应的Block Pool一起被称为NameSpace Volume。它是管理的基本单位。当一个NN/NS被删除后，其所有DN上对应的Block Pool也会被删除。当集群升级时，每个NameSpace Volume作为一个基本单元进行升级。
    
Federation很像是linux文件系统的挂载的感觉
![upload successful](/img/5qj7S2w81gvHMk02Tx7P.png)
客户端可以通过不同的挂载点来访问不同的命名空间，如同linux系统中访问不同挂载点一样  

主要优点
- namespace是一个可扩展的，相当于namenode是一个分布式的。 
- 性能提升了，操作不会由于一个namenode的吞吐量收到限制。 
- 隔离性。每个namenode只管理一部分文件 。不同用户可以被namespace隔离。

## 高可用
如果考虑HDFS集群的标准配置，则NameNode将成为单点故障。发生这种情况是因为NameNode变得不可用的时候，整个集群都变得不可用，直到有人重新启动NameNode或者引起新的NameNode。  
HDFS HA通过在主动/被动配置中提供在同一集群中运行两个NameNode的来解决上述问题。这两个NameNode被称为活动NameNode和备用NameNode。与Secondary NameNode不同的是，备用NameNode是热备用，允许在主机崩溃的情况下或者有计划的停机维护情况下快速的自动的切换到新的NameNode。一个集群只能有一个NameNode。
![upload successful](/img/62mlhWqjNp23MqH5s27m.png)



# 命令
查看文件系统中各个文件由哪些块构成
```shell
hdfs fsck / -files -blocks
```
# 参考
[hadoop学习——namenode的fsimage与editlog详解](https://blog.csdn.net/chenkfkevin/article/details/61196409)  
[NameNode工作机制、镜像文件、编辑日志文件、namenode版本号](https://blog.csdn.net/newbie_907486852/article/details/83069565)  
[如何使用HDFS高可用性设置Hadoop集群](https://blog.csdn.net/qq_40784783/article/details/79115526)  
[HDFS高可用简介](https://www.jianshu.com/p/a3242285da54)
