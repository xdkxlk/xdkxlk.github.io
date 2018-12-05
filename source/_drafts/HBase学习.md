title: HBase学习
author: xdkxlk
tags:
  - 基础学习
categories:
  - HBase
date: 2018-12-04 15:50:00
---
# 基本命令
## 创建表
```sql
create '表名称', '列族名称1', '列族名称2', '列族名称N'
```
## 添加记录/更新记录
```sql
put '表名称', '行名称', '列族名称:列名称', '值'
```
## 查看记录
```sql
get '表名称', '行名称'
```
## 查看表中的记录总数
```sql
count '表名称'
```
## 删除记录
```sql
delete '表名', '行名称', '列名称'
```
## 删除一张表
```sql
# 先要屏蔽该表，才能对该表进行删除，第一步
disable '表名称'

# 第二步
drop '表名称'
```
## 查看所有记录
```sql
scan "表名称"
```
## 查看某个表某个列中所有数据
```sql
scan "表名称", { COLUMNS => '列族名称:列名称' }
```
## 多版本
```sql
scan 'test',{VERSIONS=>3}
```
这句话的意思是，查出最近的3个版本。如果是 `VERSIONS=>1`，则是查出最近的1个版本  
默认建表可容纳的 `VERSIONS` 为1，所以就会发现怎么查都只有一条。这需要修改或者在建表的时候指定
```sql
hbase(main):044:0> describe 'test'
Table test is ENABLED                                                                                     
test                                                                                                      
COLUMN FAMILIES DESCRIPTION                                                                               
{NAME => 'cf1', BLOOMFILTER => 'ROW', VERSIONS => '1', IN_MEMORY => 'false', KEEP_DELETED_CELLS => 'FALSE'
, DATA_BLOCK_ENCODING => 'NONE', TTL => 'FOREVER', COMPRESSION => 'NONE', MIN_VERSIONS => '0', BLOCKCACHE 
=> 'true', BLOCKSIZE => '65536', REPLICATION_SCOPE => '0'}                                                
1 row(s) in 0.0310 seconds
```
注意里面的 `VERSIONS => '1'`意思是说，只保留一个版本  
修改`VERSIONS` 为3
```sql
alter 'test', {NAME=>'cf1', VERSIONS=>3}
```
然后就可以查出来了
## 清空表
```sql
truncate '表名称'
```
# javaApiCRUD
## 依赖
```xml
<dependency>
    <groupId>org.apache.hbase</groupId>
    <artifactId>hbase-client</artifactId>
    <version>2.1.1</version>
</dependency>
```
## 连接获取
```java
Configuration conf = HBaseConfiguration.create();
Connection connection = ConnectionFactory.createConnection(conf);
Table table = connection.getTable(TableName.valueOf(tableName)
// 后面操作的就是这个table
// 最后别忘了close，可以使用 try-with-resources
```
## put
新增和更新  
定义操作，一个`Put`就是对于一个rowKey的操作
```java
List<Put> puts = new ArrayList<>();

Put put = new Put(Bytes.toBytes("row1"));
put.addColumn(Bytes.toBytes("info"),
        Bytes.toBytes("q1"),
        Bytes.toBytes("val1"));
puts.add(put);

put = new Put(Bytes.toBytes("row2"));
put.addColumn(Bytes.toBytes("info"),
        Bytes.toBytes("q2"),
        Bytes.toBytes("val2"));
puts.add(put);
```
执行操作
```java
table.put(put);
// 可以是一个List<Put>
table.put(puts);
```
如果使用`List<Put>`，**那么就算中间有一个put出错了，后面的put操作也会继续执行**，没有事务
### 写缓存
如果使用`Table`类来进行操作，一个put操作对应一个RPC请求，如果想减少RPC请求的数量，可以使用客户端写缓存。
```java
try (BufferedMutator mutator = connection.getBufferedMutator(TableName.valueOf(tableName))) {

    Put put = new Put(Bytes.toBytes("r1"));
    put.addColumn(Bytes.toBytes("info"),
            Bytes.toBytes("name"),
            Bytes.toBytes("lk"));
    put.addColumn(Bytes.toBytes("info"),
            Bytes.toBytes("age"),
            Bytes.toBytes("23"));
    mutator.mutate(put);

    put = new Put(Bytes.toBytes("r2"));
    put.addColumn(Bytes.toBytes("info"),
            Bytes.toBytes("name"),
            Bytes.toBytes("mk"));
    put.addColumn(Bytes.toBytes("info"),
            Bytes.toBytes("age"),
            Bytes.toBytes("18"));
    mutator.mutate(put);

    mutator.flush();
}
```
使用`BufferedMutator`进行操作（旧版本是`Table.setAutoFlush(fasle)`），如果调用`flush`将显式的将缓存中的操作提交到HBase。如果不显式的调用，那么会在 `hbase.client.write.buffer` 的限制下将数据`flush`到HBase。在`flush`之前，HBase上是看不见数据的。  
**客户端写缓存是简单的保存在客户端的内存中，有数据丢失的风险**  
而且，**更大的缓冲区意味着客户端和服务端都会消耗更多的内存**，因为一次性发送这写数据同样占用服务端这么多的内存，占用的内存可用`hbase.client.write.buffer * hbase.regionserver.handler.count（region服务器的数量）`估计

### compare-and-set
使用`checkAndPut` 方法，只有 `value` 匹配得上，才会进行操作  
有时候需要判断某个值不存在，那么这个时候 `value` 设为 `null` 即可

## batch操作
`table.batch`，可以批量操作 `Put`, `Get`, `Delete`

# 工作机制简述
## 写数据
- 默认情况下，写会写到两个地方去：预写式日志（write-ahead log, WAL, 又名HLog）和 MemStore。只有这两个地方都写入并确认成功了，才认为写动作完成。 
- MemStore是内存中的写入缓冲区，当MemStore写满后，其中的数据会刷写到磁盘，生成一个HFile
- 一个列簇可以有多个HFile，一个HFile不能有多个列簇
- 集群的每个节点上，每个列簇有一个MemStore
- WAL 是用来保证MemStore万一丢失了来恢复的，用来记录发生的变化。最终是用HFile存储的

<img src="/img/Lyc4o4AGOJ2dFzJ6AAw3.png" width="70%"/>
<img src="/img/jYpmgb0Ez6Rmpak4POsH.png" width="70%"/>
## 读数据
- HBase大多数读操作可以做到毫秒级
- 使用LRU缓存，BlockCache。用于保存HFile里读入内存频繁的数据
- 每个列族都有自己的BlockCache
- Block是HBase从硬盘完成一次读取的数据单位