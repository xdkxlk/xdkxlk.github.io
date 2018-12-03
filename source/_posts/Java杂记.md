title: Java杂记
author: xdkxlk
tags: []
categories:
  - Java
date: 2018-11-27 16:05:00
---
# 基础编程
## 数字和范围
| 类型名 | 取值范围 | 位数 | 字节数 |
| ------ | ------ | ---- | --- |
| byte | -2^7 ~ 2^7 - 1 | 8位 | 1字节 |
| short | -2^15 ~ 2^15 - 1 | 16位 | 2字节 |
| char | 0 ~ 65535 | 16位 | 2字节 (无符号的两个字节) |
| int | -2^31 ~ 2^31 - 1 | 32位 | 4字节 |
| long | -2^63 ~ 2^63 - 1 | 64位 | 8字节 |

怎么算出来的？  
以 int 为例：  
总共32位，最高位为符号位，所以数字有31位。  
假设我们的int类型只有2位的二进制位，那么它能表示-1,0,1；别忘了，这可是两位二进制，理论上应该至少表示4个数，所以，为了能让内存充分利用起来，应当有-2，-1,0,1或者-1,0,1,2。但是，计算机科学家们显然是选择了-2，-1,0,1这种做法。  
- -2的二进制是11
- -1的二进制是10
- 0的二进制是00
- 1的二进制是01

所以32位的int数据所能表示的数值当是负的2的31次方~2的31次方减1.
## 赋值
```java
// 错误代码
long a = 3147483649;
// 由于3147483649超过了int的范围，所以报错
// 正确代码
long a = 3147483649L;

// 错误代码
float b = 1.1;
// java默认小数为double
// 正确代码
float b = 1.1F;

// 错误代码
int[] c = new int[3]{1,2,3};
// 不能又定义数组长度，又给初始值
// 正确代码
int[] c = new int[3];
int[] c = new int[]{1,2,3};
```
## 运算符号
### &, &&, |, ||
&，与  
&&，短路与，前面为false，后面就不再执行  
|，或  
||，短路或，前面为true，后面就不再执行  

从逻辑的意思上来说，与和短路与没有区别，或和短路或也一样。只不过在于继不继续后面的操作。

## 条件语句
### switch
**switch 里面只能是，char, byte, short, int, Character, Byte, Short, Integer, String, 枚举类，没有long, float, double**  
switch语句的转换与具体的系统实现相关。如果分支比较少，可能会使用跳转指令（if 实现的方式），如果分支比较多，那么会采用跳转表的方式实现，这种方式更加的高效。跳转表是一个类似于下面的一个表：

| 条件值 | 跳转地址 |
| --- | --- |
| 值1 | 代码块1的位置 |
| 值2 | 代码块2的位置 |
| ... | ...... |
| 值n | 代码块n的位置 |

其中，**值必须为整数，而且按照大小顺序排序。**这样可以通过二分法的方式进行查找。**如果值是连续的，还可以进行优化，优化为一个数组**，连表都不用查了，直接通过下标访问。即使不是连续的，但是比较密集，也可以用数组优化，没有的用一个特殊的标识表示一下就好了。  
代码中，case并不要求要排序，编译器会进行排序。前面说，是通过值来确定的，那么这个值是怎么来的呢？byte, short, int本来就是整数；char本质上也是整数；String会先hashCode转换为整数，但是又可能两个不同的String hashCode一样，所以跳转之后会再根据String的内容进行比较。  
**要注意的是，跳转表的值一般是32位的，装不下long，所以switch里面不能是long**
# 字符编码
## 常见非Unicode编码
- ASCII是基础，使用一个字节表示，最高位为0，其他7位表示128个字符。其他编码都是兼容ASCII的，使用最高位为1来区别
- 西欧使用 Windows-1252，高位为1，使用一个字节，增加了额外128个字符。ISO 8859-1已经被 Windows-1252取代，HTML5中明确规定，如果声明的是ISO 8859-1 那么应该视为 Windows-1252 编码
- 我国内地使用的三个主要编码：GB2312、GBK、GB18030，有时间先后关系，表示的字符越来越多，且后面的兼容前面的
	- GB2312 使用两个字符，高位都为1
	- GBK 使用两个字符，高字节的高位位1，低字节的高位可能为0，也可能为0。通过前面一个字符来判断将其视位ASCII还是GBK
	- GB18030 使用变长的字符数量
- 香港/台湾主要使用Big5，针对繁体
    
## 常见非Unicode编码之间的兼容性
- 所有的都兼容ASCII
- 除了GB2312、GBK、GB18030 之间存在兼容关系，其他的相互之间都不兼容
- 虽然GB18030 和 Big5 都能够表示繁体，但是相互之间不兼容

## Unicode编码
Unicode给世界上所有的字符都规定了一个统一的编号，编号范围达到110多万，但大部分字符都在65536以内。Unicode本身没有规定怎么把这个编号对应到二进制。  
所以，有3种对应的方法，UTF-32/UTF-16/UTF-8。这3个就是3种将Unicode对应为2进制的方法。  
UTF-32使用4个字节；UTF-16大多使用2个字节，少部分4个。**这两个都不兼容ASCII编码**  
UTF-8使用1~4个字节表示，**兼容ASCII编码**。英文字符使用1个字节，中文大多使用3个字节
## 编码转换
举例说明：  
将"马"从GB18030转换为UTF-8。先查 GB18030->Unicode 编号，得到编号为 9A 6C，然后查 Unicode->UTF-8，得到UTF-8编码：E9A9AC
## 乱码的原因
### 解析错误
举例说明：  
本来应该是 Windows-1252 编码的文件，以 GB18030 打开，那么就乱码了
### 错误的解析和编码转换
举例说明：  
![upload successful](/img/hH9xMJmCoJ7FkPkVZRX3.png)
## java中的char
char的本质是一个**固定占用两个字节的无符号正整数**，这个正整数对应于 Unicode 编号，用于表示那个 Unicode 编号的字符。  
**所以，char只能表示小于等于65535的Unicode字符**
# 类
## 构造函数
`super` 一定是构造函数的第一行
## 可见性
private < 默认（包）< protectd < public  
所以，声明为 protected，同一个包下的可以直接访问
# 接口
## java8，java9对接口的增强
java8可以在接口里面些默认方法和静态方法。它们都是 `public` 的
```java
public interface ISpeak {

    void speak();

    static void hello(){
        System.out.println("hello");
    }

    default void hi(){
        hello();
    }
}
```
java9还可以写私有的
```java
public interface IDemo {

    private void common() {
        System.out.println("common");
    }

    default void actionA(){
        common();
    }
}
```
# 抽象类
## 抽象类和接口的比较
相似之处：  
- 都不能实例化对象
- 接口中的方法其实都是抽象方法（如果仅仅只是指方法的话，在java8中，其实接口已经可以达到很多抽象类的效果了）

本质上的不同之处：
- 接口中不能定义实例变量，而抽象类中可以
- 一个类只能继承一个类，但是可以实现多个接口

抽象类和接口之间并非替代关系而是配合关系。接口声明能力，抽象类提供默认实现，实现全部或者部分方法。一个接口经常有一个对应的抽象类。

# 内部类
- 内部类与包含它的外部类有着比较密切的关系，而与其他类关系不大
- 定义在内部可以对外部实现完全隐藏，可以有很好的封装性
- 相关的类写在一起写写法也更为简洁

## 静态内部类
```java
public class Outer {
    private static int shared = 100;

    public static class StaticInner {
        public void innerMethod() {
            System.out.println("outer shared: " + shared);
        }
    }
}
```
- 可以访问外部类的静态方法和变量
- 如果与外部类关联密切，且不依赖于外部类的实例，那么可以考虑使用静态内部类

使用场景：与外部类关系密切，且不依赖于外部类实例

## 成员内部类
```java
public class Outer {

    private int a = 100;

    public class Inner {
        public void innerMethod() {
            System.out.println("outer a: " + a);
            Outer.this.action();
        }
    }

    private void action() {
        System.out.println("action");
    }
    
    public void test(){
        Inner inner = new Inner();
        inner.innerMethod();
    }
}

public class Main {

    public static void main(String[] args) {
        Outer outer = new Outer();
        // 注意这里
        Outer.Inner inner = outer.new Inner();
        inner.innerMethod();
    }
}
```
- 除了可以访问静态的方法、变量，还可以访问外部类实例的方法、变量
- **注意对于外部 `this` 的引用为 `Outer.this`**
- 在外部类(`Outer`)中使用成员内部类可以直接使用 `Inner inner = new Inner()`
- 一个成员内部类对象总是与一个外部类对象相连接
- **在外面的其他类使用 `Outer.Inner inner = outer.new Inner()`**
- 成员内部类不可定义静态方法和变量，除了`final`的静态变量（原因可以这么理解：成员内部类是与一个外部类对象相连的，不应独立使用。而`static` 一般是独立使用的）

使用场景：内部类于外部类关系密切，需要访问外部类实例变量和方法。外部类可以返回某个接口，而成员内部类实现这个接口且为 `private` 这样就可以对外完全隐藏。
## 方法内部类
```java
public class Outer {

    private int a = 100;

    public void test(int p) {
        String str = "Hello";

        class Inner {
            private void func() {
                System.out.println(a);
                System.out.println(p);
                System.out.println(str);

                a = 12;
            }
        }

        Inner inner = new Inner();
        inner.func();

        System.out.println(a);
    }
}
```
- 方法内部类只能在定义的方法内部使用
- 方法是实例方法，则方法内部类可以访问静态变量、方法，实例变量、方法
- 方法是静态方法，则方法内部类只能访问静态变量、方法
- 方法内部类可以访问方法的局部变量，但是不能修改局部变量的值，更好的办法是，**将方法内部类访问的局部变量定义为`final`**
- 实例方法的方法内部类可以修改外部类的成员变量

其实方法内部类可以用成员内部类代替，方法参数通过参数传过去就好了。当这个内部类只有一个方法使用的时候，使用方法内部类可以实现更好的封装性
## 匿名内部类
```java
new 父类(参数列表) {
	// 匿名内部类实现部分
}

new 父接口() {
	// 匿名内部类实现部分
}
```
```java
public class PlayGround {

    private int a = 100;

    public void test() {

        Base base = new Base() {
            private int i;

            {
                System.out.println("初始化代码块");
                this.i = a;
            }

            @Override
            public void set(int v) {
                i = v;
            }

            @Override
            public int get() {
                return i;
            }
        };

        System.out.println(base.get());
        base.set(10);
        System.out.println(base.get());
    }
}
```
- 没有名字
- 没有构造函数，但可以通过参数列表调用对应的父类的构造函数
- 可以通过初始化代码块起到构造函数的效果
- 访问局部变量不可修改其值（`final`）

使用场景：回掉函数
# 枚举
## 基本方法
```java
public enum Size {
    SMALL, MEDIUM, LARGE
}
```
- 使用`==`和`equals`效果是一样
- 有一个`int ordinal()` 方法，可获得枚举值在声明时的顺序（从0开始）`Size.LARGE.ordinal()`
- 默认实现了`Comparable`接口，实际上是通过比较`ordinal`的大小
- 自带 `valueOf(String)` 方法，返回对应的枚举值 `Size.valueOf("MEDIUM")`，如果不存在，抛出`IllegalArgumentException`异常
- 自带 `values` 方法，返回包含所有枚举值的数组，顺序和声明的顺序一致
- switch语句中，枚举不能带前缀
```java
switch (size){
    case SMALL:
        System.out.println("s");
        break;
    case MEDIUM:
        System.out.println("m");
        break;
    case LARGE:
        System.out.println("l");
        break;
}
```
	不能写成 `case Size.SMALL`
- 前面说过，`switch`的跳转表的值必须为整型，所以，在`switch`语句中，枚举值会被转换为其对应的`ordinal`值

## 实质
编译器实际上会根据我们写的这个枚举生成一个继承于 `Enum`的一个类  
生成的示意代码
```java
public class Size extends Enum<Size> {
    public static final Size SMALL = new Size("SMALL", 0);
    public static final Size MEDIUM = new Size("MEDIUM", 1);
    public static final Size LARGE = new Size("LARGE", 2);

    private static Size[] VALUES = new Size[]{SMALL, MEDIUM, LARGE};

    private Size(String name, int ordinal) {
        super(name, ordinal);
    }

    public static Size[] values() {
        Size[] values = new Size[VALUES.length];
        System.arraycopy(VALUES, 0, values, 0, VALUES.length);
        return values;
    }

    public static Size valueOf(String name) {
        return Enum.valueOf(Size.class, name);
    }
}
```
需要注意以下几点：
- 生成类是 `final` 的
- 构造函数是私有的
- 三个枚举值实际上是三个 `static final`的静态变量，**这也是为什么使用`==`和`equals`效果是一样的原因**
- `values` 是编译器添加的
- `valueOf` 调用的父类方法实际上是回过头来调用 `values`，根据 `name` 比对得到值的

# 异常
部分异常之间的关系图
![upload successful](/img/mpabRHzW8GCv7oUDgn1w.png)
- Error  
表示系统错误或者资源耗尽，由Java自己抛出，应用程序不应该自己抛出
- Exception  
应用程序错误
- RuntimeException  
虽然名字这么取，但是其他异常其实也是运行时产生的。**它实际上的意思是未受检异常，相对而言，其他的Exception子类是受检异常，Error及其子类也是未受检异常**  
未受检异常 和 受检异常 的区别在于受检异常要求程序必须进行异常的处理，而未受检异常不用  
![upload successful](/img/6dr6HBJPFO5faggclNHW.png)

## finally
finally 无论有无异常发生都是会执行的
### 执行时间
- 没有异常发生  
try内代码执行结束之后执行
- 有异常且被捕获  
在catch执行结束之后执行
- 有异常但没有被捕获  
在异常抛给上层之前执行

**注意：finally中有 赋值语句，return 或者 抛出了新的异常 的情况**
### 有赋值语句
```java
public class Main {

    public static int test() {
        int r = 0;
        try {
            return r;
        } finally {
            r = 2;
        }
    }

    public static void main(String[] args) {
        System.out.println(test());
    }
}
```
输出 0  

实际执行的过程是，在执行到try里面的`return r`，语句前，会先将返回的`r`保存在一个临时变量中，然后才执行finally语句，最后try再返回那个临时变量。所以，finally对于`r`的赋值没有效果。**但是，这也意味着，这个类似与传递引用，调用它的方法会有效果。**
```java
public class Main {

    public static int[] test2() {
        int[] r = new int[]{0, 1, 2};
        try {
            return r;
        } finally {
            r[0] = 2;
        }
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(test2()));
    }
}
```
输出 \[2, 1, 2\]  

**在finally里面修改了数组里面的内容**
### 有return
```java
public class PlayGround {

    public static int test() {
        int r = 0;
        try {
            int a = 5/r;
            return r;
        } finally {
            return 2;
        }
    }

    public static void main(String[] args) {
        System.out.println(test());
    }
}
```
输出 2，且没有报异常  

在finally里面有return，try/catch里面的return会丢失，实际上会返回finally里面的返回值。同时，finally里面有返回值还掩盖了try/catch里面的错误，看起来就像没有异常一样
### 抛出了新的异常
```java
public class Main {

    public static int test() {
        int r = 0;
        try {
            int a = 5/r;
            return r;
        } finally {
            throw new RuntimeException("error");
        }
    }

    public static void main(String[] args) {
        System.out.println(test());
    }
}
```
同样，原有的异常也被finally里面的异常给掩盖了  

**总结一下，通常来说，为了避免混淆，不要在finally里面使用return或者抛出异常**
## try-with-resources
Java7新增的语法糖，针对于实现了 `java.lang.AutoCloseable` 接口的对象
## throws/throw
- throw  
是抛出一个异常 `throw new RuntimeException("error")`
- throws  
是在函数上声明可能抛出的异常。对于未受检异常，不要求使用throws声明。对于受检异常，要么catch掉，要么throws出去，没有throws不能抛出
```java
public void test() throws AppException, SQLException {
	//代码
}
```


# 参考
[Java CAS 理解](https://mritd.me/2017/02/06/java-cas/)