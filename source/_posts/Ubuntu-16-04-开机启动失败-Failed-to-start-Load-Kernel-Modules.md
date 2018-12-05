title: Ubuntu 16.04 开机启动失败 Failed to start Load Kernel Modules
author: xdkxlk
tags: []
categories:
  - Ubuntu
date: 2018-12-04 10:05:00
---
感觉应该是升级了内核的原因，无法进入图形界面了  

解决：

```shell
dpkg-reconfigure linux-image-$(uname -r)
```