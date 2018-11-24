package com.lk.sync;

/**
 * @author lk
 */
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
        System.out.println("case2=================================");
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
        System.out.println("case3=================================");
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
        case4();
    }
}
