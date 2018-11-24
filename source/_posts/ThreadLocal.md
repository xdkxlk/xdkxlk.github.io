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
# 深入分析
## ThreadLocalMap
ThreadLocalMap 是在`Thread`中实际存储值的对象。  
ThreadLocalMap是一个定制的哈希映射，只适合维护线程本地值。在ThreadLocal类之外不导出任何操作。类是包私有的，允许在类线程中声明字段。为了帮助处理非常大的长期使用，哈希表条目对键使用弱引用。但是，由于没有使用引用队列，只有当表开始耗尽空间时，过时的条目才会被删除。
## 存储方式
- 内部使用一个`Entry[] table`来保存数据
- `Entry[] table`的大小必须是2的倍数（这有一定的数学依据）
- `Entry[] table`初始大小为16
- 如果使用的空间超过了`Entry[] table`的 2/3 ，那么会将数组大小扩大两倍（`int newLen = oldLen * 2`）
- 存储使用的是开放地址法，如果发现通过hash计算出的位置被占用了，那么就找下一个，一直找到有空的位置
- **`Entry` 保存的是一个 `ThreadLocal` 的 `WeakReference`，和一个value的强引用**

这里贴一下 `Entry` 这个 `ThreadLocalMap` 的内部类，注意这个弱引用
```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    /** The value associated with this ThreadLocal. */
    Object value;

    Entry(ThreadLocal<?> k, Object v) {
        super(k);
        value = v;
    }
}
```

## hash算法
每一个 `ThreadLocal` 在创建之后，它的hash值就已经确定了
```java
private final int threadLocalHashCode = nextHashCode();

/**
 * The next hash code to be given out. Updated atomically. Starts at
 * zero.
 */
private static AtomicInteger nextHashCode =
    new AtomicInteger();

/**
 * The difference between successively generated hash codes - turns
 * implicit sequential thread-local IDs into near-optimally spread
 * multiplicative hash values for power-of-two-sized tables.
 * 连续生成的哈希码之间的区别——将隐式顺序线程本地id转换为几乎最优分布的
 * 乘法哈希值，用于大小为2的幂的表。
 */
private static final int HASH_INCREMENT = 0x61c88647;

/**
 * Returns the next hash code.
 */
private static int nextHashCode() {
    return nextHashCode.getAndAdd(HASH_INCREMENT);
}
```
可以看出，`threadLocalHashCode` 是一个常量，它通过 `nextHashCode()` 函数产生。`nextHashCode()` 函数其实就是在一个 `AtomicInteger` 变量（初始值为0）的基础上每次累加 `0x61c88647`，使用 `AtomicInteger` 为了保证每次的加法是原子操作。而 `0x61c88647` 这个就比较神奇了，它可以使 `hashcode` 均匀的分布在大小为 2 的 N 次方的数组里。（Fibonacci Hashing）  
这里写个代码测试一下
```java
public static void main(String[] args) {
    AtomicInteger nextHashCode = new AtomicInteger();
    int hash_increment = 0x61c88647;
    int size = 16;
    List<Integer> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
        list.add(nextHashCode.getAndAdd(hash_increment) & (size - 1));
    }
    System.out.println("original:" + list);
    Collections.sort(list);
    System.out.println("sort:    " + list);
}
```
```java
// size=16
original:[0, 7, 14, 5, 12, 3, 10, 1, 8, 15, 6, 13, 4, 11, 2, 9]
sort:    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]

// size=32
original:[0, 7, 14, 21, 28, 3, 10, 17, 24, 31, 6, 13, 20, 27, 2, 9, 16, 23, 30, 5, 12, 19, 26, 1, 8, 15, 22, 29, 4, 11, 18, 25]
sort:    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31]

// size=64
original:[0, 7, 14, 21, 28, 35, 42, 49, 56, 63, 6, 13, 20, 27, 34, 41, 48, 55, 62, 5, 12, 19, 26, 33, 40, 47, 54, 61, 4, 11, 18, 25, 32, 39, 46, 53, 60, 3, 10, 17, 24, 31, 38, 45, 52, 59, 2, 9, 16, 23, 30, 37, 44, 51, 58, 1, 8, 15, 22, 29, 36, 43, 50, 57]
sort:    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63]
```
可以看到随着 size 的变化，hashcode 总能均匀的分布。
## ThreadLocalMap.set
```java
private void set(ThreadLocal<?> key, Object value) {

    Entry[] tab = table;
    int len = tab.length;
    // 获得hash值对应于数组的哪儿
    int i = key.threadLocalHashCode & (len-1);

    // 如果当前位置已经有元素了，那么使用开放地址法，找下一个
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        // 获得已经占用的这个元素的Reference的referent属性
        // 如果被gc了，那么这个k会是null
        ThreadLocal<?> k = e.get();

        // 如果k和现在的这个key是同一个“引用”
        // 那么直接替换掉原来的值
        if (k == key) {
            e.value = value;
            return;
        }

        // 如果k为null（被gc了）
        // 清理掉原来的entry，设置新的值
        if (k == null) {
            replaceStaleEntry(key, value, i);
            return;
        }
    }

    // 找到了空的位置
    tab[i] = new Entry(key, value);
    int sz = ++size;
    // 如果没有清理出空的位置（清理掉k为null的entry）
    // 且所占用的空间已经超过总大小的2/3
    // 那么进行扩容
    if (!cleanSomeSlots(i, sz) && sz >= threshold)
        rehash();
}
```
## ThreadLocalMap.cleanSomeSlots
```java
/**
 * 启发式地扫描一些单元格，寻找过时的条目。在添加新元素或删除另一个陈旧元素时调用此函数。
 * 它执行对数次扫描，作为不扫描(快速但保留垃圾)和与元素数量成比例的扫描次数之间的平衡，
 * 扫描的时候将查找所有垃圾，但会导致一些插入花费O(n)时间。
 */
private boolean cleanSomeSlots(int i, int n) {
    boolean removed = false;
    Entry[] tab = table;
    int len = tab.length;
    do {
        i = nextIndex(i, len);
        Entry e = tab[i];
        if (e != null && e.get() == null) {
            // 发现了垃圾，启发式的将扫描范围扩大
            n = len;
            removed = true;
            // i为环形向后访问最近的一个entry为null的位置
            // 详细见下面的expungeStaleEntry
            i = expungeStaleEntry(i);
        }
    } while ( (n >>>= 1) != 0);
    return removed;
}
```
参数
- i  
开始进行脏数据扫描的位置，**注意从代码中可以看出，`cleanSomeSlots`约定 `i` 这个位置一定不是脏数据**
- n  
主要用于扫描控制（scan control），从while中是通过n来进行条件判断的说明n就是用来控制扫描趟数（循环次数）的。在扫描过程中，如果没有遇到脏entry就整个扫描过程持续log2(n)次，log2(n)的得来是因为n >>>= 1，每次n右移一位相当于n除以2。  
如果在扫描过程中遇到脏entry的话就会令n为当前hash表的长度（n=len），再扫描log2(n)趟，注意此时n增加无非就是多增加了循环次数从而通过nextIndex往后搜索的范围扩大，示意图如下
![upload successful](/img/vUlCzs3P21dU0S5hAP7N.png)
按照n的初始值，搜索范围为黑线，当遇到了脏entry，此时n变成了哈希数组的长度（n取值增大），搜索范围log2(n)增大，红线表示。如果在整个搜索过程没遇到脏entry的话，搜索结束，采用这种方式的主要是用于时间效率上的平衡。  
如果是在set方法插入新的entry后调用，n位当前已经插入的entry个数size；如果是在replaceSateleEntry方法中调用n为哈希表的长度len。

## ThreadLocalMap.expungeStaleEntry
```java
/**
 * 清除当前脏数据，并继续向后扫描，清除在扫描过程中的脏数据
 * 一直到找到一个空位置（为null）
 * 返回这个为null的序号
 */
private int expungeStaleEntry(int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;

    // expunge entry at staleSlot
    // 将这个地方的value设为null，value会被gc
    // 再把这个地方的值设为null
    tab[staleSlot].value = null;
    tab[staleSlot] = null;
    size--;

    // Rehash until we encounter null
    Entry e;
    int i;
    // 继续向后面搜索，直到遇见一个为null的entry
    for (i = nextIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = nextIndex(i, len)) {
        ThreadLocal<?> k = e.get();
        if (k == null) {
            // 如果在向后搜索过程中再次遇到脏entry（key被gc了）
            // 那么清除这个entry
            e.value = null;
            tab[i] = null;
            size--;
        } else {
            // 如果不是脏数据
            // 那么重新计算其应该在数组中的位置，并尽量将它挪到离hash值更近一点的地方
            int h = k.threadLocalHashCode & (len - 1);
            if (h != i) {
                // 将现在用的这个位置的entry“引用”清掉
                tab[i] = null;

                // Unlike Knuth 6.4 Algorithm R, we must scan until
                // null because multiple entries could have been stale.
                // 从期望的最优hash位置开始，重新向后找到一个空位置
                while (tab[h] != null)
                    h = nextIndex(h, len);
                // 放到新的位置
                tab[h] = e;
            }
        }
    }
    // 返回遇见的第一个为null的地方
    return i;
}
```
`expungeStaleEntry` 并不是单纯的就是清掉 `staleSlot` 所指的 `entry`，除此之外还做了几件事情：
- 清除当前脏`entry`，将`entry`的`value`置为null，这样gc的时候就会被回收掉
- 然后，继续向后面搜索，如果发现了脏数据，那么继续清理掉
- 如果遇见的不是脏数据，那么重新放置这个数据，让它尽量靠近直接计算出来的最优的hash位置（因为使用的是开放地址法）
- 当遇见了一个为`null`的`entry`，那么函数返回

## cleanSomeSlots小结
cleanSomeSlots的大体流程如下：
- 从i开始（不包括i）开始向后面在log2(n)的范围里面查找脏元素
- 如果发现了脏元素，那么调用`expungeStaleEntry`
	- （expungeStaleEntry开始）
	- 清除当前脏元素
    - 继续向后面扫描，发现了脏元素就清理，不是脏元素那么重新进行hash存储，便于以后访问的加速
    - 一直扫描，直到遇见了一个为`null`的`entry`
    - 返回这个为`null`的`entry`的序号
    - （expungeStaleEntry结束）
	- 重置n为整个数组长度，i赋值为`expungeStaleEntry`返回的为`null`的序号
- 循环直到log2(n)范围扫描完

举个例子：
![upload successful](/img/bgT2iy4fXpKqQUFYPV7r.png)
假设现在i=1，size=10
- `nextInt`为2，这个`entry`为`null`，继续向下面找
- 现在 `i=3`，发现这是一个脏数据，那么调用`expungeStaleEntry`进行清理
- `expungeStaleEntry`清理了 `i=3`这个数据之后，继续工作，清理了 4、5
- `expungeStaleEntry` 发现 6 不是一个脏数据，那么就为它重新找一个尽量好的位置（最接近hash的最优位置），它的hashIndex为8，但是8这个位置已经有数据了，继续向下，发现9是空的，那么现在就把它挪到了9了
- `expungeStaleEntry` 发现 7 是一个空位置，函数返回，返回 7 这个位置
- `cleanSomeSlots` i 现在赋值为 7，由于进行了数据的清理，启发式的将 `n = len` 扩大搜索范围，进入下一个循环

## ThreadLocalMap.replaceStaleEntry
在 `ThreadLocalMap.set` 里面，还调用了 `replaceStaleEntry` 下面来看一看
```java
private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                               int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;
    Entry e;

    // Back up to check for prior stale entry in current run.
    // We clean out whole runs at a time to avoid continual
    // incremental rehashing due to garbage collector freeing
    // up refs in bunches (i.e., whenever the collector runs).
    // 向前找到直到遇见null entry
    // 记录最前面的entryId
    int slotToExpunge = staleSlot;
    for (int i = prevIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = prevIndex(i, len))
        if (e.get() == null)
            slotToExpunge = i;

    // Find either the key or trailing null slot of run, whichever
    // occurs first
    // 向后找，直到遇见null entry
    for (int i = nextIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = nextIndex(i, len)) {
        // e是现在访问的这个entry
        ThreadLocal<?> k = e.get();

        // If we find key, then we need to swap it
        // with the stale entry to maintain hash table order.
        // The newly stale slot, or any other stale slot
        // encountered above it, can then be sent to expungeStaleEntry
        // to remove or rehash all of the other entries in run.
        if (k == key) {
            // 如果找到了一样的key
            // 那么将其value修改
            // Pa
            e.value = value;

            // i是现在访问的这个entry的序号
            // staleSlot是传进来的脏entry的序号
            // 将这个重复key值的entry同哪个脏entry进行交换
            tab[i] = tab[staleSlot];
            tab[staleSlot] = e;

            // Start expunge at preceding stale entry if it exists
            if (slotToExpunge == staleSlot)
                // 如果向前搜索的时候，没有找到脏entry
                // 那么就以当前位置（现在已经发生了交换，是一个脏entry了）开始进行清理
                // Pb
                slotToExpunge = i;
            // 进行清理
            cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
            return;
        }

        // If we didn't find stale entry on backward scan, the
        // first stale entry seen while scanning for key is the
        // first still present in the run.
        // 如果向前查找没有找到脏entry，而在向后查找过程遇到脏entry的话
        // 后面就以此时这个位置作为起点执行cleanSomeSlots
        if (k == null && slotToExpunge == staleSlot)
            // Pc
            slotToExpunge = i;
    }

    // If key not found, put new entry in stale slot
    // 如果在查找过程中，没有找到可以覆盖的entry
    // 那么使用新entry覆盖脏entry
    // 这里将value置为了null，便于gc
    // Pd
    tab[staleSlot].value = null;
    tab[staleSlot] = new Entry(key, value);

    // If there are any other stale entries in run, expunge them
    // 如果在运行中，找到了任何一个脏entry，那么开始清理
    if (slotToExpunge != staleSlot)
    	// Pe
        cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
}
```
下面以几个例子来说明
### 前向有脏entry
#### 后向环形查找找到可覆盖的entry
![upload successful](/img/k0iWK17VB1sWis8biNoT.png)
- 向前找到了脏entry，更新slotToExpunge
- 向后找，找到了key相同的，覆盖并同staleSlot交换（Pa）
- 现在脏entry就到了红色的那个地方，staleSlot现在是新的数据了
- 如果有必要的话（找到了除了staleSlot以外的其他脏数据），以slotToExpunge开始，向后清理
- 函数返回

#### 后向环形查找未找到可覆盖的entry
![upload successful](/img/5CjRvVcTTNmpFFt61zx3.png)
- 同样，向前找到了脏entry，更新slotToExpunge
- 现在向后找，并没有找到同样的key或者脏entry，遇见了null entry
- 将新值覆盖staleSlot那个位置（Pd）
- 如果有必要的话（找到了除了staleSlot以外的其他脏数据），以slotToExpunge开始，向后清理
- 函数返回

### 前向没有脏entry
#### 后向环形查找找到可覆盖的entry
![upload successful](/img/05F53iwpVIGWslVrJxsY.png)
- 向前找，没有找到脏entry，现在的slotToExpunge等于staleSlot
- 向后找，找到了一个脏数据，那么由于向前搜索的时候没有找到脏数据（slotToExpunge没有变），将slotToExpunge赋值为当前序号（Pc）
- 继续向后找，找到了一个重复的key，覆盖并同staleSlot交换（Pa）。如果在整个过程中还是没有遇到脏entry的话，将slotToExpunge赋值为现在的序号（Pb）
- 如果有必要的话（找到了除了staleSlot以外的其他脏数据），以slotToExpunge开始，向后清理
- 函数返回

#### 后向环形查找未找到可覆盖的entry
![upload successful](/img/eZ5YI5Efg7HZyXcsP2aL.png)
- 向前找，没有找到脏entry，现在的slotToExpunge等于staleSlot
- 向后找，如果找到了一个脏数据，那么由于向前搜索的时候没有找到脏数据（slotToExpunge没有变），将slotToExpunge赋值为当前序号（Pc）
- 遇到了null entry，结束向后找
- 没有找到重复的key，将新值覆盖staleSlot那个位置（Pd）
- 如果有必要的话（找到了除了staleSlot以外的其他脏数据），以slotToExpunge开始，向后清理
- 函数返回

## ThreadLocalMap.getEntry/getEntryAfterMiss
这个两个一起说，因为在前文的基础上，这两个比较简单了。直接看代码
```java
private Entry getEntry(ThreadLocal<?> key) {
    // 获得hashId
    int i = key.threadLocalHashCode & (table.length - 1);
    Entry e = table[i];
    if (e != null && e.get() == key)
        // 如果找到了，直接返回
        return e;
    else
        // 如果没有，那么向后继续找
        return getEntryAfterMiss(key, i, e);
}

private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
    Entry[] tab = table;
    int len = tab.length;

    // 一直找，直到遇见null entry
    // 如果遇见了null entry，那么说明没有对应的值
    while (e != null) {
        ThreadLocal<?> k = e.get();
        if (k == key)
            return e;
        if (k == null)
            // 如果发现了一个脏数据，那么进行清理
            expungeStaleEntry(i);
        else
            i = nextIndex(i, len);
        e = tab[i];
    }
    return null;
}
```
## ThreadLocalMap.remove
remove比较简单
```java
private void remove(ThreadLocal<?> key) {
    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        if (e.get() == key) {
            // 调用Reference clear方法
            e.clear();
            // 从当前开始，向后清理
            expungeStaleEntry(i);
            return;
        }
    }
}
```
# 思考
在`replaceStaleEntry`中为什么只有在向后搜索的时候，才会有把新值覆盖旧值的操作？  
回看 `set` 函数，它是从最优的hashId开始向后找的，故，应该尽量的将数据放在最优的hashId向右距离最近的地方。而如果在向前搜索的时候将数据放了进去，那么进行查询操作的时候，距离可能会变得比较远。而且，在 `set` 中，是从最优位置开始，如果由重复的，那么就直接覆盖了，如果发现了第一个脏数据，就会调用本函数，所以，向前查找出来的脏数据会是在最优位置左边。  

在`replaceStaleEntry`中为什么向后查找的时候，如果发现脏数据并不覆盖？  
这要秉承一个思想，放的位置离最优位置向后距离越近越好。现在我已经有了一个更近的位置了，那就是 `staleSlot`，为什么还要放这个更远一点的位置呢？之所以同样的key值的操作是同 `staleSlot` 交换也是这个道理。

`getEntryAfterMiss` 里，发现了脏`entry`，`expungeStaleEntry` 调用之后，i这个地方不是被清理了么，那么是不是就意味着一旦发现了一个脏数据就会跳出循环，返回null了？  
不是的。注意`expungeStaleEntry`除了有脏entry的清理功能，还有一个数据重排列的功能（当遇见的不是一个脏数据的时候）。那么就是意味着，如果 i 这个位置真的应该是有数据，只是由于开始位置被占了挪到后面去了的话，那么在`expungeStaleEntry`重排列数据之后，这个位置就会有值。  

会不会出现要获得的值前面插了个`null`从而`get`不到的情况？   
在ThreadLocalMap的 `set` `replaceStaleEntry` 中，都是最终如果其他各种更优选择没有的情况下，那么就会使用离hashId最近的那个null entry。  
在 `remove` 的时候，也会调用`expungeStaleEntry`进行重拍，尽量使数据离最优的hashId进，尽量让数据紧凑，所有不会有这种情况发生。

# 小结
每一个线程都有一个 Map，对于每一个 `ThreadLocal` 对象，调用其 get/set 实际上就是以 `ThreadLocal` 对象为键读写当前线程的 Map，这样就实现了每一个线程都有自己独立副本的效果。  
但是，要注意的是，`ThreadLocal` 并不是一种保证线程安全的手段。假如多个线程之间共享一个 ArrayList，那么这个 ArrayList 并不是线程安全的。（每个线程单独保存的是独立的“引用”，但是这个“引用”指向的依然是同一个内存空间）
# 参考
http://mahl1990.iteye.com/blog/2347932  
https://www.jianshu.com/p/250798f9ff76  
https://www.cnblogs.com/windliu/p/7623369.html  
https://stackoverflow.com/questions/17968803/threadlocal-memory-leak  
https://blog.csdn.net/liu1pan2min3/article/details/80236105  
https://www.cnblogs.com/zhangjk1993/archive/2017/03/29/6641745.html#\_label3\_2  
[一篇文章，从源码深入详解ThreadLocal内存泄漏问题](https://www.jianshu.com/p/dde92ec37bd1)

[>>>是什么](https://bbs.csdn.net/wap/topics/270060707)