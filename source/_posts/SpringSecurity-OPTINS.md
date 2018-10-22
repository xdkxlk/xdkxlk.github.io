title: SpringSecurity OPTIONS请求报401错误
author: xdkxlk
tags:
  - SpringSecurity
categories:
  - 后端
date: 2018-10-16 10:45:00
---
按道理，OPTIONS请求不应该进行权限的拦截判断，但是很奇怪的发现SpringSecurity对它进行拦截，然而由于OPTIONS请求并不会带上token的header，所以就会拦截掉。 
解决方法就是对于OPTIONS的请求直接放行
## 解决方法1
[Standalone Spring OAuth2 JWT Authorization Server + CORS](https://stackoverflow.com/questions/30632200/standalone-spring-oauth2-jwt-authorization-server-cors/30638914#30638914)
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCorsFilter implements Filter {

    public SimpleCorsFilter() {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, authorization");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
```
## 解决方法2
[401 Unauthorized for Options request to /oauth/token in spring](https://stackoverflow.com/questions/44115004/401-unauthorized-for-options-request-to-oauth-token-in-spring)
```java
@Override
protected void configure(HttpSecurity httpSecurity) throws Exception {

         httpSecurity.// your autherization configuration
          .antMatchers(HttpMethod.OPTIONS).permitAll()
 } 
```

