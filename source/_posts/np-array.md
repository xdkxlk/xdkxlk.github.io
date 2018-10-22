---
title: np-array
date: 2018-1-10 10:35:54
tags: Python
categories: ML
---
## np.array
> array(object, dtype=None, copy=True, order='K', subok=False, ndmin=0)  
  
  
  
> See Also：empty, empty\_like, zeros, zeros\_like, ones, ones\_like, full, full\_like

  
  
### NumPy的数组中比较重要ndarray对象属性有
- **ndarray.ndim**  
数组的维数（即数组轴的个数），等于秩。最常见的为二维数组（矩阵）。  
- **ndarray.shape**  
数组的维度。为一个表示数组在每个维度上大小的整数元组。例如二维数组中，表示数组的“行数”和“列数”。ndarray.shape返回一个元组，这个元组的长度就是维度的数目，即ndim属性。
- **ndarray.size**  
数组元素的总个数，等于shape属性中元组元素的乘积。
- **ndarray.dtype**  
表示数组中元素类型的对象，可使用标准的Python类型创建或指定dtype。另外也可使用前一篇文章中介绍的NumPy提供的数据类型。
- **ndarray.itemsize**  
数组中每个元素的字节大小。例如，一个元素类型为float64的数组itemsiz属性值为8(float64占用64个bits，每个字节长度为8，所以64/8，占用8个字节），又如，一个元素类型为complex32的数组item属性为4（32/8）。
- **ndarray.data**  
包含实际数组元素的缓冲区，由于一般通过数组的索引获取元素，所以通常不需要使用这个属性。  
  

### 参数
- **object : array\_like**   
An array, any object exposing the array interface（暴露array接口的object）, an object whose \_\_array\_\_ method returns an array（array方法返回array类型的object）, or any (nested) sequence
- **dtype : data-type（array的数据类型）, optional**  
The desired data-type for the array. 如果没有被指定，会自动确定需要的最小的数据类型
- **copy : bool, optional**  
默认为true。如果为true，则object将会被复制。操作为object的副本 
- **order : {'K', 'A', 'C', 'F'}, optional**  
Specify the memory layout of the array. 默认为K  
- **subok : bool, optional**  
If True, then sub-classes will be passed-through  
默认为false。the returned array will be forced to be a base-class array  
- **ndmin : int, optional**  
数组的最小维度  
  
### 例子  
**创建**
```python
>>> np.array([1, 2, 3])
array([1, 2, 3])
```
**Upcasting**  
```python
>>> np.array([1, 2, 3.0])
array([ 1.,  2.,  3.])
```
**More than one dimension**  
```python
>>> np.array([[1, 2], [3, 4]])
array([[1, 2],
        [3, 4]])
```
**Minimum dimensions 2:**
```python
>>> np.array([1, 2, 3], ndmin=2)
array([[1, 2, 3]])
```
**Type provided:**
```python
>>> np.array([1, 2, 3], dtype=complex)
array([ 1.+0.j,  2.+0.j,  3.+0.j])
```
**Data-type consisting of more than one element:**
```python
>>> x = np.array([(1,2),(3,4)],dtype=[('a','<i4'),('b','<i4')])
>>> x['a']
array([1, 3])
```
**Creating an array from sub-classes:**
```python
>>> np.array(np.mat('1 2; 3 4'))
array([[1, 2],
        [3, 4]])

>>> np.array(np.mat('1 2; 3 4'), subok=True)
matrix([[1, 2],
        [3, 4]])
```