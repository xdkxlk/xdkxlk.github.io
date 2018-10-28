title: Swagger配置
author: xdkxlk
tags:
  - Swagger
categories:
  - 后端
date: 2018-10-28 20:21:00
---
# 解决方案
主要是Swagger的资源文件的映射问题。核心代码(配置WebMvcConfigurerAdapter)
```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    super.addResourceHandlers(registry);
    registry.addResourceHandler("swagger-ui.html")
            .addResourceLocations("classpath:/META-INF/resources/");
    registry.addResourceHandler("/webjars/**")
            .addResourceLocations("classpath:/META-INF/resources/webjars/");
}
```
然后，如果进行了登录拦截，需要将这些路径放行
```
"/swagger-ui.html",
"/swagger-*/**",
"/webjars/**",
"/v2/api-docs"
```
我怎么找到的这些资源路径？
![upload successful](/img/xYxR1eo5s00SRRSHmhei.png)
这些资源文件都在<code>springfox-swagger-ui</code>的<code>META-INF</code>下面  
# 代码
## CommonSwaggerConfig
```java
@Configuration
@EnableSwagger2
public class CommonSwaggerConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private SwaggerProperties swaggerProperties;

    @Bean
    public Docket docket() {
        Assert.state(!StringUtils.isEmpty(swaggerProperties.getBasePackage()),
                "swagger.basePackage 不可为空");

        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage(swaggerProperties.getBasePackage()))
                .paths(PathSelectors.any())
                .build()
                .securitySchemes(securitySchemes())
                .securityContexts(securityContexts());
    }

    private List<ApiKey> securitySchemes() {
        return Lists.newArrayList(new ApiKey("Authorization", "Authorization", "header"));
    }

    private List<SecurityContext> securityContexts() {
        return Lists.newArrayList(
                SecurityContext.builder()
                        .securityReferences(securityReferences())
                        .forPaths(PathSelectors.any())
                        .build()
        );
    }

    private List<SecurityReference> securityReferences() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;

        return Lists.newArrayList(new SecurityReference("Authorization",
                authorizationScopes));
    }

    private ApiInfo apiInfo() {
        Assert.state(!StringUtils.isEmpty(swaggerProperties.getTitle()),
                "swagger.title 不可为空");
        Assert.state(!StringUtils.isEmpty(swaggerProperties.getVersion()),
                "swagger.version 不可为空");

        return new ApiInfoBuilder()
                .title(swaggerProperties.getTitle())
                .version(swaggerProperties.getVersion())
                .build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        super.addResourceHandlers(registry);
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
```
## SwaggerProperties
```java
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "swagger")
public class SwaggerProperties {

    private String basePackage;

    private String title;

    private String version;
}
```
## 使用
上面的那些类可以打成jar包，其他微服务就可以直接使用文档服务了。  
application.yml
```yml
swagger:
  basePackage: "com.company.controller"
  title: "Api文档"
  version: "1.0"
```