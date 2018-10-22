---
title: 高性能MySQL学习（一）
date: 2018-02-09 17:27:27
tags: 高性能MySQL
categories: MySQL
---
## MySQL逻辑架构
![image](/img/2018-2-9/1518333331.png)
1. 最上层是大多数基于网络的客户端/服务器都有的。比如连接处理、授权、安全等。
2. 第二层为MySQL服务器。包括SQL解析、分析、优化、缓存以及所有内置函数，所有跨存储引擎的功能的实现（存储过程、触发器、视图等）。
3. 第三层为存储引擎。负责数据的存储和读取，例如InnoDB。存储引擎API包含几十个底层函数，用于执行“开始一个事物”、“根据主键提取一行记录”等操作。但存储引擎不会去解析SQL（InnoDB是例外，由于MySQL服务器本身没有实现外键，其会解析外键），不同存储引擎之间也不会相互通信。  
  
## 并发控制
### 读写锁
- 读锁，又称共享锁。相互之间不阻塞。多客户可以同时读取。
- 写锁，又称排他锁。一个写锁会阻塞其他写锁和读锁。  
  
### 锁粒度
每种MySQL存储引擎都可以实现自己的锁策略和锁粒度。
#### 表锁
MySQL中最基本的锁策略，开销最小。会锁住整张表。  
在特定场景，表锁可能有良好的性能。另外，写锁有比读锁更高的优先级，因此一个写锁请求可能会被插入到读锁队列前面。  
尽管存储引擎可以管理自己的锁，MySQL本身还是会使用各种有效的表锁实现不同的目的。例如，对于ALTER TABLE之类的使用表锁，而忽略存储引擎的锁机制。
#### 行级锁
可以最大程度地支持并发，同时，也带来了最大的锁开销。（例如InnoDB和XtarDB）行级锁只在存储引擎实现，而MySQL服务器层没有实现。
### 事务
事务的ACID概念。ACID表示原子性（atomicity）、一致性（consistency）、隔离性（isolation）、持久性（durability）。
#### 隔离级别
- **READ UNCOMMITTED（未提交读）**  
事务中的修改，即使没有提交，对其他事务也都是可见的（<font color='red'>脏读</font>）。实际很少用。
- **READ COMMITTED（提交读）**  
大多数数据库默认的隔离级别（Sql Server，Oracle。但MySQL不是）。一个事务开始时，只能“看见”已经提交的事务所做的修改。也称为<font color='red'>不可重复读</font>，执行两次同样的查询，结果可能不同。  
singo拿着工资卡去消费，系统读取到卡里确实有2000元，而此时她的老婆也正好在网上转账，把singo工资卡的2000元转到另一账户，并在 singo之前提交了事务，当singo扣款时，系统检查到singo的工资卡已经没有钱，扣款失败，singo十分纳闷，明明卡里有钱，怎么没了。  
- **REPEATABLE READ（可重复读）**  
<font color='red'>MySQL默认隔离级别。</font>存在<font color='red'>幻读</font>问题。即指，在某个事物在读取某个范围内的记录时，另外一个事务又在该范围插入了新的纪录，当之前的事务再次读取该范围的记录时，会产生换行。InnoDB和XtraDB通过多版本并发控制（MVCC）解决了此问题。  
singo的老婆工作在银行部门，她时常通过银行内部系统查看singo的信用卡消费记录。有一天，她正在查询到singo当月信用卡的总消费金额 （select sum(amount) from transaction where month = 本月）为80元，而singo此时正好在外面胡吃海塞后在收银台买单，消费1000元，即新增了一条1000元的消费记录（insert transaction ... ），并提交了事务，随后singo的老婆将singo当月信用卡消费的明细打印到A4纸上，却发现消费总额为1080元，singo的老婆很诧异，以为出 现了幻觉，幻读就这样产生了。  
- **SERIALIZABLE（可串行化）**  
最高隔离级别。强制事务串行执行。实际中很少用。  
  
![image](/img/2018-2-9/1518333542.png)

### 死锁
对于死锁的解决是在存储引擎。解决死锁的方法：
1. 检测死锁的循环依赖
2. 当查询的时间达到锁等待超时的设定后放弃锁请求，但这种方式通常不太好。InnoDB目前的处理方式是，将持有最少行级排他锁的事务进行回滚。  
  
### 事务日志
可以帮助提高事务的效率。存储引擎在修改表的数据时只需要修改其内存拷贝，再把该修改行为记录到之久在硬盘的事务日志中，而不用每次都将修改数据本身持久到磁盘。日志持久之后，内存中被修改的数据在后台可以慢慢刷回到磁盘。
### 多版本并发控制
>[MySQL InnoDB引擎 MVCC并发控制](http://blog.csdn.net/tb3039450/article/details/66472579)  
  
只在RC和RR隔离级别工作。因为RU总是读取最新的数据行，而SERIALIZABLE则会对所有读取的行都加锁。
## MySQL的存储引擎
<font color='red'>除非万不得已，否则不要混合使用多种存储引擎</font>
### InnoDB
MySQL默认事务型引擎。用于处理大量的短期事务（大多正常提交，很少会被回滚）。可自动崩溃恢复。InnoDB采用MVCC支持高并发，并通过 Next-key Lock（Next-key Lock = Record Lock + Gap Lock）策略防止幻读的出现。间隙锁使InnoDB不仅仅锁定查询涉及的行，还会对索引中的间隙进行锁定，以防止幻影行的插入。  
但是MVCC是解决幻读的，Next-key Lock也是解决幻读的，两者之间的的关系还搞得不太清楚。  
>[MVCC和Next-key Lock的关系](http://blog.csdn.net/chen77716/article/details/6742128)  

### MyISAM
MySQL 5.1及其之前的默认引擎。不支持事务和行级所，崩溃后无法安全恢复。相对于InnoDB，支持全文索引，支持地理空间搜索。
### 例子
**日志型应用**  
对于插入的速度要求很高，使用MyISAM或者Archive比较合适，它们开销低，且插入速度非常快。  
如果要对日志进行报表分析，生成报表的SQL很可能导致插入的效率明显降低。  
解决方法：
1. 利用MySQL内置的复制方案将数据复制到备库上，在备库上执行查询
2. 将日志的记录按照时间分表（例如：web\_logs\_2012\_01）。这样可以在已经没有插入操作的历史表上做频繁的查询操作。  
  
### 表引擎的修改
- **方法1：ALTER TABLE**  
  
```sql
mysql> ALTER TABLE mytable ENGINE = InnoDB
```
存在问题：需要执行很长时间。MySQL会按行将数据从原表复制到一张新表中，会消耗大量I/O能力，同时原表上会加上读锁。因此，在繁忙表上要小心。
- **方法2：导入与导出**  
手动导出原表，手动修改再导入
- **方法3：创建与查询**  
不需要导出整个表的数据，先创建一个新的存储引擎的表，在利用INSERT...SELECT完成  
  
```sql
CREATE TABLE innodb_table LIKE myisam_table;
ALTER TABLE innodb_table ENGINE = InnoDB;
INSERT INTO innodb_table SELECT * FROM myisam_table;
```
数据量很大，可以进行分批处理，避免大事物产生过多的undo
```sql
START TRANSACTION;
INSERT INTO innodb_table SELECT * FROM myisam_table WHERE id BETWEEN x AND y;
COMMIT;
```
## 附录
### 幻读
>[关于幻读，可重复读的真实用例是什么?](https://www.zhihu.com/question/47007926?answer_deleted_redirect=true)  
  
由于MySQL通过MVCC解决了幻读，所以，对于MySQL的幻读并不是对于范围数据的修改产生的。如下：  
```sql
users： id 主键
1、T1：select * from users where id = 1;
2、T2：insert into `users`(`id`, `name`) values (1, 'big cat');
3、T1：insert into `users`(`id`, `name`) values (1, 'big cat');
```
&emsp;&emsp;T1 ：主事务，检测表中是否有 id 为 1 的记录，没有则插入，这是我们期望的正常业务逻辑。  
&emsp;&emsp;T2 ：干扰事务，目的在于扰乱 T1 的正常的事务执行。  
&emsp;&emsp;在 RR 隔离级别下，1、2 是会正常执行的，3 则会报错主键冲突，对于 T1 的业务来说是执行失败的，这里 T1 就是发生了幻读，因为T1读取的数据状态并不能支持他的下一步的业务，见鬼了一样。  
&emsp;&emsp;在 Serializable 隔离级别下，1 执行时是会隐式的添加 gap 共享锁的，从而 2 会被阻塞，3 会正常执行，对于 T1 来说业务是正确的，成功的扼杀了扰乱业务的T2，对于T1来说他读取的状态是可以拿来支持业务的。  
&emsp;&emsp;所以 mysql 的幻读并非什么读取两次返回结果集不同，而是事务在插入事先检测不存在的记录时，惊奇的发现这些数据已经存在了，之前的检测读获取到的数据如同鬼影一般。  
&emsp;&emsp;这里要灵活的理解读取的意思，第一次select是读取，第二次的 insert 其实也属于隐式的读取，只不过是在 mysql 的机制中读取的，插入数据也是要先读取一下有没有主键冲突才能决定是否执行插入。  
&emsp;&emsp;不可重复读侧重表达 读-读，幻读则是说 读-写，用写来证实读的是鬼影。  