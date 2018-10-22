title: Sklearn SelectFromModel
author: xdkxlk
tags:
  - Sklearn
categories:
  - ML
date: 2018-09-04 17:00:00
---
>[原博客](https://blog.csdn.net/fontthrone/article/details/79064930)
## SelectFromModel
sklearn在Feature selection模块中内置了一个SelectFromModel，该模型可以通过Model本身给出的指标对特征进行选择，其作用与其名字高度一致，select （feature） from model。 
SelectFromModel 是一个通用转换器,**其需要的Model只需要带有conef_或者feature_importances属性,那么就可以作为SelectFromModel的Model来使用**. 如果相关的<code>coef_</code>或者<code>featureimportances</code>属性值低于预先设置的阈值，这些特征将会被认为不重要并且移除掉。除了指定数值上的阈值之外，还可以通过给定字符串参数来使用内置的启发式方法找到一个合适的阈值。可以使用的启发式方法有 mean、median 以及使用浮点数乘以这些（例如，0.1\*mean ）。

**根据基础学习的不同，在estimator中有两种选择方式**

第一种是基于L1的特征选择，使用L1正则化的线性模型会得到稀疏解，当目标是降低维度的时候，可以使用sklearn中的给予L1正则化的线性模型，比如LinearSVC，逻辑回归，或者Lasso。但是要注意的是：在 SVM 和逻辑回归中，参数 C 是用来控制稀疏性的：小的 C 会导致少的特征被选择。使用 Lasso，alpha 的值越大，越少的特征会被选择。

第二种是给予Tree的特征选择，Tree类的算法包括决策树，随机森林等会在训练后，得出不同特征的重要程度，我们也可以利用这一重要属性对特征进行选择。

但是无论选择哪一种学习器,我们都要记住的是我们的特征选择的最终标准应当是选择最好的特征,而非必须依照某种方法进行选择

几个重要的参数，属性，方法
- threshold ： 阈值，string, float, optional default None 
 - 可以使用：median 或者 mean 或者 1.25 \* mean 这种格式。
 - 如果使用参数惩罚设置为L1，则使用的阈值为1e-5，否则默认使用用mean
- prefit ：布尔，默认为False，是否为训练完的模型，（注意不能是cv，GridSearchCV或者clone the estimator得到的），如果是False的话则先fit，再transform。
- threshold_ ：采用的阈值

## 简单的示例
使用L1进行特征选择
```python
from sklearn.svm import LinearSVC
from sklearn.datasets import load_iris
from sklearn.feature_selection import SelectFromModel

# Load the boston dataset.
load_iris = load_iris()
X, y = load_iris['data'], load_iris['target']
print("X 共有 %s 个特征"%X.shape[1])

lsvc = LinearSVC(C=0.01, penalty="l1", dual=False).fit(X, y)
model = SelectFromModel(lsvc,prefit=True)
X_new = model.transform(X)
print("X_new 共有 %s 个特征"%X_new.shape[1])
```
```python
X 共有 4 个特征
X_new 共有 3 个特征
```