title: Redis的持久化
author: xdkxlk
tags: []
categories:
  - Redis
date: 2018-11-09 15:43:00
---
Redis有两种持久化的方式：快照（snapshotting），只追加文件（append-only file，AOF）
# 快照（RDB）
创建存储在内存的某个时间的数据的副本
## 配置
### save
可以写多个 或 关系的条件。这些条件只要有一个被满足就会执行快照；如果不写，那么就会完全禁用掉快照
```
save 900 1	# 900秒之后至少有1个key被修改，就快照
save 300 10	# 300秒之后至少有10个key被修改，就快照
save 60 10000	# 60秒之后至少有10000个key被修改，就快照
```
### stop-writes-on-bgsave-error
默认值 yes  
默认情况下，如果启用了RDB快照（至少一个保存点）并且最近的后台保存失败，Redis将停止接受写操作。这将使用户意识到数据没有正确地保存在磁盘上，否则很可能没有人会注意到并且会发生一些灾难。  
如果后台保存过程将再次开始工作，Redis将自动允许再次写入。  
但是，如果您已经对Redis服务器做了适当监视和持久性的配置，那么您可能希望禁用该特性，以便即使存在磁盘、权限等问题，Redis仍会像往常一样工作。
### rdbcompression
默认值 yes  
是否在创建dump(.rdb)文件的时候使用LZF对于字符串对象进行压缩。一般进行压缩是比较好的策略，但是如果想在减少一些CPU的使用，那么可以设为`no`
### dbfilename
存储dump文件的文件名
## 创建快照的方法
- 发送 `BGSAVE`  
redis会fork（Windows不支持）一个子线程负责快照的写入，父进程继续处理请求。但是如果在一个内存占用比较高的redis，或者存储的数据很大的redis（数十GB）上，光是fork就会耗费很多的时间，而且子线程会同主线程争夺资源，可能会导致系统长时间的停顿（卡死）
- 发送 `SAVE`
redis会在创建完快照前不会响应任何其他命令。很少用
- 根据 `save` 选项触发快照
- 收到 `SHUTDOWN` 命令关闭时，或者收到标准 `TERM` 信号关闭时  
redis会在关闭之前，执行一个 `SAVE`
- 服务器之间复制的时候  
`SYNC`

## 存在问题
由于快照怎么配置都会有时间的延迟，保存的数据不会是实时的，所以一定会存在丢失数据的情况。**所以，快照持久化只适用于即使丢失一部分数据也没有关系的应用程序。**
# AOF
将被执行的命令写到aof文件末尾，恢复的时候需要从头到尾重新执行一次，**注意，AOF和RDB可以同时开启，但是如果AOF开启了，那么启动的时候会优先读取AOF**
## 配置
### appendonly
开启或关闭 AOF
## appendfilename
AOF文件文件名，默认 "appendonly.aof"
## appendfsync
操作系统文件写入的时候，会先写在缓存，然后再写入磁盘
- always  
每个redis写命令都会被写入磁盘，但对于磁盘的写入太多了，收到磁盘性能的限制
- no  
让操作系统来决定（可能会丢失数据）
- everysec  
每秒执行一次，显示写入磁盘
## no-appendfsync-on-rewrite
- 设置为yes。就相当于将appendfsync设置为no，这说明并没有执行磁盘操作，只是写入了缓冲区，因此这样并不会造成阻塞（因为没有竞争磁盘），但是如果这个时候redis挂掉，就会丢失数据。在linux的操作系统的默认设置下，最多会丢失30s的数据。
- 默认设置为no。

## auto-aof-rewrite-*
当AOF文件大于64M,并且体积比上一次重写之后的大小大了一倍（100%）时，进行BGREWRITEAOF
```
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
```

## aof-load-truncated
redis再恢复时，忽略最后一条可能存在问题的指令(因为最后一条指令可能存在问题，比如写一半时突然断电了)
## aof-use-rdb-preamble
Redis4.0新增RDB-AOF混合持久化格式，在开启了这个功能之后，AOF重写产生的文件将同时包含RDB格式的内容和AOF格式的内容，其中RDB格式的内容用于记录已有的数据，而AOF格式的内存则用于记录最近发生了变化的数据，这样Redis就可以同时兼有RDB持久化和AOF持久化的优点（既能够快速地生成重写文件，也能够在出现问题时，快速地载入数据）。
## BGREWRITEAOF 机制
AOF 文件一般会很大，用户可以向redis发送 `BGREWRITEAOF` ，通过移除AOF文件中的冗余命令来重写AOF文件。BGREWRITEAOF机制，在一个子进程中进行AOF的重写，从而不阻塞主进程对其余命令的处理，同时解决了AOF文件过大问题。
