title: Rabbitmq学习
author: xdkxlk
tags: []
categories:
  - Rabbitmq
date: 2018-12-13 20:19:00
---
# 消息通信
首先看一下AMQP的整体结构
<img width="75%" src="/img/XYltjZMh8yVMraeVzCPz.png"/>
## 队列
消费者可以通过两种方式接受消息：
- basic.consume  
订阅，有新消息就会自动收到
- basic.get  
获得队列中的一条消息。循环get效果上就等于consume，但是影响性能

消费者需要使用 `basic.ack` 对每一条消息进行确认
- 如果消费者在确认之前从rabbitmq断开了连接，那么就会重新分发给下一个消费者
- 如果没有确认，且没有断开连接，那么rabbit将不会给该消费者发送更多的消息，会认为这个消费者还没有准备好接收下一条消息

如果消费者想明确的拒绝一条消息的处理方法：
- 同rabbit断开连接（这不是一个好办法，虽然能够达到目的）
- 发送 `basic.reject` 拒绝消息。如果 `requeue` 为 `true`，那么会重新放回队列，发给下一个消费者；否则丢弃这条消息

创建队列 `queue.declare`
- `queue.declare` 创建队列
- 参数 `exclusive` 为true，那么队列将会是私有的
- 参数 `auto-delete` 当最后一个消费者取消订阅的时候，队列就会被删除
- 如果创建一个已经存在的队列，如果所有参数都一样，那么会什么都不做然后返回；如果参数不一样，那么会抛出错误
- 检测队列是否存在：设置 `passive` 为 `true`