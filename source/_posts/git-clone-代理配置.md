title: git clone 代理配置
author: xdkxlk
tags:
  - git
categories: []
date: 2018-08-10 23:56:00
---
设置代理
```shell
git config --global https.proxy 127.0.0.1:8087
git config --global http.proxy 127.0.0.1:8087
```
取消已设置的代理
```shell
git config --global --unset http.proxy
git config --global --unset https.proxy
```