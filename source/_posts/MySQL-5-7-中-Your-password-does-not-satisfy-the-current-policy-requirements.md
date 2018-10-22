---
title: MySQL 5.7 中 Your password does not satisfy the current policy requirements
date: 2018-03-14 19:43:58
tags:
categories: MySQL
---
http://blog.csdn.net/maxsky/article/details/51171474   
1. 
```sql
set global validate_password_policy=0;
```
更改强度为 LOW，代表密码任意，但长度在 8 位或以上。但是，LOW 强度允许我们设置为纯数字纯字母等密码，但是我们还是不能设置 123456，因为最低要求 8 位  
2. 
 ```sql
set global validate_password_length=4;
 ```
 其实不管你设置 1、2、3、4，最低长度都是 4