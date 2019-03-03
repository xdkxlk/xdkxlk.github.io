title: ArrayList
author: xdkxlk
tags:
  - 源码阅读
categories:
  - Java
date: 2018-11-10 10:03:00
---
版本 jdk1.8
# 概览、描述
Java Collections Framework 成员之一。  
ArrayList是List的可变大小的数组的实现（resizable-array implementation）。实现了所有的list的操作，**并可以存储所有的元素，包括 `null`。**内部存储使用数组实现（array），同时也还提供了可以操作内部存储的数组的大小的方法。ArrayList类似于Vector，除了不是同步的。  
`size`, `isEmpty`, `get`, `set`, `iterator`, `listIterator` 这些操作都是在常数时间（constant time）完成。`add` 操作为 amortized constant time（均摊常数时间）,也就是说添加 n 个元素需要 O(n) 的时间。大致上说，所有的其他操作都是在线性时间。The constant factor is low compared to that for the LinkedList implementation  
每一个ArrayList都有一个容量（`capacity`）。`capacity` 是指存储列表中元素的数组的大小。它总是至少和列表的大小一样（等于或大于）。当元素被添加到ArrayList中时，其容量会自动增长。The details of the growth policy are not specified beyond the fact that adding an element has constant amortized time cost.  
在添加大量元素之前，可以调用 `ensureCapacity ` 来增加 `capacity`。这样可以减少（may reduce）增量再分配的数量。  
需要注意的是，**ArrayList并不是线程安全的**。如果多个线程同时访问ArrayList实例，并且至少一个线程在结构上修改列表，则必须在外部对其进行同步。（**结构修改**是指任何一个操作增加、删除一个或者多个元素，或者显示的修改内部存储数组的大小；仅仅这是设置元素的值不是结构修改）。应该自行对于ArrayList进行线程同步，或者使用 `Collections.synchronizedList` 包裹ArrayList。这最好在创建时完成，以防止意外的非同步访问列表。
```java
List list = Collections.synchronizedList(new ArrayList(...));
```
由 `iterator` 和 `listIterator` 返回的迭代器是fail-fast的（故障快速失败）。如果在创建迭代器之后的任何时间以任何方式（除了通过迭代器自己的remove或add方法之外）对列表进行结构修改，迭代器将抛出一个ConcurrentModificationException。因此，在面临并发修改时，迭代器会快速的失败（fails quickly and cleanly），而不会在未来不确定的时间冒任意、非确定性行为的风险。  
但是要注意的是，fail-fast并不能在多线程的情况下完全保证，在并发情况下并不能作为hard guarantees。Fail-fast iterators会尽力（on a best-effort basis）的抛出ConcurrentModificationException。因此，编写依赖于此异常的程序来确保其正确性是错误的：**迭代器的快速失败行为应该只用于检测bug。**  
# 成员
## elementData
核心就是一个 `elementData` 
```java
transient Object[] elementData;
```
存储数组列表元素的数组缓冲区。数组的容量是这个数组缓冲器的长度。当添加第一个元素时，任何带有 `elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA` 的空ArrayList将被扩展为 `DEFAULT_CAPACITY` 。  
注意，这个 `elementData` 的访问级别不是 private，javaDoc里面说是为了便于嵌套的类的访问（non-private to simplify nested class access）。
## size
ArrayList的大小，有多少个元素
```java
private int size;
```
## DEFAULTCAPACITY_EMPTY_ELEMENTDATA
描述空实例的共享对象。用这个以将此与 `EMPTY_ELEMENTDATA` 区分开来，以知道在添加第一个元素时要膨胀多少。
```java
private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};
```
## EMPTY_ELEMENTDATA
描述空数组的共享对象
```java
private static final Object[] EMPTY_ELEMENTDATA = {};
```
## DEFAULT_CAPACITY
默认初始化 `capacity`
```java
private static final int DEFAULT_CAPACITY = 10;
```
## MAX_ARRAY_SIZE
数组的最大大小  
有些VM在array里面保存了一些头，如果试图请求一个很大的array可能会导致`OutOfMemoryError`
```java
/**
   * The maximum size of array to allocate.
   * Some VMs reserve some header words in an array.
   * Attempts to allocate larger arrays may result in
   * OutOfMemoryError: Requested array size exceeds VM limit
*/
private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
```
## java.util.AbstractList#modCount
```java
protected transient int modCount = 0;
```
# 对象的构造
ArrayList 有3个构造函数
## 无参构造函数
```java
public ArrayList() {
	this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
}
```
这个将 `elementData` 初始化为 `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` 代表空对象，而在后面的逻辑中，**如果是一个空对象，那么它的默认 `capacity` 是10**
## ArrayList(int initialCapacity)
```java
public ArrayList(int initialCapacity) {
    if (initialCapacity > 0) {
        this.elementData = new Object[initialCapacity];
    } else if (initialCapacity == 0) {
    		// 注意这里
        this.elementData = EMPTY_ELEMENTDATA;
    } else {
        throw new IllegalArgumentException("Illegal Capacity: "+
                initialCapacity);
    }
}
```
这个构造函数传入了一个初始的 `capacity` ，传的多大，那么内部的数组开得就有多大。**注意，如果为0，那么初始化为一个 `EMPTY_ELEMENTDATA` 空数组，而不是 new 了一个空的**，个人觉得，这样的好处是可以方便的将空对象、空数组方便的区分开来。
## ArrayList(Collection<? extends E> c)
```java
public ArrayList(Collection<? extends E> c) {
    elementData = c.toArray();
    if ((size = elementData.length) != 0) {
        // 注意这里，bug6260652
        // c.toArray might (incorrectly) not return Object[] (see 6260652)
        if (elementData.getClass() != Object[].class)
        	// 返回生成一个 Object[]
            elementData = Arrays.copyOf(elementData, size, Object[].class);
    } else {
        // 如果是空的，那么替换为EMPTY_ELEMENTDATA
        // replace with empty array.
        this.elementData = EMPTY_ELEMENTDATA;
    }
}
```
这个方法就是传入一个collection，并初始化为collection里面的元素。如果collection是空的，那么就初始化为 `EMPTY_ELEMENTDATA`。  
**注意，代码里面提到了一个jdk的bug，6260652**(参考，[Jdk Bug6260652](/2018/11/12/Jdk-Bug6260652/))  
**还可以注意到，类型判断使用的是 `elementData.getClass() != Object[].class` **，这么写的原因参考 [java中的instanceof和getClass()](/2018/11/12/java%E4%B8%AD%E7%9A%84instanceof%E5%92%8CgetClass/)

# 附录
## Amortized Constant Time
[算法中Amortised time的理解](http://www.cnblogs.com/zwCHAN/p/3772246.html)  
[constant-amortized-time ](https://stackoverflow.com/questions/200384/constant-amortized-time)  
[ArrayList集合实现RandomAccess接口有何作用？为何LinkedList集合却没实现这接口](https://blog.csdn.net/weixin_39148512/article/details/79234817)