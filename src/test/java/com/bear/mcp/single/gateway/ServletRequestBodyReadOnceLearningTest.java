package com.bear.mcp.single.gateway;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 演示 Servlet 请求体默认只能读一次。
 *
 * <p>如果 Filter 直接读取原始 request body，再放行给后面的 Spring AI MCP 处理器，
 * 后续处理器再次读取 body 时就可能读不到，甚至直接抛异常。所以项目里的 MCP Filter
 * 会先用 CachedRequest 缓存 body，再把包装后的 request 继续传下去。</p>
 */
class ServletRequestBodyReadOnceLearningTest {

    @Test
    void testNoCache() throws Exception {
        System.out.println();
        System.out.println("========== 反例：Filter 直接读取原始 request body ==========");

        MockHttpServletRequest request = mcpRequest();

        filterReadBodyAndPassOriginalRequest(request);
        boolean secondReadSuccess = downstreamReadBodyAgain(request);

        if (secondReadSuccess) {
            throw new RuntimeException("演示失败：原始 request 不应该被读取两次");
        }
    }

    @Test
    void testWithCache() throws Exception {
        System.out.println();
        System.out.println("========== 正例：Filter 使用 CachedRequest 缓存 body ==========");

        MockHttpServletRequest request = mcpRequest();

        CachedRequest cachedRequest = filterReadBodyAndPassCachedRequest(request);
        boolean secondReadSuccess = downstreamReadBodyAgain(cachedRequest);

        if (!secondReadSuccess) {
            throw new RuntimeException("演示失败：CachedRequest 应该允许后面再次读取 body");
        }
    }

    private MockHttpServletRequest mcpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json");
        request.setContent("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"hello","arguments":{}}}
                """.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private void filterReadBodyAndPassOriginalRequest(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("Filter：第一次读取原始 request body，body = " + body);
        System.out.println("Filter：继续放行原始 request，后面再读就会出问题");
    }

    private CachedRequest filterReadBodyAndPassCachedRequest(HttpServletRequest request) throws IOException {
        CachedRequest cachedRequest = new CachedRequest(request);
        String body = cachedRequest.body();
        System.out.println("Filter：第一次读取 body，body = " + body);
        System.out.println("Filter：放行 CachedRequest，后面还能从缓存里再读一次");
        return cachedRequest;
    }

    private boolean downstreamReadBodyAgain(HttpServletRequest request) {
        System.out.println("下游处理器：模拟 Spring AI / Controller，准备第二次读取 body");
        try {
            String body = request.getReader().lines().collect(Collectors.joining("\n"));
            System.out.println("下游处理器：第二次读取成功，body = " + body);
            return true;
        } catch (Exception e) {
            System.out.println("下游处理器：第二次读取失败，异常 = " + e.getClass().getSimpleName());
            System.out.println("下游处理器：失败原因 = " + e.getMessage());
            return false;
        }
    }

    private static class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        private String body() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }

                @Override
                public int read() {
                    return inputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
