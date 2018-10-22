---
title: Tensorflow官方mnist基础例子学习
date: 2017-12-30 17:40:54
tags: Tensorflow
categories: ML
---
>[官方文档](https://www.tensorflow.org/get_started/mnist/beginners)  
>[中文文档](http://wiki.jikexueyuan.com/project/tensorflow-zh/tutorials/mnist_beginners.html) (略微有点出入)  
  

## tf.placeholder
```python
tf.placeholder(dtype, shape=None, name=None)
```
此函数可以理解为形参，用于定义过程，在执行的时候再赋具体的值  
参数：
- shape  
数据形状。默认是None，就是一维值，也可以是多维，比如
```python
[2, 3]  #表示2行3列
[None, 3]  #表示列是3，行不定
[10]  #表示1行10列
```
## tf.placeholder 与 tf.Variable
- tf.Variable：主要在于一些可训练变量 (trainable variables)，比如模型的权重 (weights，W)或者偏执值
- tf.placeholder：用于得到传递进来的真实的训练样本。  
  
## reduce\_sum的reduction\_indices参数
![image](/img/v2-42f4cebfcadac318d3de2394905c5c99_hd.jpg)
[3, 3]竖起来过来显示是为了说明reduction\_indices=[1, 0]的过程中维度的信息是一直保留着的，所以它并不是一个列向量，亦即它不是[ [3], [3] ]，它本质还是[ 3, 3 ]，这也是为什么你在仅仅使用reduction\_indices=1的时候，打印出来的是[ 3, 3 ]的原因。  
## 代码中y = tf.matmul(x, W) + b维度不同怎么可以相加
《Deep Learning》中文版第2.1节原文
![image](/img/v2-9f46c9c10efc8e185fda5ccd32045019_r.jpg)  
![image](/img/v2-c62a831754fb507a1c95fe8e6bfdcf5a_r.jpg)  
```python
# 矩阵 None * 784
x = tf.placeholder(tf.float32, [None, 784])
# 矩阵 784 * 10
W = tf.Variable(tf.zeros([784, 10]))
# 矩阵 1 * 10
b = tf.Variable(tf.zeros([10]))
# x * W = None * 10的矩阵
# 这里 None*10 的矩阵加上 1*10 的矩阵实际上是 None*10 每一行加上b
# 所以最终计算出来的结果是 None*10
y = tf.matmul(x, W) + b
```