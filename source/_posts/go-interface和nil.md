title: go interface和nil
author: xdkxlk
tags:
  - go
categories:
  - go
date: 2019-06-25 16:49:00
---
# 问题示例

首先有一个 DataReader的 interface

```go
type DataReader interface {
	ReadData() (string, error)
}
```

然后有一个实现了这个 interface的 struct

```go
type SampleReader struct {
}

func (SampleReader) ReadData() (string, error) {
	return "data", nil
}

func OpenReader() *SampleReader {
	return nil
}
```

最后有一个 Read方法

```go
func Read() {
	var dataReader DataReader
	dataReader = OpenReader()
	if dataReader != nil {
		fmt.Println(dataReader.ReadData())
	}else {
		fmt.Println("开启连接失败")
	}
}
```

实际测试的时候机会发现，其实这个 `dataReader` 永远都不为 `nil`

# 原因

在 go里面 `inerface{}` 包含了两个指针，一个指向类型，一个指实际的值

如果接口指向实体的类型，那么内存中的大致结构如下


![upload successful](/img/WScbrxqsWS3ab87j9mkE.png)

如果接口指向实体的指针，那么内存中的大致结构如下


![upload successful](/img/fW4wUyxnzD4kcbgZsU49.png)

那么在上面的例子中，`OpenReader()` 返回的是实体类型的指针，所以接口一个指针指向了 `*SampleReader`类型，一个为 nil（因为没有值）

但是，由于一个指针已经有指了，所以就不等于 nil

# 解决方法

## 方法一：直接返回 interface

修改 `OpenReader()` 为

```go
func OpenReader() DataReader {
	return nil
}
```

这样，返回的接口两个指针都是为空的，那么这个接口就等于 nil

## 方法二：先判断一下再赋值给 interface

修改 `Read`方法

```go
func Read() {
	var dataReader DataReader
    // 这里有修改
	newReader := OpenReader()
	if newReader != nil {
		dataReader = newReader
		fmt.Println(dataReader.ReadData())
	}else {
		fmt.Println("开启连接失败")
	}
}
```

先用 `*SampleReader` 类型的指针判断一下，再赋值给接口

## 方法三：通过反射判断

新增 `IsNil` 方法：

```go
func IsNil(i interface{}) bool {
	v := reflect.ValueOf(i)
	if v.Kind() == reflect.Ptr {
        // 注意判断是否为指针，不然调用 IsNil()会报错
		return v.IsNil()
	}
	return false
}
```

修改 `Read` 方法：

```go
func Read() {
	var dataReader DataReader
	dataReader = OpenReader()
	if !IsNil(dataReader) {
		fmt.Println(dataReader.ReadData())
	} else {
		fmt.Println("开启连接失败")
	}
}
```

## 方法四：在设计层面避免这个问题

修改 `OpenReader()` 方法，增加 `error` 返回值

```go
func OpenReader() (*SampleReader, error) {
	return nil, fmt.Errorf("open reader error")
}
```

这样就可以直接根据 error 的信息判断是否出错了，而且个人觉得，这才是更加好的方法

使用的时候判断一个 error 是否为 nil 就好了

```go
func Read() {
	var dataReader DataReader
	var err error
	if dataReader, err = OpenReader(); err != nil {
		fmt.Println("开启连接失败")
	} else {
		fmt.Println(dataReader.ReadData())
	}
}
```