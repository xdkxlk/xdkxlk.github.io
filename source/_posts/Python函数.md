---
title: Python函数
date: 2017-12-30 11:14:39
tags: Python
categories: 语言
---
<font size=4>**参数定义的顺序必须是：必选参数、默认参数、可变参数、命名关键字参数、关键字参数。**</font>
## 默认参数
```python
def power(x, n=2):
```
<font color=red>**默认参数必须指向不变对象！**</font>  
<font color=red>错误示范</font>
```python
def add_end(L=[]):
    L.append('END')
    return L
```
```bash
>>> add_end()
['END', 'END']
>>> add_end()
['END', 'END', 'END']
```
正确写法
```python
def add_end(L=None):
    if L is None:
        L = []
    L.append('END')
    return L
```
原因：Python函数在定义的时候，默认参数L的值就被计算出来了，即[]，因为默认参数L也是一个变量，它指向对象[]，每次调用该函数，如果改变了L的内容，则下次调用时，默认参数的内容就变了，不再是函数定义时的[]了。
## 可变参数
可变参数允许传入0个或任意个参数，这些可变参数在函数调用时自动组装为一个tuple
```python
def calc(*numbers):
    sum = 0
    for n in numbers:
        sum = sum + n * n
    return sum
```
调用
```python
calc(1, 2)
#在list或tuple前面加一个*号，把list或tuple的元素变成可变参数
calc(*[1, 2, 3])
```
## 关键字参数
关键字参数允许传入0个或任意个含参数名的参数，这些关键字参数在函数内部自动组装为一个dict
```python
def person(name, age, **kw):
    print('name:', name, 'age:', age, 'other:', kw)
```
```python
>>> person('Michael', 30)
name: Michael age: 30 other: {}
>>> person('Bob', 35, city='Beijing')
name: Bob age: 35 other: {'city': 'Beijing'}
>>> person('Adam', 45, gender='M', job='Engineer')
name: Adam age: 45 other: {'gender': 'M', 'job': 'Engineer'}
```
```python
>>> extra = {'city': 'Beijing', 'job': 'Engineer'}
>>> person('Jack', 24, **extra)
name: Jack age: 24 other: {'city': 'Beijing', 'job': 'Engineer'}
```
\*\*extra表示把extra这个dict的所有key-value用关键字参数传入到函数的\*\*kw参数，kw将获得一个dict，<font color=red>注意kw获得的dict是extra的一份拷贝，对kw的改动不会影响到函数外的extra</font>。
## 命名关键字参数
```python
def person(name, age, *, city, job):
    print(name, age, city, job)
```
调用必须传入参数名
```python
>>> person('Jack', 24, job='Engineer', city='Beijing')
Jack 24 Beijing Engineer
```
如果函数定义中已经有了一个可变参数，后面跟着的命名关键字参数就不再需要一个特殊分隔符*了
```python
def person(name, age, *args, city, job):
    print(name, age, args, city, job)
```
## 例子
```python
def f2(a, b, c=0, *, d, **kw):
    # 位置参数，默认参数，命名关键字参数，关键字参数
    print('a =', a, 'b =', b, 'c =', c, 'd =', d, 'kw =', kw)

f2(1, 2, d=99, ext=None)
# a = 1 b = 2 c = 0 d = 99 kw = {'ext': None}
```