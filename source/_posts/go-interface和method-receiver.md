title: go interface和method receiver
author: xdkxlk
tags:
  - go
categories:
  - go
date: 2019-06-25 16:53:00
---
# 现象及总结

先直接说结论

Methods Receivers 和 Values的兼容性

| Methods Receivers | Values    |
| ----------------- | --------- |
| (t  T)            | T  和  \*T |
| (t  \*T)           | \*T        |

对于以下代码：

```go
type Adder interface {
	Add()
}

type User struct {
	Age int
}

func (self User) Add(){
	self.Age++
}

func main() {
	var adder Adder

	adder = User{}
	adder.Add()
	fmt.Println(adder) //{0}

	adder = &User{}
	adder.Add()
	fmt.Println(adder) //&{0}
}
```

User 的 Methods Receivers 是 `(t T)` 类型，所以可以接受 `User{}` 和 `&User{}` 两种类型的值

对于以下代码：

```go
type Adder interface {
	Add()
}

type User struct {
	Age int
}

func (self *User) Add(){
	self.Age++
}

func main() {
	var adder Adder

	// 报错
	// User does not implement Adder (Add method has pointer receiver)
	//adder = User{}
	//adder.Add()
	//fmt.Println(adder)

	// 不会报错
	adder = &User{}
	adder.Add()
	fmt.Println(adder) // &{1}
}
```

User 的 Methods Receivers 是 `(t *T)` 类型，只能接受  `&User{}` 值的类型

# 原因

go 会根据

```go
func (self User) Add(){
	self.Age++
}
```

自动生成一个新的 Notify 方法

```go
func (self *User) Add(){
    // go 没有重载，这里仅仅是示意代码
	(*self).Add()
}
```

对于 `User{}.Add()` 和 `(&User{}).Add()` 产生的效果可以等同，因为 `(*self)` 传递过去之后是一个副本，不会影响原有值

所以，对于 `(t T)` 类型的方法，其实同时包含了 `(t T)` 和 `(t *T)`

而对于

```go
func (self *User) Add(){
	(*self).Add()
}
```

就不能反过来生成

```go
func (self User) Add(){
    // go 没有重载，这里仅仅是示意代码
	(&self).Add()
}
```

因为对于 `User{}.Add()` 和 `(&User{}).Add()` 产生的效果是不同的， `User{}.Add()` 仅仅只会修改副本

# 扩展示例

对于一下代码

```go
type LessAdder interface {
	Less(b Integer) bool
	Add(b Integer)
}

type Lesser interface {
	Less(b Integer) bool
}

type Integer int

func (a Integer) Less(b Integer) bool {
	return a < b
}
func (a *Integer) Add(b Integer) {
	*a += b
}
```

下面的赋值哪个是正确的呢

```go
var a Integer = 1

var b LessAdder = &a
var c LessAdder = a

var d Lesser = &a
var e Lesser = a
```

答案是，c是错误的，其他都是正确的。c 会报 Add方法只有指针的receiver的错误