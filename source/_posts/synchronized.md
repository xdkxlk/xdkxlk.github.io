title: synchronized
author: xdkxlk
tags: []
categories:
  - Java
date: 2018-10-31 20:31:00
---
# 锁在非静态方法上
## 测试代码
[代码](/file/2018_10_31_synchronized/ClassA.java)
<!-- more -->
```java
public class ClassA {

    public synchronized void f1() throws Exception {
        System.out.println("f1 start");
        Thread.sleep(1000);
        f3();
        System.out.println("f1 end");
    }

    public synchronized void f2() throws Exception {
        System.out.println("f2 start");
        Thread.sleep(1000);
        System.out.println("f2 end");
    }

    public synchronized void f3() throws Exception {
        System.out.println("f3 start");
        Thread.sleep(1000);
        System.out.println("f3 end");
    }


    static void case1(){
        System.out.println("case1=================================");
        final ClassA classA = new ClassA();
        Thread t1 = new Thread(() -> {
            try {
                classA.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classA.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case2(){
        System.out.println("case2=================================");
        final ClassA classA1 = new ClassA();
        final ClassA classA2 = new ClassA();
        Thread t1 = new Thread(() -> {
            try {
                classA1.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classA2.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    public static void main(String[] args) {
        case1();
        // case2();
    }
}
```
```
case1=================================
Thread-0 f1 start
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
Thread-1 f2 start
Thread-1 f2 end
```
```
case2=================================
Thread-0 f1 start
Thread-1 f1 start
Thread-0 f3 start
Thread-1 f3 start
Thread-0 f3 end
Thread-1 f3 end
Thread-1 f1 end
Thread-0 f1 end
```
## 结果分析
- 可重入的
- 锁的是某一个具体的对象实例的所有方法
- 对于某个对象实例，同一时间只能调用这个实例的其中的一个方法

# 锁在静态方法上
## 测试代码
[代码](/file/2018_10_31_synchronized/ClassB.java)
```java
public class ClassB {

    public synchronized static void f1() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f1 start");
        Thread.sleep(1000);
        f3();
        System.out.println(Thread.currentThread().getName() + " f1 end");
    }

    public synchronized static void f2() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f2 start");
        Thread.sleep(1000);
        System.out.println(Thread.currentThread().getName() + " f2 end");
    }

    public synchronized static void f3() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f3 start");
        Thread.sleep(1000);
        System.out.println(Thread.currentThread().getName() + " f3 end");
    }

    static void case1() {
        System.out.println("case1=================================");
        Thread t1 = new Thread(() -> {
            try {
                ClassB.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassB.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case2() {
        System.out.println("case2=================================");
        Thread t1 = new Thread(() -> {
            try {
                ClassB.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassB.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case3() {
        System.out.println("case3=================================");
        ClassB classB1 = new ClassB();
        ClassB classB2 = new ClassB();
        Thread t1 = new Thread(() -> {
            try {
                classB1.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classB2.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    public static void main(String[] args) {
        case3();
    }
}
```
## 测试结果
```
case1=================================
Thread-0 f1 start
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
Thread-1 f2 start
Thread-1 f2 end
```
```
case2=================================
Thread-0 f1 start
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
Thread-1 f1 start
Thread-1 f3 start
Thread-1 f3 end
Thread-1 f1 end
```
```
case3=================================
Thread-0 f1 start
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
Thread-1 f2 start
Thread-1 f2 end
```
## 结果分析
- 可重入的
- 锁的是所有加锁了的静态方法
- 无论是对于同一个对象 (classB1.f1(), classB1.f2()) 还是不同是对象 (classB1.f1(), classB2.f2())，或者没有对象(CLassB.f1(), ClassB.f2())（因为static不属于任何一个对象），同一时间都只能有一个调用其中的一个方法

# 锁在静态方法上，和不在静态方法上同时存在
## 测试代码
[代码](/file/2018_10_31_synchronized/ClassC.java)
```java
public class ClassC {

    public synchronized void f1() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f1 start");
        Thread.sleep(1000);
        f3();
        System.out.println(Thread.currentThread().getName() + " f1 end");
    }

    public synchronized void f2() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f2 start");
        Thread.sleep(1000);
        System.out.println(Thread.currentThread().getName() + " f2 end");
    }

    public synchronized static void f3() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f3 start");
        Thread.sleep(1000);
        System.out.println(Thread.currentThread().getName() + " f3 end");
    }
    
    public synchronized static void f4() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f4 start");
        Thread.sleep(1000);
        System.out.println(Thread.currentThread().getName() + " f4 end");
    }

    static void case1(){
        System.out.println("case1=================================");
        final ClassC classC = new ClassC();
        Thread t1 = new Thread(() -> {
            try {
                classC.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassC.f3();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case2(){
        System.out.println("case1=================================");
        final ClassC classC = new ClassC();
        Thread t1 = new Thread(() -> {
            try {
                classC.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classC.f3();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case3(){
        System.out.println("case1=================================");
        final ClassC classC = new ClassC();
        Thread t1 = new Thread(() -> {
            try {
                classC.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassC.f3();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }
    
    static void case4(){
        System.out.println("case4=================================");
        final ClassC classC = new ClassC();
        Thread t1 = new Thread(() -> {
            try {
                classC.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassC.f4();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }
    
    public static void main(String[] args) {
        case3();
    }
}
```
## 测试结果
```
case1=================================
Thread-1 f3 start
Thread-0 f1 start
Thread-1 f3 end
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
```
```
case2=================================
Thread-1 f3 start
Thread-0 f1 start
Thread-1 f3 end
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
```
```
case3=================================
Thread-0 f2 start
Thread-1 f3 start
Thread-0 f2 end
Thread-1 f3 end
```
```
case4=================================
Thread-1 f4 start
Thread-0 f1 start
Thread-1 f4 end
Thread-0 f3 start
Thread-0 f3 end
Thread-0 f1 end
```
## 结果分析
- 非静态方法的锁 和 静态方法上的锁互不影响
- 意味着这两个会并行执行
- 如果在 非静态方法 中调用了 静态方法，同时有其他地方调用了静态方法，那么会相互之间竞争静态方法的使用权

# 静态方法中 synchronized (xxx.class)
[代码](/file/2018_10_31_synchronized/ClassD.java)  
代码就不粘贴了，直接说结论。执行方式和 `public synchronized static void` 一样
# synchronized (this)
[代码](/file/2018_10_31_synchronized/ClassE.java) 
代码就不粘贴了，直接说结论。执行方式和 `public synchronized void` 一样
# 总结
![upload successful](/img/52Xe0M5SIf3YvRd4BFh7.png)
[Java Synchronized Block for .class
](https://stackoverflow.com/questions/2056243/java-synchronized-block-for-class)  
- 个人理解，一个 ClassE.class 就类似于一个变量，`synchronized(ClassE.class)`就是在这个唯一的变量上加锁，所以如果在同一个类中，既有`synchronized(ClassE.class)`又有`synchronized(ClassD.class)`，那么这两个方法其实是可以并行执行的；如果在加在同一个`ClassE.class`上，无论是不是静态方法，甚至不是一个类（例如一个在`ClassE`，一个在`ClassF`），都会相互竞争。  
- `synchronized(this)`的锁和`synchronized(ClassE.class)`的锁不是一把锁。  
- `synchronized(this)` 在这个对象上加锁，仅仅是指的是内存中，这一个对象，跟其他同类型的其他对象没有关系。同一时间，只能有一个线程拿到这个对象的锁以运行代码。