title: Java IO
author: xdkxlk
tags: []
categories:
  - Java
date: 2018-12-29 11:59:00
---
这几天发现自己对于IO方面的知识很是薄弱，再把Java核心技术卷2翻出来看看
# 输入/输出流
**inputStream/outputStream 这两个抽象类是所有字节流的基础**  
**别忘记 `close`，可以使用 try-with-resource**
## InputStream
- 字节流
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
- 字节流
- 是一个`abstract class`
- 只有一个`abstract` 方法 `void write(int b) throws IOException;` 所以，它的实现类至少需要实现这个方法，其他方法都是通过默认调用 `write()` 实现的
- `write()` 是阻塞的

## Reader
- 字符流
- 有两个`abstract`方法，`public int read(char cbuf[], int off, int len)` 和 `public void close()`
- `read` 方法的返回值虽然和 `InuptStream` 一样，也是 `int`，但是需要注意的是，**`Reader`的返回的取值范围是-1到Integer.MAX_VALUE，而`InputStream`的返回值的取值范围是-1到255**
- 没有`available`，对应的是`ready()`

## Writer
- 字符流
- 有三个`abstract`方法，`public void write(char cbuf[], int off, int len)`，`public void flush()`，`public void close()`

## Closeable
- 是接口
- 继承于 `java.lang.AutoCloseable`
- 覆盖了`AutoCloseable` 的 `close`
- `AutoCloseable` 的 `close` 抛出 `Exception`，而 `Closeable` 的 `close` 抛出 `IOException`

# 整体结构
## InuptStream/OutputStream
![upload successful](/img/jrbc3GfiodhZcrBLfF4F.png)
## Reader/Writer
对于Unicode文本，可以使用 `Reader/Writer`
![upload successful](/img/hiumP8K0CH6pb2a8CO6x.png)
## 常用类
![upload successful](/img/uHhp01UCld7fPx8AxiK0.png)
![upload successful](/img/aiRF3DCL3Nx2i9LUudiY.png)
# 基本介绍
## 字节流
- FileInputStream/FileOutputStream
对于文件的读写流。
FileOutputStream有两个稍稍特殊的方法
	- public FileChannel getChannel()  
    FileChannel定义在nio里面，指一个通道
	- public final FileDescriptor getFD()  
    FileDescriptor是文件的描述符，于操作系统的文件结构相关联。`FileDescriptor.sync()`方法可以保证将数据落到磁盘上面
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
- BufferedInputStream/BufferedOutputStream  
属于过滤流，给流增加缓冲的功能。先读一大块到buffer里面慢慢读，和先写到buffer里面再到一定大小之后flush
- CheckedInputStream和CheckedOutputStream  
https://blog.csdn.net/zmken497300/article/details/51915854  
属于过滤流，在输入/输出的同时获得文件的checksum（`getChecksum`）
- CipherInputStream/CipherOutputStream  
属于过滤流，增加加密的功能
- DigestInputStream/DigestOutputStream  
属于过滤流，也是增加加密的功能
- DataInputStream/DataOutputStream  
属于过滤流，增加读写java的基本类型的能力
- LineNumberInputStream  
已经过时，应该使用`LineNumberReader`
- PushbackInputStream  
属于过滤流，增加可以预览一个字节或者具有指定尺寸的回推缓冲区的功能
- PrintStream
属于过滤流，`System.out` 就是一个 `PrintStream`，可以打印字节/字符到某一个流上面

## 字符流
- BufferedReader/BufferWriter  
给Reader提供缓冲的能力，同时拥有readLine（BufferedReader）和newLine（BufferWriter）两个比较实用的功能  
一般处理文本的方法

```java
InputStream inputStream = null;
try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream,
        StandardCharsets.UTF_8))) {

    String line;
    while ((line = in.readLine()) != null) {
        //....
    }
} catch (IOException e) {
    e.printStackTrace();
}
```
在jdk1.8里面，还可以这样写
```java
try {
    Stream<String> lineStream = Files.lines(Paths.get(""), StandardCharsets.UTF_8);
    lineStream.forEach(s -> {
        //...
    });
} catch (IOException e) {
    e.printStackTrace();
}
```
- InputStreamReader/OutputStreamWriter  
能够将字节流包装成字符流
- FileReader/FileWriter  
继承于`InputStreamReader/OutputStreamWriter` ，其实就是默认把 `FileInputStream/FileOutputStream` 传递给 `InputStreamReader/OutputStreamWriter`
- StringReader/StringWriter  
将String作为输入源读入；将内容输出成一个String。这两个存在的意义感觉有点类似于 `ByteArrayInputStream/ByteArrayOutputStream`
- CharArrayReader/CharArrayWriter  
类似于`StringReader/StringWriter`，只不过是读写的是`char[]`
- PipedReader/PipedWriter  
作用同`PipedInputStream/PipedOutputStream`
- PrintWriter  
功能上类似于`PrintStream`。整体上区别不大
	- 区别
	- 在jdk1.5之前`PrintStream`不能指定编码格式，而`PrintWriter`可以
    - `PrintStream`可以处理rawbyte，而`PrintWriter`不能
    - autoflush的时机不同，前者在输出byte数组、调用println方法、输出换行符或者byte值10（即\n）时自动调用flush方法，后者在调用println，format，printf发生autoflushing
    - 虽然它们都有一个方法为`write(int b)`，但是`PrintStream`使用的是字节流，所以只使用最低8位（一个byte），而`PrintWriter`使用16位（一个char）
    - `PrintStream`继承于`FilterOutputStream`，`PrintWriter`直接继承于`Writer`
    
# RandomAccessFile
- 有一个比较特殊的 `readFully` 方法，和 `read` 方法的不同之处在于，一定可以读到期望的长度，如果没有读够，则会抛出`EOFException`异常
- 可以操作文件指针 `native long getFilePointer()`，`native void seek(long pos)`
- 可以直接获取文件长度。`native long length()`
- 还可以直接修改文件长度。`native void setLength(long newLength) `。如果缩小，那么就直接截断文件，如果扩大，则文件会扩展，扩展的部分未定义
- `writeBytes(String s)`，`String readLine()`，这两个方法没有考虑文字的编码，如果是中文会存在问题
    
# 实用代码
## 复制输入流的内容到输出流
```java
public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[4096];
    int bytesRead = 0;
    while ((bytesRead = in.read(buf)) != -1) {
        out.write(buf, 0, bytesRead);
    }
}
```
## 读取文件到byte数组
```java
public static byte[] readFileToByteArray(String fileName) throws IOException {
    try (BufferedInputStream fin = new BufferedInputStream(
            new FileInputStream(fileName));
         ByteArrayOutputStream bout = new ByteArrayOutputStream()) {

        copy(fin, bout);
        return bout.toByteArray();
    }
}
```
    
# 一些小问题
- 注意到，`BufferedInputStream` 继承于 `FilterOutputStream`，而 `BufferedReader` 直接继承于 `Reader`  
原因我现在还不能说清除，网上有些解释时说是由于历史性的原因造成的  
[为什么BufferedInputtream继承自过滤字节流，而BufferedReader却并非继承自过滤字符流呢](https://bbs.csdn.net/topics/390329674)
- `FilterOutputStream` 不是 `Abstract`的  
我觉得这个应该是历史遗留问题，`FilterInputStream` 是jdk1.0就有的，而在jdk1.1中，`FilterReader` 就是`Abstract`的。对于OutputStream和Writer类似。