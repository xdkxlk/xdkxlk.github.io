---
title: centos 命令记录
date: 2018-03-03 11:17:02
tags:
---
## 防火墙(Centos 7)
```shell
firewall-cmd --state #查看默认防火墙状态（关闭后显示notrunning，开启后显示running）
systemctl stop firewalld.service #停止firewall
systemctl disable firewalld.service #禁止firewall开机启动

firewall-cmd --list-ports #查看已经开放的端口
firewall-cmd --zone=public --add-port=80/tcp --permanent #开启端口
```
## 压缩、解压
### 压缩
```shell
tar  -zcvf   压缩文件名.tar.gz   被压缩文件名
```
### 解压
```shell
tar  -zxvf   压缩文件名.tar.gz -C 目标目录
```
## 软件的安装和卸载
查看已安装的PHP
```shell
rpm -qa |grep php
```
查询rpm包的安装时间和详情
```shell
rpm -qi php-cli-5.4.16-42.el7.x86_64
```
卸载
```shell
yum remove php*
```
## MySQL的安装
CentOS上默认的mysql是mariadb，安装真正的mysql可以参考官网
>[Download MySQL Yum Repository ](https://dev.mysql.com/downloads/repo/yum/)  
>[在CentOS7上安装MySQL的辛路历程 ](http://blog.csdn.net/holmofy/article/details/69364800)  
  
```shell
rpm -Uvh mysql57-community-release-el7-9.noarch.rpm
yum install mysql-community-server
service mysqld start  
```
## 查看Linux版本
```shell
uname -a
```