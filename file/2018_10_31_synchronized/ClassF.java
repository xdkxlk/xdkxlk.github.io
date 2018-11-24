package com.lk.sync;

/**
 * @author lk
 */
public class ClassF {

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

    public void f3() throws Exception {
        synchronized (ClassF.class) {
            System.out.println(Thread.currentThread().getName() + " f3 start");
            Thread.sleep(1000);
            System.out.println(Thread.currentThread().getName() + " f3 end");
        }
    }

    public void f4() throws Exception {
        synchronized (String.class) {
            System.out.println(Thread.currentThread().getName() + " f4 start");
            Thread.sleep(1000);
            System.out.println(Thread.currentThread().getName() + " f4 end");
        }
    }

    static void case1() {
        System.out.println("case1=================================");
        final ClassF classF = new ClassF();
        Thread t1 = new Thread(() -> {
            try {
                classF.f1();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classF.f4();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();
    }

    static void case2() {
        System.out.println("case1=================================");
        final ClassF classF = new ClassF();
        Thread t1 = new Thread(() -> {
            try {
                classF.f2();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                classF.f4();
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
