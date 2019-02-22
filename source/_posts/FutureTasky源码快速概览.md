title: FutureTask源码快速概览
author: xdkxlk
date: 2019-02-22 18:48:56
tags:
---
jdk 1.8
# 基本使用方法
```java
Callable<Integer> callable = () -> {
    // 模拟实现
    int num = ThreadLocalRandom.current().nextInt(10);
    Thread.sleep(num * 1000);
    return num;
};

FutureTask<Integer> futureTask = new FutureTask<>(callable);

// 启动线程
Thread t = new Thread(futureTask);
t.start();

System.out.println("start...");
// 获得结果
System.out.println(futureTask.get());
```
`FutureTask` 实现了 `Future`，在 `Future` 中有这么几个控制的方法：
- get方法：获取计算结果（如果还没计算完，也是必须等待的）
- cancel方法：还没计算完，可以取消计算过程
- isDone方法：判断是否计算完
- isCancelled方法：判断计算是否被取消

# 类的继承结构
![upload successful](/img/8KHHswhjDs2ubyu28BpG.png)

# 任务状态的流转
`FutureTask` 定义了7个任务的执行状态，他们之间的流转如下  
这里面要注意一下这个 `NEW`， 它并不是意味着刚刚创建但还没有运行，它其实代表刚刚创建和正在运行的状态  
同时，`COMPLETING` 和 `INTERRUPTING` 是两个瞬时状态，存在的时间很短

- NEW -> COMPLETING -> NORMAL  
任务顺利完成
- NEW -> COMPLETING -> EXCEPTIONAL  
任务出现异常
- NEW -> CANCELLED  
任务被取消。如果调用 `cancel(false)`，那么并不会 `interrupt` 线程，子线程其实还是会继续执行的，只不过 get 会不再阻塞，抛出 `CancellationException`
- NEW -> INTERRUPTING -> INTERRUPTED  
任务被中断。如果调用 `cancel(true)`，子线程会被 `interrupt`，get 会抛出 `CancellationException`

# 线程是如何执行的
FutureTask也是一个Runnable，看看它的run方法
```java
public void run() {
    //确保task不会被重复执行
    if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                    null, Thread.currentThread()))
        return;
    try {
        Callable<V> c = callable;
        if (c != null && state == NEW) {
            V result;
            boolean ran;
            try {
                //执行callable的call方法获取结果
                result = c.call();
                ran = true;
            } catch (Throwable ex) {
                result = null;
                ran = false;
                setException(ex);
            }
            if (ran)
                set(result);
        }
    } finally {
        runner = null;
        //线程运行时已经接收到了中断请求，测试state为INTERRUPTING状态，需要确保state
        //变成INTERRUPTED状态。INTERRUPTING->thread.interrupt()->INTERRUPTED
        int s = state;
        if (s >= INTERRUPTING)
            handlePossibleCancellationInterrupt(s); //内部自旋使用Thread.yeild()
    }
}
```
run 方法里面其实就是调用 callable 获得结果并保存下来，如果有异常也保存下来。  
如果执行成功，那么调用 set 保存运行结果，在 set 中调用 finishCompletion 唤醒阻塞等待结果的线程。  
```java
// 设置执行结果
protected void set(V v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }
```
如果出现异常，那么保存异常信息。但这里要注意的是，保存的时候判断了仅仅保存的是在 NEW 状态下产生的异常，所以，如果是被 cancell(true) 打断的并不会保存这个异常
```java
// 保存异常信息
protected void setException(Throwable t) {
        // 这里判断了状态
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }
```
这里面还有一个 `finishCompletion`，它的主要功能是唤醒阻塞的线程。这个详细的后面再说，个人觉得这是 `FutureTask` 很有亮点的东西。
# 是如何获得结果的
结果是通过 `get` 方法获得的
```java
public V get() throws InterruptedException, ExecutionException {
        int s = state;
        if (s <= COMPLETING)
        	// 如果任务还没有结束，那么进入阻塞
            s = awaitDone(false, 0L);
        // 任务结束了，那么根据状态返回结果或者抛出异常
        return report(s);
    }
```
核心是 `awaitDone` 方法
```java
private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            //等待线程被中断了，移除等待节点
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            //计算完成，返回
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            //计算已经COMPLETING，接下很短的时间会被设置成NORMAL（纯CPU计算，无等待），并唤醒等待线程
            //所以这里使用了Thread.yield () 尝试让出线程调度资源，而不是去挂起线程。
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                //加入等待队列的头部
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            else if (timed) {
                nanos = deadline - System.nanoTime();
                //等待超时，将自己从等待队列中移除，并返回当前的状态。
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                //将线程挂起等待被唤醒，或者超时
                LockSupport.parkNanos(this, nanos);
            }
            else
                //将线程挂起等待被唤醒。
                LockSupport.park(this);
        }
    }
```
`WaitNode` 其实是一个链表，记录了阻塞的所有线程
```java
static final class WaitNode {
	volatile Thread thread;
	volatile WaitNode next;
	WaitNode() { thread = Thread.currentThread(); }
}
```
awaitDone 其实是自旋和阻塞相结合的方式。  
如果发现马上就要结束了（处于`COMPLETING`状态），那么就没有阻塞了，就 `yield` 尝试让出 CPU，并自旋。  
如果发现没有结束，那么使用 CAS 的方式使用头插法加入到 `WaitNode waiters` 链表中，然后使用 `LockSupport.park` 挂起。当被唤醒的时候，说明任务已经完成、取消或者打断了。
# 回到finishCompletion
`finishCompletion` 的主要工作就是遍历挂起的线程的链表，并唤醒他们
```java
private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;        // to reduce footprint
}
```
首先使用 CAS 将waiters设置成null，如果成功，则开始进行遍历，唤醒线程。  
注意那个 `done()` 方法，这里其实是一个钩子，用于扩展。CompletionService就使用了这个方法将计算结果放入BlockingQueue。
# 总结
过了一下主要的几个方法，大致知道了 FutureTask 是怎么实现的。  
核心有一个 WaitNode 记录已经挂起的线程，当完成的时候，遍历这个链表唤醒这些线程。  
整个实现都是无锁的，通过 CAS 实现。  
同时还知道了，对于存在很短的的状态的等待，可以使用 自旋 + `Thread.yield()` 的方式进行等待
