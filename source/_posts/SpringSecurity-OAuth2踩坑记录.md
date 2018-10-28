title: SpringSecurity OAuth2踩坑记录
author: xdkxlk
tags:
  - Spring
  - SpringSecurity
categories:
  - 后端
date: 2018-10-28 19:43:00
---
背景，用户权限什么的使用的JWT
# Principal自定义解析
由于生成的token的内容和SpringSecurity默认的不一样，所以需要自定义解析
```java
public class OAuth2PrincipalExtractor implements PrincipalExtractor {

    @Override
    public Object extractPrincipal(Map<String, Object> map) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonVal = objectMapper.writeValueAsString(map);
            return objectMapper.readValue(jsonVal, AuthUser.class);
        } catch (Exception e) {
            return null;
        }
    }
}
```
然后注册成一个Bean就好了
# Authorities自定义解析
同样，然后注册成一个Bean
```java
public class OAuth2AuthoritiesExtractor implements AuthoritiesExtractor {

    @Override
    public List<GrantedAuthority> extractAuthorities(Map<String, Object> map) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonVal = objectMapper.writeValueAsString(map);
            Collection<AuthUserAuthority> authorities = objectMapper
                    .readValue(jsonVal, AuthUser.class)
                    .getAuthorities();
            return new ArrayList<>(authorities);
        } catch (Exception e) {
            return Collections.EMPTY_LIST;
        }
    }
}
```
## 为什么要自定义解析
这样之后的好处，可以直接使用SpringSecurity的<code>hasAuthority</code>和<code>@PreAuthorize("hasAuthority('SUPPLY')")</code>
```java
@EnableResourceServer
@Configuration
public class ResourceServerConfiguration extends BaseResourceServerConfig {

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .antMatchers("/customer")
                .hasAuthority("CUSTOMER")
                .antMatchers("/supply")
                .hasAuthority("SUPPLY");
    }
}
```
原来由于Spring不知道怎么解析你的数据，所以这些东西都不能用。
# OPTIONS请求401错误
由于前端发过来OPTIONS请求没有token，而且这个请求一般都是浏览器发的，所以不应该拦截，~~但是不知道SpringSecurity为什么拦截了~~
## 方法一
```java
@Configuration
@EnableWebSecurity
@Order(ConfigOrder.SECURITY_CONFIGURATION)
public class CommonSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(HttpMethod.OPTIONS);
    }
}
```
## 方法二
使用Filter
```java
@Slf4j
@Component
@Order(ConfigOrder.CROSS_ORIGIN_FILTER)
public class CommonFilter implements Filter {

	@Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

       if("OPTIONS".equalsIgnoreCase(request.getMethod())){
            response.setStatus(HttpServletResponse.SC_OK);
       }else {
            filterChain.doFilter(servletRequest, response);
       }
    }
}
```
# permitAll的路径如果带上了token，还是会验证token
思路，注册一个优先级高于 <code>ResourceServerConfig</code>的<code>WebSecurityConfigurer</code>，在这里面放行
## CommonSecurityConfiguration
```java
@Configuration
@EnableWebSecurity
@Order(ConfigOrder.SECURITY_CONFIGURATION) 
//Ordered.HIGHEST_PRECEDENCE + 2
public class CommonSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final NoAuthPathProperties noAuthPathProperties;

    @Autowired
    public CommonSecurityConfiguration(NoAuthPathProperties noAuthPathProperties) {
        this.noAuthPathProperties = noAuthPathProperties;
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(HttpMethod.OPTIONS);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        List<RequestMatcher> requestMatchers = getRequestMatcherList();

        http
                .requestMatcher(new OrRequestMatcher(requestMatchers))
                .csrf().disable()
                .authorizeRequests()
                .anyRequest()
                .permitAll();
    }

    private List<RequestMatcher> getRequestMatcherList() {
        List<RequestMatcher> res = Arrays.stream(SwaggerRequestUrl.URLS).map(AntPathRequestMatcher::new)
                .collect(Collectors.toList());

        if (Objects.nonNull(noAuthPathProperties.getUrls())
                && !noAuthPathProperties.getUrls().isEmpty()) {
            List<RequestMatcher> noauth = noAuthPathProperties.getUrls().stream()
                    .map(AntPathRequestMatcher::new)
                    .collect(Collectors.toList());
            res.addAll(noauth);
        }

        return res;
    }
}
```
## NoAuthPathProperties
作为一个放行路径的配置文件，便于使用
```java
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "no-auth-path")
public class NoAuthPathProperties {

    private List<String> urls;
}
```
```yml
no-auth-path:
  urls:
   - "/login"
   - "/view/**"
```
## 一些要注意的点
注意这段代码
```java
http
                .requestMatcher(new OrRequestMatcher(requestMatchers))
                .csrf().disable()
                .authorizeRequests()
                .anyRequest()
                .permitAll();
```
<code>requestMatcher</code>意味着这个HttpSecurity仅仅只在匹配的这些地址下运行。由于我们想要的就是放行，所以，仅仅只需要在这些路径下的<code>anyRequest</code>（所有地址）<code>permitAll</code>就可以了。不在这些路径的权限就丢给后面的HttpSecurity了。  
这样还有一个好处，就是，由于这个在ResourceServerConfig前面执行，所以，ResourceServerConfig只需要专注要登录验证的路径就好了，甚至可以直接
```java
//ResourceServerConfig.java
@Override
public void configure(HttpSecurity http) throws Exception {
    http
            .csrf().disable()
            .authorizeRequests()
            .anyRequest()
            .authenticated();
}
```
这里的<code>anyRequest</code>其实指的是**除开放行的路径剩下的**。
# 自定义AccessDeniedException处理
```java
@Slf4j
public class CommonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        MsgResult<Void> result = new MsgResult<>(ResultCode.UNAUTHORIZED, accessDeniedException.getMessage());
        
        //这里其实就是response.getWriter().write写数据
        ResponseUtil.response(response, result);
    }
}
```
然后在<code>ResourceServerConfig</code>里面配置
```java
public class BaseResourceServerConfig extends ResourceServerConfigurerAdapter {

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources.accessDeniedHandler(accessDeniedHandler);
    }
}

```
# @Order提示 java: 元素值必须为常量表达式
这个其实是Java语法的问题，~~不知道写在哪儿就先写在这里吧~~。  
开始我是这么写的
```java
public final class ConfigOrder {

    public static final Integer CROSS_ORIGIN_FILTER = Ordered.HIGHEST_PRECEDENCE + 1;

    public static final Integer SECURITY_CONFIGURATION = CROSS_ORIGIN_FILTER + 1;
}
```
**注意，Integer应该写成int，就可以了**
```java
public final class ConfigOrder {

    public static final int CROSS_ORIGIN_FILTER = Ordered.HIGHEST_PRECEDENCE + 1;

    public static final int SECURITY_CONFIGURATION = CROSS_ORIGIN_FILTER + 1;
}
```