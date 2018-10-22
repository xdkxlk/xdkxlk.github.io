title: SpringBoot项目打包的各种异常
author: xdkxlk
tags:
  - SpringBoot
  - maven
categories:
  - 后端
date: 2018-08-11 10:12:00
---
## 修改项目jdk版本
由于maven默认的jdk版本为1.5，所以maven项目报错。  
虽然可以手动修改idea的Target bytecode version等为1.8，但是每更新一次maven文件就要手动改一次，非常麻烦。

解决方法: 修改pom.xml
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.7.0</version>
    <configuration>
        <compilerArgument>-parameters</compilerArgument>
        <testCompilerArgument>-parameters</testCompilerArgument>
        <source>1.8</source>
        <target>1.8</target>
        <encoding>UTF-8</encoding>
    </configuration>
</plugin>
```
## lombok和mapstruct共存问题
由于在项目中需要同时使用lombok和mapstruct，发现本来单独使用都可以，一同时使用就会出现lombok自动生成的方法找不到的问题。

解决方法：修改pom.xml
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.7.0</version>
    <configuration>
        <compilerArgument>-parameters</compilerArgument>
        <testCompilerArgument>-parameters</testCompilerArgument>
        <source>1.8</source>
        <target>1.8</target>
        <encoding>UTF-8</encoding>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${org.mapstruct.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```
## SpringBoot项目打成jar包后无法启动
使用<code>mvn install</code>打包后，运行jar包说<code>找不到或无法加载主类</code>。

解决:  
首先需要检查下是否配置了<code>spring-boot-maven-plugin</code>插件
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>1.5.9.RELEASE</version>
</plugin>
```
但是我遇见了虽然配置了这个插件，但是可能是由于项目里面同时配置了<code>maven-compiler-plugin</code>（因为不配置会出现jdk版本问题，详见第一个），的原因，打包过后依然无法运行。

解决:  
使用命令
```bash
mvn package spring-boot:repackage
```
在由于spring-boot:repackage需要依赖sources文件，所以，如果没有没有生成xxx-sources.jar需要修改pom.xml
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```


