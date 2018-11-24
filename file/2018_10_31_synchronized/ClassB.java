package com.lk.sync;

/**
 * @author lk
 */
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
