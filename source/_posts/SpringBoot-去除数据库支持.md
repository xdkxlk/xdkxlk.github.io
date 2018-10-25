title: SpringBoot 去除数据库支持
author: xdkxlk
tags:
  - SpringBoot
  - ''
categories:
  - 后端
date: 2018-10-25 12:05:00
---
一般SpringBoot默认有数据库支持，但是要去除数据库支持怎么办。  

方法，在启动类上添加
```java
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
```