package pl.mbaracz.jwebsockets;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;

import java.util.Base64;

public class Util {

    public static FullHttpRequest createHttpRequest(String path) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        HttpHeaders headers = getDefaultHeaders();
        request.headers().set(headers);
        return request;
    }

    public static void performHandshake(EmbeddedChannel channel, String path) {
        FullHttpRequest request = createHttpRequest(path);
        channel.writeInbound(request);
    }

    public static HttpHeaders getDefaultHeaders() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add(HttpHeaderNames.HOST, "http://localhost:8081");
        headers.add(HttpHeaderNames.UPGRADE, "websocket");
        headers.add(HttpHeaderNames.CONNECTION, "Upgrade");
        headers.add(HttpHeaderNames.SEC_WEBSOCKET_KEY, Base64.getEncoder().encodeToString("randomKey".getBytes()));
        headers.add(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
        return headers;
    }
}
