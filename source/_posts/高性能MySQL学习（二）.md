---
title: 高性能MySQL学习（二）
date: 2018-02-11 15:49:59
tags: 高性能MySQL
categories: MySQL
---
*对应《高性能MySQL》第4章*
## 数据类型的选择
- 更小的通常更好  
但是要确保没有低估需要存储的值的范围
- 简单就好  
使用简单的数据类型（例如，整型比字符串操作代价更低）
- 尽量避免NULL  
  
可为NULL的列使得索引、索引统计和值比较都更复杂。然而，通常把NULL改成NOT NULL带来的性能提升比较小，但是，如果计划在列上建索引，就应该尽量避免设计成可为NULL
## 整数类型
TINYINT，SMALLINT，MEDIUMINT，INT，BIGINT  
分别使用  
8，16，24，32，64位存储空间  
存储范围从 -pow(2, N-1) 到 pow(2, N-1)-1  
整数还有 UNSIGNED 属性
## 字符串类型
每个字符串列可以定义自己的字符集和排序规则（校对规则），这些会很大程度影响性能（详细在第7章讲）。下面在InnoDB或者MyISAM的情况下对比VARCHAR和CHAR
### VARCHAR
用于存储可变长的字符串。仅存储必要的空间，越短的字符串使用的空间越少。（例外情况：如果MySQL表使用<code>ROW_RORMAT=FIXED</code>创建，每行都会使用定长存储，会浪费空间）  
需要1或2个额外字节记录字符串长度。如果列的最大长度小于等于255字节，则需要1个字节表示，否则使用2个字节。（或许这就是Navicat VARCHAR默认是255的原因吧）
### CHAR
定长。当存储CHAR值时，MySQL会删除所有末尾的空格（因为，CHAR值会根据需要采用空格进行填充以便于比较）。适合存储很短的字符串，或所有值的长度都差不多（比如密码的MD5值）。对于经常变的数据，CHAR也比VARCHAR更好。
### BLOB和TEXT
仅有的区别：  
- BLOB存储的是二进制数据，没有排序规则或字符集
- TEXT有字符集和排序规则  
  
BLOB等于SMALLBOLB，属于TINYBLOB，SMALLBLOB，MEDIUMBLOB，LONGBLOB  
TEXT等于SMALLTEXT，属于TINYTEXT，SMALLTEXT，MEDIUMTEXT，LONGTEXT  
- 当太大的时候，InnoDB会将数据保存于“外部”存储区，在每个值的位置只保存指针
- 对于它们的排序不同于其他，只对于前面的一小部分字符进行排序  
  
### 使用枚举类型代替字符串类型
>[MYSQL数据库中的枚举类型和集合类型](http://blog.csdn.net/woshinannan741/article/details/50519339)  
  
感觉对于固定的永远都不变的分类什么的，直接使用MySQL的枚举类型比较方便（比如，性别），但实际上的应用场景也不是很多。枚举类型在处理的时候是转化成数字了的，所以，在查找时采用整数主键对于查询的速度比较快
## 日期和时间
MySQL最小时间粒度为秒（MariaDB支持微秒），但MySQL可以使用微秒进行临时计算。
### DATETIME
保存大范围的时间（从1001年到9999年）。与时区无关。
### TIMESTAMP
保存从1970.1.1以来的秒数（从1970年到2038年）。与时区有关。
### 总结
通常尽量使用TIMESTAMP，因为其空间效率更高。  
<font color='red'>mysql5.6.4以后的版本，支持带毫秒、微妙的时间数据</font>。使用<code>DATETIME(6)、TIMESTAMP(6)、CURRENT_TIMESTAMP(6)</code>既可以精确到秒后面6位了。  
查询方法
```sql
SELECT
    DATE_FORMAT( create_time, '%Y-%m-%d %T.%f' ) AS createTimeStr 
FROM
	time_stu
```
## 标识符（identifier）
选择哪个类型作为主键
### 整数类型
通常是最好的选择
### ENUM、SET
不好。只适用于存储固定信息。而且内部使用整数存储，比较时转换为字符串
### 字符串类型
避免使用字符串作为标识列。对于完全“随机”的字符串（如，MD5，SHA1，UUID产生的），这些值的取值范围过大，于是INSERT已经SELECT语句变得很慢。  
如果存储UUID，应移除 “-” 符号。更好的做法，用UNHEX()转换为16字节数字，并存储于BINARY(16)列中。检索时通过 HEX()还原
## MySQL schema 设计中的陷阱
- 太多的列
- 太多的关联
- 全能的枚举  
防止过度使用枚举。修改枚举的值需要 ALTER TABLE操作
- 变相的枚举
- Not Invent Here 的 NULL  
避免使用NULL，可以使用其他值来代替NULL。但不要过于极端。（MySQL会在索引中存储NULL值，而Oracle则不会）  
  
## 范式与反范式
### 范式
- 范式化的更新操作通常更快
- 修改是只需要修改更少的数据
- 范式化的表通常更小
- 很少有多余的数据意味着检索列表数据时更少需要DISTINCT或者GROUP BY