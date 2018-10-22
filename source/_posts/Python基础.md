---
title: Python基础
date: 2017-12-30 10:47:36
tags: Python
categories: 语言
---
## 基本数据结构
### List
```python
list = ['Michael', 'Bob', 'Tracy']
```
List的拷贝
```python
a = [1, 2]
b = a
a[0] = 5
print(b) # [5, 2]
```
```python
a = [1, 2]
b = a[:]
a[0] = 5
print(b) # [1, 2]
```
其实，个人觉得更好的方式是使用 copy.copy() 方法和 copy.deepcopy() 方法（经过测试 a[:] 的方式类似于浅拷贝）。  
对于 copy.copy() 方法和 copy.deepcopy() 方法的自定义可以重写类的 \_\_copy\_\_ 和 \_\_deepcopy\_\_ 方法
### Tuple
不可变
```python
tuple = ('Michael', 'Bob', 'Tracy')

#只有1个元素的tuple定义时必须加一个逗号,来消除歧义
tuple = ('Michael',)
```
### Dict
就是map
```python
d = {'Michael': 95, 'Bob': 75, 'Tracy': 85}
```
判断key存不存在
```python
>>> 'Thomas' in d
False

#或者
>>> d.__contains__('Thomas')
False

#或者
>>> d.get('Thomas')
>>> d.get('Thomas', -1)
-1
```
### Set
```python
s = set([1, 2, 3])
```