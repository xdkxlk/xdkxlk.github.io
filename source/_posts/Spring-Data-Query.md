---
title: Spring Data Query
date: 2018-01-26 20:13:46
tags: SpringData
categories: 后端
---
>[Spring Data JPA 2.0.3 Reference](https://docs.spring.io/spring-data/jpa/docs/2.0.3.RELEASE/reference/html/#repositories.query-methods.query-creation)  
  
在看《SpringData实战》的过程中发现居然不用写sql，**定义一个Repository接口，写上几个接口方法名，然后，就能使用这些接口方法了**。感觉很是厉害！！就看了下SpringData的官方文档，学习了下。
## Query creation
对于<code>List<Person> findByEmailAddressAndLastname(EmailAddress emailAddress, String lastname)</code>Spring会把<code>find…By, read…By, query…By, count…By</code>和<code>get…By</code>这些前缀去掉，而只处理之后的字符串。例如，以下方法名，在Spring看来，就是<code>EmailAddressAndLastname</code>而已。而<code>And</code>和<code>Or</code>都被作为保留关键字，并起到SQL中<code>AND</code>和<code>OR</code>的作用。当然，你定义的<code>Person</code>对象，必须含有<code>emailAddress</code>和<code>lastname</code>属性，否则Spring找不到这些属性就会出错。
```java
interface PersonRepository extends Repository<User, Long> {

  List<Person> findByEmailAddressAndLastname(EmailAddress emailAddress, String lastname);

  // Enables the distinct flag for the query
  List<Person> findDistinctPeopleByLastnameOrFirstname(String lastname, String firstname);
  List<Person> findPeopleDistinctByLastnameOrFirstname(String lastname, String firstname);

  // Enabling ignoring case for an individual property
  // 无视大小写
  List<Person> findByLastnameIgnoreCase(String lastname);
  // Enabling ignoring case for all suitable properties
  List<Person> findByLastnameAndFirstnameAllIgnoreCase(String lastname, String firstname);

  // Enabling static ORDER BY for a query
  // 排序
  List<Person> findByLastnameOrderByFirstnameAsc(String lastname);
  List<Person> findByLastnameOrderByFirstnameDesc(String lastname);
}
```
需要知道的几点：
- 除了<code>AND, OR</code>之外，也支持<code>Between, LessThan, GreaterThan, Like</code>。这些运算符的实际效果取决于使用的底层数据库
- <code>IgnoreCase</code>用于无视特定属性的大小写，<code>AllIgnoreCase</code>用于无视所有的支持无视操作的属性（一般是String）。是否有效实际上还是取决于数据库。  
  
## Property expressions
如果<code>Person</code>有<code>Address</code>属性，而<code>Address</code>有<code>ZipCode</code>属性，那么以下方法名仍能生成你心里想着的query。过程如下：
1. Spring在<code>Person</code>里找<code>AddressZipCode</code>属性，没找到
2. Spring按驼峰从右往左分割，第一次，它分割为<code>AddressZip</code>和<code>Code</code>，但还是没找到<code>AddressZip</code>属性。
3. Spring将分割点左移，分割为<code>Address</code>和<code>ZipCode</code>，它在<code>Person</code>找到了<code>Address</code>属性，又在<code>Address</code>中找到了<code>ZipCode</code>属性，成功。  
  
```java
List<Person> findByAddressZipCode(ZipCode zipCode);
```
以上仍可能出错，例如Person有<code>Address，AddressZip</code>属性，而<code>Address</code>有<code>ZipCode</code>属性，<code>AddressZip</code>没有<code>Code</code>属性，那么Spring匹配进<code>AddressZip</code>里边，结果没找到<code>Code</code>，就失败。  
更好的方法是用下划线，下划线被Spring保留为分隔符，<code>Address\_ZipCode</code>直接被分割成<code>Address</code>和<code>ZipCode</code>。既然如此，也要求我们在定义<code>Person</code>，<code>Address</code>类时，属性名不要使用下划线，而使用纯正的驼峰命名。
```java
List<Person> findByAddress_ZipCode(ZipCode zipCode);
```
## Special parameter handling
```java
Page<User> findByLastname(String lastname, Pageable pageable);

Slice<User> findByLastname(String lastname, Pageable pageable);

List<User> findByLastname(String lastname, Sort sort);

List<User> findByLastname(String lastname, Pageable pageable);
```
第一个函数允许传入一个<code>Pageable</code>来动态的分页查询。一个<code>Page</code>知道可用元素和页面的总数。它通过触发一个计数查询来计算总的数量。这就可以在一定程度上会占用很大的内存，这个时候就可以用<code>Slice</code>。<code>Slice</code>仅仅知道是否是否还有下一个<code>Slice</code>。从类的关系上来说，<code>Slice</code>是<code>Page</code>的子类。
## Limiting query results
查询的结果的数量可以用<code>first</code>或者<code>top</code>来限制，这两个是等价的。
```java
User findFirstByOrderByLastnameAsc();

User findTopByOrderByAgeDesc();

Page<User> queryFirst10ByLastname(String lastname, Pageable pageable);

Slice<User> findTop3ByLastname(String lastname, Pageable pageable);

List<User> findFirst10ByLastname(String lastname, Sort sort);

List<User> findTop10ByLastname(String lastname, Pageable pageable);
```
limiting expressions同样支持<code>Distinct</code>关键词。当将结果限制到一个的时候，<code>Optional</code>是支持的。limiting query同<code>Sort</code>相结合可以进行K最小或者K最大的查询。
## Streaming query results
```java
@Query("select u from User u")
Stream<User> findAllByCustomQueryAndStream();

Stream<User> readAllByFirstnameNotNull();

@Query("select u from User u")
Stream<User> streamAllPaged(Pageable pageable);
```
返回结果可以是Java8的<code>Stream</code>，但要注意要关闭流，建议使用Java7的try-with-resources block
```java
try (Stream<User> stream = repository.findAllByCustomQueryAndStream()) {
  stream.forEach(…);
}
```
<font color='red'>并不是所有的Spring Data modules都支持Stream的返回值</font>
## Async query results
可以很便捷的实现异步的操作
```java
// Use java.util.concurrent.Future as return type.
@Async
Future<User> findByFirstname(String firstname);

// Use a Java 8 java.util.concurrent.CompletableFuture as return type.
@Async
CompletableFuture<User> findOneByFirstname(String firstname);

//Use a org.springframework.util.concurrent.ListenableFuture as return type.
@Async
ListenableFuture<User> findOneByLastname(String lastname);  
```