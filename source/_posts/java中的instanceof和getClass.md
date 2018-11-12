title: java中的instanceof和getClass()
author: xdkxlk
tags: []
categories:
  - Java
date: 2018-11-12 19:06:00
---
父类A
```java
class A { }
```
子类B
```java
class B extends A { }
```
构造对象
```
Object o1 = new A(); 
Object o2 = new B(); 
```
# instanceof
```
o1 instanceof A => true  
o1 instanceof B => false  
o2 instanceof A => true // <================ HERE  
o2 instanceof B => true 
``` 
- 用法

```
英文：result = object instanceof class
中文：结果 = 某个实例对象  instanceof   某个类名
```
java 中的instanceof 运算符是用来在运行时指出对象是否是特定类的一个实例。instanceof通过返回一个布尔值来指出，这个对象是否是这个特定类或者是它的子类的一个实例。
- 总结

```
S(Object) instanceof T(Class)
```
**简单来说，instanceof就是判断对象S是否是T类的实例，或者是T类的子类实例。**
# getClass
```java
o1.getClass().equals(A.class) => true 
o1.getClass().equals(B.class) => false 
o2.getClass().equals(A.class) => false // <===============HERE 
o2.getClass().equals(B.class) => true
```
getClass方法在JDK1.8中定义如下：
```
/**
*    Returns the runtime class of this Object
*/
public final native Class<?>  getClass();
```
- 功能

返回在运行时期对象的类。  
getClass() 会在你需要判断一个对象不是一个类的子类的时候很有用  
getClass() will be useful when you want to make sure your instance is NOT a subclass of the class you are comparing with.  
# 总结
instanceof 用来判断对象与类的关系，判断对象S是否是T类的实例，或者是T类的子类实例。  
getClass 获得运行时期对象的类。当限定到具体某一类时，则使用getClass+equals搭配。  
在 jdk ArryList 的构造函数中，`elementData.getClass() != Object[].class` 限制了 `elementData` 的类型一定要是 Object 的数组，如果是Object的子类什么的都不行（为什么要这么判断见[Jdk Bug6260652](http://xdkxlk.github.io/2018/11/12/Jdk-Bug6260652) 和 [ArrayList](/2018/11/10/ArrayList/#ArrayList-Collection-lt-extends-E-gt-c)）  
另外，对于 getClass() 和某个 class 比较，其实使用 `equals` 和 `==` 是一样的
# 参考
[java中instanceof和getClass()的作用](https://www.cnblogs.com/aoguren/p/4822380.html)