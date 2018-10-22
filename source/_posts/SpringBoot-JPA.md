---
title: SpringBoot JPA
date: 2018-01-26 15:32:46
tags: SpringData
categories: 后端
---
## 单元测试的时候，报no session的错
需要在单元测试类上面添加注解
```java
@Transactional
```
也可以继承单元测试类
```java
AbstractTransactionalJUnit4SpringContextTests
```
## 单元测试的时候，数据库会自动回滚
在使用单元测试的时候，发现对于数据库的插入、更新等操作在单元测试程序结束后会自动回滚
在单元测试类上面添加 @Rollback(false) 注解
```java
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Rollback(false)
public class CustomerRepositoryTest {
    //...
}
```
## 在将hibernate查出来的类以json格式返回的时候，报lazyInit错误
对于open session in view，SpringBoot已经默认配置为开启了，不用再多余的配置了 (open-in-view: true)
   
可以使用配置
```yml
spring:
  jackson:
    serialization:
      fail-on-empty-beans: false
```
的方式解决，但是出来的json字符串里面会有多余的 “handler”: {}, “hibernateLazyInitializer”: {} 字段    
  
<font color='red'>更好的方法。</font>jackson官方有为hibernate提供的库，以解决懒加载的问题
```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-hibernate5</artifactId>
    <version>2.9.3</version>
</dependency>
```
```java
@Bean
public Module module() {
    Hibernate5Module module = new Hibernate5Module();
    module.disable(Hibernate5Module.Feature.USE_TRANSIENT_ANNOTATION);
    module.enable(Hibernate5Module.Feature.FORCE_LAZY_LOADING);
    return module;
}
```
## lombok导致死循环
类相互之间有相互的引用，调用lombok自动生成的 toString() 或者 hashCode() 方法会导致死循环。
## Entity之间有循环引用的时候，导致转换成json字符串的时候死循环（JsonView）
可以使用JsonView自定义要转成json的字段属性，个人觉得这是一个类似于DTO的方法        
[<font color='blue'>Latest Jackson integration improvements in Spring</font>](https://spring.io/blog/2014/12/02/latest-jackson-integration-improvements-in-spring)  
```java
public class View {
    public interface Summary {
    }
    public interface SummaryWithDetail extends Summary {
    }
}

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(View.Summary.class)
    private Long id;

    @JsonView(View.Summary.class)
    private String firstName;
    @JsonView(View.Summary.class)
    private String lastName;

    @JsonView(View.SummaryWithDetail.class)
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "customer")
    private Set<Address> addressSet;
}

@RestController
@RequestMapping("/")
public class HomeController {

    @JsonView(View.SummaryWithDetail.class)
    @GetMapping("/cus")
    public Customer customer(){
        return customerService.get(4L);
    }
}
```
## SpringBoot打印hibernate的sql语句
### 方法一
SpringBoot自带，但是只能显示语句，不能显示参数
```yml
spring:
  jpa:
    show-sql: true
```
### 方法二
使用log4jdbc。其可以显示底层执行的sql语句。还会显示sql执行的时间    
pom.xml 文件
```xml
<dependency>
    <groupId>com.googlecode.log4jdbc</groupId>
    <artifactId>log4jdbc</artifactId>
    <version>1.2</version>
</dependency>
```
application.yml 文件
```yml
spring:
  datasource:
    url: jdbc:log4jdbc:mysql://localhost:3306/springdata
    driver-class-name: net.sf.log4jdbc.DriverSpy
```