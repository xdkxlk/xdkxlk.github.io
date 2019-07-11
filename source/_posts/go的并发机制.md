title: go的并发机制
author: xdkxlk
tags:
  - go
categories:
  - go
date: 2019-07-07 09:48:00
---
# GPM模型
- M：machine。M是内核线程的抽象，负责调度任务
- P：processor。P代表执行一个GO代码片段所必须的资源。对于G来说，P相当于CPUP，G只有绑定到P(在P的local runq中)才能被调度。对M来说，P提供了相关的执行环境(Context)，如内存分配状态(mcache)，任务队列(G)等
- G：goroutine。一个G代表一个Go代码片段，是对于Go代码片段的封装。G保存Goroutine的运行堆栈，即并发任务状态。G并非执行体，每个G需要绑定到P才能被调度执行。

一般情况下，GPM之间的关系
![upload successful](/img/rQ5BKvz3TR755v8QsTmB.png)

# GPM的容器

![upload successful](/img/5u2TZ8exsVu7PsAsq9yv.png)

注意几个点：
- 全局的列表都是为了对于整体有一个把控，重点在于那些非全局的容器
- 这里有两个可运行的G列表，这两个列表都有着几乎平等的运行机会
- 这两个可运行G列表是可以进行互相转移的。例如：
	- runtime.GOMAXPROCS，那么会把将死的P的可运行G列表转移到调度器的可运行G列表
	- 如果P的本地可运行G满了，那么会转移一部分到调度器可运行的G列表中

![upload successful](/img/29nm5XmgYYd8h8BEdvuc.png)

对于M的数量的控制通过调用 `debug.SetMaxThreads(num)` 修改，默认大小为10000

对于P数量的控制通过调用 `runtime.GOMAXPROCS(num)` 修改。修改GOMAXPROCS会导致 StopTheWorld，暂时让所有P都脱离运行状态

对于M和P的数量的修改最好是早点修改比较好，虽然go可以在运行时对于其进行修改，但是修改的时候会与性能会有很大的影响

# M

M结构部分字段

字段 | 描述 |  
-|-|
g0 \*g | 一个特殊的G，一般用于执行调度、垃圾回收、栈管理等 |
mstartfn func() | 在一个新的M上启动的某个特殊任务的函数，如果这个任务是系统监控任务的话，那么该M就会一直执行它。当任务执行完了，会跟nextp相关联，然后寻找可以运行的G |
curg \*g | 当前M正在运行的G |
p \*p | 当前关联的P |
nextp \*p | 预先关联的P，暂存与当前M有潜在关联的P |
spinning bool | 标识是否处于自旋状态。处于自旋状态说明M正在寻找可以运行的G |
lockedg \*g | M可以同一个G绑定在一个，lockedg保存那个绑定的G。如果绑定了一个G，那么这个M就只能运行这个G了，这个G也只能在这个M上运行 |

M创建的时机：
- 当没有足够的M来关联P的时候来运行G的时候
- 运行时系统执行系统监控或者GC的时候

# P

P状态的转换

<img src="/img/72RjCN5YHnRch9aNRJ6K.png" width="50%"/>

需要注意几个点：
- 除了Pgcstop其他状态都有可能被置于Pdead而被丢弃（比如缩小了GOMAXPROCS）
- 所有非Pdead状态的P在停止调度之后恢复调度的时候，都是统一转换为Pidle状态的，说明，他们之间是公平的
- 刚刚创建的G是在本地P可运行列表里面
- 当本地P自由的G很多的时候，会转移一部分到调度器的自由G列表里面
- 当本地P自由的G不够的时候，会先从调度器的自由G列表里面拿，如果还是没有，那么新建G

# G

G状态的转换

<img src="/img/U9fqE27KxneeKWJk6aJe.png" width="100%"/>

需要注意的几个点：
- 还有一个 `Gscan` 状态，但是这个状态是同上面的状态组合存在的。比如，Gscanrunnable，表明这个G等待运行，同时它的栈等待扫描。一般Gscan的原因是GC
- G进入Gdead状态之后是可以被重新初始化然后再被使用的（不同于P）

## G的创建
需要G的时候尝试获得自由的G，先从本地P的自由G中获得（无锁）；如果没有那么去调度器的自由G中获取（加锁），同时转移一部分的自由G到P；如果还是没有才创建新的G，新建的G都是放入本地P的可运行G列表中的。

创建好了G之后：
- 尝试将这个G添加到P的runnext
- 如果失败，尝试将G添加到本地P的可运行G中
- 如果失败，将G添加到调度器的可运行G里面（加锁），同时将本地P的可运行G的一半添加到调度器的可运行G里面

# 调度器
Go的调度器并不是一个运行在某一个专门的内核线程的程序，而是运行在若干个M中。系统运行时，几乎所有的M都会参与调度器的任务，它们共同实现Go调度器的功能

## 调度流程

<img src="/img/66akf7R5bjgrJ7GnmwCC.png" width="70%"/>

全力查找可运行G：
1. 阶段1：
	1. 获取执行终结器的G。
    可以通过 `runtime.SetFinalizer` 函数设置一个对象的 finalizer。所有的 finalizer都由一个专用的 G负责。调度器会在这个 G空闲的时候，派发新的 finalizer给它执行
	2. 从本地P可运行G列表中获得G
	3. 从调度器可运行G列表中获得G
	4. 从网络I/O轮询器（netpoller）获得G
    这里仅仅只是尝试一下，失败了也不会阻塞
	5. 从其他P的可运行G队列中窃取G
    如果可以，就窃取一半
2. 阶段2：
  1. 获取GC标记任务的G
  如果现在处于GC标记阶段，并且本地P可以用于GC标记任务。那么就会将本地P的GC专用G设为Grunnable
  2. 从调度器可运行G列表中获得G
  再试一下。如果还是失败，**解除本地P和M之间的关联**
  3. 便利全局P列表，如果有P有可运行的G，那么就获得一个空闲的P（从调度器获取），然后重新进入阶段1
  4. 如果处于GC标记阶段，那么就获得一个空闲的P（从调度器获取），同GC专用G关联，进入阶段2的步骤1
  5. 如果netpoller已经初始化，且有过网络I/O操作，则从网络I/O轮询器（netpoller）阻塞获得G
3. 如果依然找不到，那么调度器就会停止当前M。之后被唤醒之后，再次进入“全力查找可运行G”流程

## 用户态阻塞/唤醒
当Goroutine因为Channel操作而阻塞(通过gopark)时，对应的G会被放置到某个wait队列(如channel的waitq)，该G的状态由_Gruning变为_Gwaitting，而M会跳过该G尝试获取并执行下一个G。

当阻塞的G被G2唤醒(通过goready)时(比如channel可读/写)，G会尝试加入G2所在P的runnext，然后再是P Local队列和Global队列。

## 系统调用
### 异步系统调用
异步系统调用，比如 netpoller

![upload successful](/img/6KtXmHaWu5fFt2MSgPEC.png)

G1正在M上面执行并且有3个G在LRQ上等待想要获取M的时间片。netpoller此时空闲。

![upload successful](/img/F783vFWhYGFGNpdwXzU4.png)

G1想要进行network system调用，因此G1移到了netpoller上面然后处理异步调用，一旦G1从M上移到netpoller，M便可以去执行其他LRQ上的G。此时 G2切换到了M上面。

![upload successful](/img/exNxBuvz8BmUexA786Z3.png)

netpoller的异步网络调用完成并且G1回到了P的LRQ上面。一旦G1能够切换回M上，Go的相关代码便能够再次执行。很大好处是，在执行netpoller调用时候，我们不需要其他额外的M。netpoller有一个OS线程能够有效的处理事件循环。

### 同步系统调用
例如：文件系统

![upload successful](/img/gepvfGUKKnQ897X3kPjT.png)

同样，G1正在M上面执行并且有3个G在LRQ上等待想要获取M的时间片。

![upload successful](/img/nr6aumNVkdunUe69bXhc.png)

调度器能够确定G1已经阻塞了M。这时，调度器会从P上拿下来M1，G1依旧在M1上。然后调度器会拿来一个新的M2去服务P。此时LRQ上的G2会上下文切换到M2上。如果已经有一个可用的M了，那么直接用它会比新建一个M要更快。**注意，这个时候，M的数量就会大于P了**

![upload successful](/img/64dqpwp8PYwDRgaE7wsZ.png)

G1的阻塞系统调用结束了。此时G1能够回到LRQ的后面并且能够重新被P执行。M1之后会被放置一边供未来类似的情况使用。

# 系统检测任务sysmon
sysmon的主要任务：
- 在需要时抢占符合条件的P和G（当某个G执行超过10ms）
- 在需要时进行强制GC
- 在需要时清理堆
- 在需要时打印调度器跟踪信息

sysmon会在一个循环里面，一直执行这些任务，直到Go程序的结束。

由于Go调度不像OS调度那样有时间片的概念，因此实际抢占机制要弱很多: Go中的抢占实际上是为G设置抢占标记(g.stackguard0)，当G调用某函数时(更确切说，在通过newstack分配函数栈时)，被编译器安插的指令会检查这个标记，并且将当前G以 `runtime.Gosched` 的方式暂停，并加入到全局队列。

# 参考
[Go 调度模型](https://wudaijun.com/2018/01/go-scheduler/)

[理解golang调度之二 ：Go调度器](https://juejin.im/post/5ce11a39f265da1baf7cbc61#heading-14)
