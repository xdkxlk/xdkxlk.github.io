package com.lk.sync;

/**
 * @author lk
 */
public class ClassE {

    public void f1() throws Exception {
        synchronized (this) {
            System.out.println(Thread.currentThread().getName() + " f1 start");
            Thread.sleep(1000);
            f3();
            System.out.println(Thread.currentThread().getName() + " f1 end");
        }
    }

    public void f2() throws Exception {
        synchronized (this) {
            System.out.println(Thread.currentThread().getName() + " f2 start");
            Thread.sleep(1000);
            System.out.println(Thread.currentThread().getName() + " f2 end");
        }
    }

    public synchronized void f3() throws Exception {
        synchronized (this) {
            System.out.println(Thread.currentThread().getName() + " f3 start");
            Thread.sleep(1000);
            System.out.println(Thread.currentThread().getName() + " f3 end");
        }
    }

    static void case1() {
        System.out.println("case1=================================");
        final ClassE classE = new ClassE();
        Thread t1 = new Thread(() -> {
            try {
                classE.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classE.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case2() {
        System.out.println("case2=================================");
        final ClassE classE1 = new ClassE();
        final ClassE classE2 = new ClassE();
        Thread t1 = new Thread(() -> {
            try {
                classE1.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classE2.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    public static void main(String[] args) {
        case1();
    }
}
