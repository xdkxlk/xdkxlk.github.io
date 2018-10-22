---
title: Hibernate遗忘知识点记录
date: 2018-01-24 20:18:29
tags: Hibernate
categories: 后端
---
## 怎么级联保存/更新
可以设置 'cascade' 属性。一般在 one-to-many 比较常见的需求，对于one-to-many在 \<set\> 标签中设置
```xml
<set name="ProductModelSet" lazy="true" inverse="true" cascade="save-update">
            <key column="catalog_id"/>
            <one-to-many class="com.lk.model.ProductModel"/>
        </set>
```
## boolean属性怎么配置
hibernate中boolean与sql的关系如下： 
``` 
hibernate映射类型       java类型              标准sql类型
true_false             boolean/Boolean       char(1)
yes_no                 boolean/Boolean       char(1)
byte                   boolean/Boolean
number(1)
```
xml格式
```xml
<property  name="tag"   column="tag"  type="yes_no"/>
<property  name="tag"   column="tag"  type="true_false"/>
<property  name="tag"   column="tag"  type="byte"/>
```
annotation注解
```java
@org.hibernate.annotations.Type(type="yes_no")
//或
@org.hibernate.annotations.Type(type="true_false")
//或
@org.hibernate.annotations.Type(type="byte")
private boolean tag;
```
## 如何插入的时候不插入为NULL的，更新的时候不更新为NULL的
设置 dynamic-update 和 dynamic-insert 属性
```xml
<class name="catalog" table="catalog"
           dynamic-update="true" dynamic-insert="true">
</class>
```
## Inverse和Cascade
Inverse：<font color='red'>inverse的真正作用就是指定由哪一方来维护之间的关联关系。</font>负责控制关系，默认为false，也就是关系的两端都能控制，但这样会造成一些问题，更新的时候会因为两端都控制关系，于是重复更新。一般来说有一端要设为true。  
Cascade：<font color='red'>负责控制关联对象的级联操作。</font>包括更新、删除等，也就是说对一个对象进行更新、删除时，其它对象也受影响，比如我删除一个对象，那么跟它是多对一关系的对象也全部被删除。  
举例说明区别：删除“一”那一端一个对象O的时候，如果“多”的那一端的Inverse设为true，则把“多”的那一端所有与O相关联的对象外键清空；如果“多”的那一端的Cascade设为Delete，则把“多”的那一端所有与O相关联的对象全部删除。 
## 设置createTime和updateTime
```java
@Getter
@Setter
@MappedSuperclass
public class TimeEntity extends AbstractEntity{

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
}
```
注解<code>@Temporal</code>可以设置<code>java.util.Date</code> or <code>java.util.Calendar</code>所映射的类型。
- TemporalType.DATE 对应MySQL中的 date
- TemporalType.TIME 对应MySQL中的 time
- TemporalType.TIMESTAMP 对应MySQL中的 datetime