title: ThreadLocal
author: xdkxlk
tags:
  - 源码阅读
categories:
  - Java
date: 2018-11-19 18:56:00
---
# 作用及基本用法
## 代码
```java
public class ThreadLocalBasic {

    private static ThreadLocal<Integer> local = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        Thread child = new Thread(() -> {
            System.out.println("child init " + local.get());
            local.set(200);
            System.out.println("child final " + local.get());
        });

        local.set(100);
        child.start();
        child.join();
        System.out.println("main final " + local.get());
    }
}
```
## 输出
```
main init null
child init null
child final 200
main final 100
```
## 分析
ThreadLocal就是说，**每一个都有同一个变量的独有拷贝**。从结果可以看出，main线程对于变量的设置对于child线程不起作用，child线程对local变量的改变也不会影响main线程。**他们虽然访问的都是同一个local，但是每一个线程都有自己的值，这就是线程本地变量。**
# 基本实现原理
## set
```java
public void set(T value) {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
}

ThreadLocalMap getMap(Thread t) {
    return t.threadLocals;
}

void createMap(Thread t, T firstValue) {
    t.threadLocals = new ThreadLocalMap(this, firstValue);
}
```
可以看出，每一个线程对象 `Thread` 都有一个 Map，类型为 `ThreadLocalMap`，调用 `set` 实际上是在线程自己的 Map 里面设置了一个值，键为当前的 `ThreadLocal` 对象。这个 Map 不同于一般的 Map，它的键类型为 `WeakReference<ThreadLocal<?>>`。
## get
```java
public T get() {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null) {
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            @SuppressWarnings("unchecked")
            T result = (T)e.value;
            return result;
        }
    }
    return setInitialValue();
}
```
`get` 就是访问 `Thread` 的 Map，以 `ThreadLocal` 对象为键从 Map 中获取value，如果 Map 中没有，则调用 `setInitialValue`
## setInitialValue
```java
private T setInitialValue() {
    T value = initialValue();
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
    return value;
}
```
其中就是调用 `initialValue` 获得初始值
# 小结
每一个线程都有一个 Map，对于每一个 `ThreadLocal` 对象，调用其 get/set 实际上就是以 `ThreadLocal` 对象为键读写当前线程的 Map，这样就实现了每一个线程都有自己独立副本的效果。  
但是，要注意的是，`ThreadLocal` 并不是一种保证线程安全的手段。假如多个线程之间共享一个 ArrayList，那么这个 ArrayList 并不是线程安全的。（每个线程单独保存的是独立的“引用”，但是这个“引用”指向的依然是同一个内存空间）