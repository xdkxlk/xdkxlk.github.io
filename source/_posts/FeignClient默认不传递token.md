title: 解决FeignClient默认不传递token的问题
author: xdkxlk
tags:
  - SpringCloud
categories:
  - 后端
date: 2018-08-11 22:33:00
---
在使用的过程中，发现如果使用token机制进行用户的验证标识，使用FeignClient不会默认传递Header中Authorization字段的token，需要对于token的传递进行特殊的自定义处理
## 最终的实现效果
使用自定义的<code>@OAuth2FeignClient</code>代替原来的<code>@FeignClient</code>
```java
@OAuth2FeignClient(name = "servera")
public interface ServerAClient {
    @GetMapping("/user")
    HashMap<String, Object> authDemo();
}
```
并在启动类上添加<code>@EnableFeignClients</code>和<code>@EnableOAuth2Client</code>注解
## @OAuth2FeignClient
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@FeignClient(configuration = OAuth2FeignAutoConfiguration.class)
public @interface OAuth2FeignClient {

    @AliasFor(annotation = FeignClient.class)
    String name();

    @AliasFor(annotation = FeignClient.class)
    String qualifier() default "";

    @AliasFor(annotation = FeignClient.class)
    String url() default "";

    @AliasFor(annotation = FeignClient.class)
    boolean decode404() default false;

    @AliasFor(annotation = FeignClient.class)
    Class<?> fallback() default void.class;

    @AliasFor(annotation = FeignClient.class)
    Class<?> fallbackFactory() default void.class;

    @AliasFor(annotation = FeignClient.class)
    String path() default "";

    @AliasFor(annotation = FeignClient.class)
    boolean primary() default true;
}
```
## OAuth2FeignAutoConfiguration
```java
@Configuration
public class OAuth2FeignAutoConfiguration {

    @Bean
    public RequestInterceptor oauth2FeignRequestInterceptor(OAuth2ClientContext oauth2ClientContext) {
        return new OAuth2FeignRequestInterceptor(oauth2ClientContext);
    }
}
```
## 核心OAuth2FeignRequestInterceptor
这个类将token添加到feign请求的头里面去
```java
public class OAuth2FeignRequestInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final OAuth2ClientContext oauth2ClientContext;

    public OAuth2FeignRequestInterceptor(OAuth2ClientContext oauth2ClientContext) {
        Assert.notNull(oauth2ClientContext, "Context can not be null");
        this.oauth2ClientContext = oauth2ClientContext;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(AUTHORIZATION_HEADER)) {
            return;
        }

        String token = null;
        try {
            OAuth2AccessToken oAuth2AccessToken = oauth2ClientContext.getAccessToken();
            token = oAuth2AccessToken.getValue();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (token == null) {
            try {
                OAuth2AccessToken oAuth2AccessToken = oauth2ClientContext.getAccessTokenRequest().getExistingToken();
                token = oAuth2AccessToken.getValue();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if(token != null){
            //设置header
            template.header(AUTHORIZATION_HEADER, String.format("%s %s", BEARER_TOKEN_TYPE, token));
        }
    }
}
```
## @EnableOAuthFeignClient
可以将<code>@EnableFeignClients</code>和<code>@EnableOAuth2Client</code>合并为一个注解，方便使用
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableFeignClients
@EnableOAuth2Client
public @interface EnableOAuthFeignClient {
}
```
## 附录 版本
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>Edgware.RELEASE</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>1.5.9.RELEASE</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-oauth2</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-security</artifactId>
</dependency>
```