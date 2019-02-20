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
    
## 字节流和字符流之间的互相转换
### 字节流转字符流
这个比较简单，直接使用 `InputStreamReader`, `OutputStreamWriter` 就好了
### 字符流转字节流
个人觉得没有必要，但是看见网上有说这道面试题那就想一下。  
个人觉得的方法是
```java
// 源
InputStreamReader inputStreamReader = ....;
BufferedReader reader = new BufferedReader(inputStreamReader);

StringBuilder sb = new StringBuilder();
String s;
while ((s = reader.readLine()) != null) {
    sb.append(s).append('\n');
}

// 目标
ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sb.toString().getBytes());
```
    
# RandomAccessFile
- 有一个比较特殊的 `readFully` 方法，和 `read` 方法的不同之处在于，一定可以读到期望的长度，如果没有读够，则会抛出`EOFException`异常
- 可以操作文件指针 `native long getFilePointer()`，`native void seek(long pos)`
- 可以直接获取文件长度。`native long length()`
- 还可以直接修改文件长度。`native void setLength(long newLength) `。如果缩小，那么就直接截断文件，如果扩大，则文件会扩展，扩展的部分未定义
- `writeBytes(String s)`，`String readLine()`，这两个方法没有考虑文字的编码，如果是中文会存在问题

# 内存映射文件
- 简单的解释：就是将文件映射到内存，文件对应于内存中的一个字节数组，对于文件的操作变成对于这个字节数组的操作。这种映射可以是文件的全部，也可以是文件的一部分
- 对于一般的文件不需要使用内存映射文件，如果需要读写大文件，需要一定的读写效率，那么可以考虑内存映射文件
- 文件并不会马上加载，仅仅在使用到的时候才会按需加载，类似于虚拟内存的页面管理
- 不适合小文件，因为是按照页面大小分配内存的，对于小文件浪费内存
- 通过 `FileChannel.map(MapMode mode, long position,
long size)` 方法映射，返回值为 `MappedByteBuffer`，代表一个内存映射文件。以后的读写都是通过这个类来进行操作的
- `FileChannel` 可以通过`FileInputStream/FileOutputStream`或者`RandomAccessFile`获得

# 对象的序列化
- 实现`Serializable`接口
- 不需要序列化的属性标识为 `transient`
- 如果需要定制序列化需要写两个方法，声明必须如下：
```java
private void writeObject(ObjectOutputStream s) throws IOException {
    s.defaultWriteObject();
    //...
}

private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
    s.defaultReadObject();
    //...
}
```
- 最好在自定义的`write/readObject`里面第一行调用一下`defaultXXX`方法。以避免`StreamCorruptedException`异常（[Why does the defaultWriteObject function have to be called first when writing into an ObjectOutputStream?](https://stackoverflow.com/questions/16239239/why-does-the-defaultwriteobject-function-have-to-be-called-first-when-writing-in)）
- 如果两个对象引用同一个对象，那么反序列化出来之后，也还是引用的同一个对象
- 如果两个对象存在循环引用，也不会有问题


# nio
## channel
Java NIO的通道类似流，但又有些不同：
- 既可以从通道中读取数据，又可以写数据到通道。但流的读写通常是单向的。
- 通道可以异步地读写。
- 通道中的数据总是要先读到一个Buffer，或者总是要从一个Buffer中写入。

![upload successful](/img/39lt9LQyqg2ZTFVCvBMT.png)
## Buffer
缓冲区本质上是一块可以写入数据，然后可以从中读取数据的内存。这块内存被包装成NIO Buffer对象，并提供了一组方法，用来方便的访问该块内存。  
使用Buffer读写数据一般遵循以下四个步骤：
- 写入数据到Buffer对于
- 调用flip()方法。将buffer从写模式切换为读模式。
- 从Buffer中读取数据
- 调用clear()方法或者compact()方法。将buffer从读模式切换为写模式。

### 基本原理
buffer是通过capacity，position和limit这三个属性来控制读写的
![upload successful](/img/j8t1xE7wbog6pIMOLbhv.png)
- 写模式下  
limit为最大空间capacity，position为当前写的位置
- 读模式下  
limit为写模式下的position，position重置为0

### 空间分配
对于 `ByteBuffer`，有两个分配的方法 `allocate`，`allocateDirect`  
`allocateDirect`对应的是`DirectByteBuffer`  
`allocate`对应的是`HeapByteBuffer`  

区别：[ByteBuffer.allocate() vs. ByteBuffer.allocateDirect()](https://stackoverflow.com/questions/5670862/bytebuffer-allocate-vs-bytebuffer-allocatedirect)  
对于操作系统，对于IO的操作是在内存当中的。而且操作系统是将其视为连续的内存区域。同时注意到，操作系统是直接访问进程中的地址进行数据传输的，这里是JVM。这就意味着，操作系统的IO操作的目标必须是连续的比特空间。在JVM中，比特数组并不一定是连续的，而且GC的时候，会将其移动。  
当然，是可以使用Nondirect buff的（`HeapByteBuffer`）当使用的是Nondirect buff的时候，它并不能直接给 native IO 进行操作，channel或许会隐式的进行一下操作：  
假设进行一个写操作
1. 创建一个临时的 direct ByteBuffer object
2. 将 nondirect buffer 的要写的内容复制到这个临时 buffer 里面
3. 使用这个临时 buffer 进行IO操作
4. 当这个临时的 buffer object 的生命周期到了，最终被GC

当然，实际上的实现会有所优化，一般会缓存、重用这些 direct buffers   
由于这些原因，所以`DirectByteBuffer`有存在的意义。对于Direct Buffer操作系统IO可以直接对其进行操作，速度会更快。但它是在堆外面的一块连续的内存空间，不被GC所管理，同时，创建的开销会比较大，所以，尽量用在能够重用的地方。

## 通过channel读取文件的simpleSample
```java
try (FileInputStream inputStream = new FileInputStream("dataFile")) {
    FileChannel inChannel = inputStream.getChannel();

    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(48);
    int bytesRead = inChannel.read(byteBuffer);
    while (bytesRead != -1) {
        byteBuffer.flip();

        while (byteBuffer.hasRemaining()) {
            System.out.print((char) byteBuffer.get());
        }

        byteBuffer.clear();
        bytesRead = inChannel.read(byteBuffer);
    }
}
```
    
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