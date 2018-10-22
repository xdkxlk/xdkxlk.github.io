title: docker修改镜像和启动异常
tags: []
categories: []
date: 2018-08-03 10:00:00
---
配置 sudo vim /etc/docker/daemon.json 文件
```json
{
    "hosts": [
        "tcp://127.0.0.1:2375",
        "unix:///var/run/docker.sock"
    ],
    "registry-mirrors": ["https://registry.docker-cn.com"]
}
```
重启服务的时候可能会报错
```shell
Job for docker.service failed because the control process exited with error code.See "systemctl status docker.service" and "journalctl -xe" for details.
```
错误原因：docker的socket配置出现了冲突，docker在运行时有一个启动入口文件：/lib/systemd/system/docker.service，而我们在修改镜像加速器的时候又给它生成了一个配置文件：/etc/docker/daemon.json，两个文件对host进行了配置，所以发生冲突。

解决方法：将docker启动入口文件中的-H fd://删除再重启服务，或者在启动入口配置监听的端口和本地socket信息：
```bash
vim /lib/systemd/system/docker.service

#原:ExecStart=/usr/bin/dockerd -H fd:// $DOCKER_OPTS
ExecStart=/usr/bin/dockerd
#或者改成：ExecStart=/usr/bin/dockerd -H tcp://0.0.0.0:2375 -H unix:///var/run/docker.sock
```
