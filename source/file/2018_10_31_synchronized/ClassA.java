package com.lk.sync;

/**
 * @author lk
 */
public class ClassA {

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

    public synchronized void f3() throws Exception {
        System.out.println(Thread.currentThread().getName() + " f3 start");
        Thread.sleep(1000);
        System.out.println(Thread.currentThread().getName() + " f3 end");
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
        case2();
    }
}
