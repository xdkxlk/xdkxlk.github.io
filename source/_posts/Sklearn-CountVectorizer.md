title: Sklearn CountVectorizer
author: xdkxlk
tags:
  - Sklearn
categories:
  - ML
date: 2018-09-04 17:15:00
---
一直不知道CountVectorizer输出的结果到底是什么意思，现在终于弄明白了
```python
from sklearn.feature_extraction.text import CountVectorizer

texts=["dog cat fish","dog cat cat","fish bird", 'bird']
cv = CountVectorizer()
cv_fit=cv.fit_transform(texts)

print(cv.get_feature_names())
print(cv_fit.toarray())
#['bird', 'cat', 'dog', 'fish']
#[[0 1 1 1]
# [0 2 1 0]
# [1 0 0 1]
# [1 0 0 0]]

print(cv_fit.toarray().sum(axis=0))
#[2 3 2 2]
```
\[0 1 1 1\]意思就是，第一段话"dog cat fish"对应单词表\['bird', 'cat', 'dog', 'fish'\]，bird出现了0次，cat出现了1次，以此类推。由此可以看出，形成的矩阵一般是一个稀疏矩阵，需要进行特征的提取和降维。