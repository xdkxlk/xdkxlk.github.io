title: Spark groupByKey和reduceByKey
author: xdkxlk
tags: []
categories:
  - Spark
date: 2018-10-13 18:58:00
---
Spark里面其实有两个不同的groupByKey
## RDD里面的groupByKey  
这个其实是PairRDDFunctions里面的，需要是RDD\[\(K,V\)\]才可以调用，这个就是网上很多人说的那个groupByKey。  
在一个 (K, V) pair 的 dataset 上调用时，返回一个 (K, Iterable<V>) .   
**Note**: 如果分组是为了在每一个 key 上执行聚合操作（例如，sum 或 average)，**此时使用 reduceByKey 或 aggregateByKey 来计算性能会更好**.   
**Note**: 默认情况下，并行度取决于父 RDD 的分区数。可以传递一个可选的 numTasks 参数来设置不同的任务数.  
groupBykey是把所有的键值对集合都加载到内存中存储计算，所以如果一个键对应的值太多的话，就会导致内存溢出的错误，这是需要重点关注的地方
[Spark源码之reduceByKey与GroupByKey](https://blog.csdn.net/do_yourself_go_on/article/details/76033252) 
![upload successful](/img/4E9HKoomXxAfnE.png)
![upload successful](/img/CG828SiJe8pHpb.png)

## DataSet里面的groupByKey
dataSet里面的groupByKey其实就是类似于数据库sql的groupBy
Returns a \[\[KeyValueGroupedDataset\]\] where the data is grouped by the given key \`func\`.  
根据key \`func\` group by 数据  
dataSet里面没有reduceByKey