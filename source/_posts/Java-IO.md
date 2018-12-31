title: Java IO
author: xdkxlk
tags: []
categories:
  - Java
date: 2018-12-29 11:59:00
---
这几天发现自己对于IO方面的知识很是薄弱，再把Java核心技术卷2翻出来看看
# 输入/输出流
**inputStream/outputStream 这两个抽象类是所有IO的基础**  
**别忘记 `close`，可以使用 try-with-resource**
## InputStream
- 是一个`abstract class`
- 只有一个`abstract` 方法 `int read() throws IOException;` 所以，它的实现类至少需要实现这个方法，其他方法都是通过默认调用 `read()` 实现的
- `read()` 读取并返回一个字节，如果结束了，那么返回 -1
- `read()` 是阻塞的
- 默认不支持重复读取（`markSupported()` 返回 `false`）

### 能不能实现“非阻塞呢”？
通过`available()`方法可以实现一定意义上的非阻塞
```java
InputStream i = null;
int byteAvailable = i.available();
if(byteAvailable > 0){
    byte[] data = new byte[byteAvailable];
    i.read(data);
}
```
`available`会返回现在可以获取到的字节数目，如果大于0的时候去读，那么就不会阻塞了

## OutputStream
- 是一个`abstract class`
- 只有一个`abstract` 方法 `void write(int b) throws IOException;` 所以，它的实现类至少需要实现这个方法，其他方法都是通过默认调用 `write()` 实现的
- `write()` 是阻塞的

## Closeable
- 是接口
- 继承于 `java.lang.AutoCloseable`
- 覆盖了`AutoCloseable` 的 `close`
- `AutoCloseable` 的 `close` 抛出 `Exception`，而 `Closeable` 的 `close` 抛出 `IOException`

## 整体结构
### InuptStream/OutputStream
![upload successful](/img/jrbc3GfiodhZcrBLfF4F.png)
### Reader/Writer
对于Unicode文本，可以使用 `Reader/Writer`
![upload successful](/img/hiumP8K0CH6pb2a8CO6x.png)
### 常用类
![upload successful](/img/uHhp01UCld7fPx8AxiK0.png)
### 基本介绍
- PipedInputStream/PipedOutputStream  
管道输入/输出流，可用于实现线程之间的通信
- ByteArrayInputStream/ByteArrayOutputStream  
对于内存中byte数组的读写，支持mark，reset
- FilterInputStream/FilterOutputStream  
这个类本身并没有什么功能，但是它是所有装饰流的基类，它们都依附在一个InputStream/OutputStream上面（装饰者模式）  
https://blog.csdn.net/zhao123h/article/details/52826682
- SequenceInputStream  
可以合并多个输入流，先读取第一个流，读完流，读下一个输入流，依附于一个InputStream
- StringBufferInputStream  
已经过时了，应该使用`StringReader`
- ObjectInputStream/ObjectOutputStream  
对于对象的序列化操作流，依附于一个InputStream/OutputStream上面