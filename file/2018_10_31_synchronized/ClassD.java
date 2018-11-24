package com.lk.sync;

/**
 * @author lk
 */
public class ClassD {

    public static void f1() throws Exception {
        synchronized (ClassD.class) {
            System.out.println(Thread.currentThread().getName() + " f1 start");
            Thread.sleep(1000);
            f3();
            System.out.println(Thread.currentThread().getName() + " f1 end");
        }
    }

    public static void f2() throws Exception {
        synchronized (ClassD.class) {
            System.out.println(Thread.currentThread().getName() + " f2 start");
            Thread.sleep(1000);
            System.out.println(Thread.currentThread().getName() + " f2 end");
        }
    }

    public static void f3() throws Exception {
        synchronized (ClassD.class) {
            System.out.println(Thread.currentThread().getName() + " f3 start");
            Thread.sleep(1000);
            System.out.println(Thread.currentThread().getName() + " f3 end");
        }
    }

    static void case1() {
        System.out.println("case1=================================");
        Thread t1 = new Thread(() -> {
            try {
                ClassD.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassD.f2();
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
                ClassD.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ClassD.f3();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case3() {
        System.out.println("case3=================================");
        ClassD classD1 = new ClassD();
        ClassD classD2 = new ClassD();
        Thread t1 = new Thread(() -> {
            try {
                classD1.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classD2.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    public static void main(String[] args) {
        case2();
    }
}
