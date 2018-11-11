title: Snap离线安装
author: xdkxlk
tags: []
categories:
  - Ubuntu
date: 2018-11-11 11:08:00
---
```
$ snap download hello-world

Fetching snap "hello-world"
Fetching assertions for "hello-world"

kyrofa@Pandora:~$ sudo snap ack hello-world_27.assert 

kyrofa@Pandora:~$ sudo snap install hello-world_27.snap

hello-world 6.3 from 'canonical' installed
kyrofa@Pandora:~$ snap list
Name                   Version                   Rev   Developer      Notes
<snip>
hello-world            6.3                       27    canonical      -
```