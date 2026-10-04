package pl.mbaracz.jwebsockets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageDecoder;
import pl.mbaracz.jwebsockets.message.impl.plain.PlainTextMessageEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionTest {

    // Marks a compressed message (RFC 7692, section 6)
    private static final int RSV1 = 0b100;

    // Empty block that ends a compressed message, the sender removes it (RFC 7692, section 7.2.1)
    private static final byte[] MESSAGE_TAIL = {0x00, 0x00, (byte) 0xff, (byte) 0xff};

    private static WebSocketServer<String, Object> createServer(boolean compressionEnabled) {
        return new WebSocketServer<String, Object>()
            .configure(configurer -> configurer
                .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
                .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
                .setCompressionEnabled(compressionEnabled)
            );
    }

    /**
     * Sends the handshake request with the extension offer and decodes the response the way a client does.
     */
    private static HttpResponse handshake(EmbeddedChannel channel, String extensionOffer) {
        FullHttpRequest request = Util.createHttpRequest("/");
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, extensionOffer);
        channel.writeInbound(request);

        EmbeddedChannel client = new EmbeddedChannel(new HttpResponseDecoder());
        ByteBuf buffer;

        while ((buffer = channel.readOutbound()) != null) {
            client.writeInbound(buffer);
        }

        return client.readInbound();
    }

    /**
     * Creates a channel with the server's pipeline and completes a handshake that negotiates compression.
     */
    private static EmbeddedChannel connectWithCompression(WebSocketServer<String, Object> server) {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(server));
        HttpResponse response = handshake(channel, "permessage-deflate");

        assertEquals("permessage-deflate", response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS), "Should negotiate compression");
        return channel;
    }

    /**
     * Compresses the text the way a client compresses a message (RFC 7692, section 7.2.1).
     */
    private static ByteBuf compress(String text) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(text.getBytes(StandardCharsets.UTF_8));

        byte[] buffer = new byte[1024];
        int length = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH);
        deflater.end();

        // The sync flush ends the data with the empty block, which is not sent
        return Unpooled.wrappedBuffer(buffer, 0, length - MESSAGE_TAIL.length);
    }

    /**
     * Decompresses a message the way a client does (RFC 7692, section 7.2.2).
     */
    private static String decompress(ByteBuf content) throws DataFormatException {
        byte[] compressed = ByteBufUtil.getBytes(content);

        // Restore the empty block the sender removed
        byte[] input = Arrays.copyOf(compressed, compressed.length + MESSAGE_TAIL.length);
        System.arraycopy(MESSAGE_TAIL, 0, input, compressed.length, MESSAGE_TAIL.length);

        Inflater inflater = new Inflater(true);
        inflater.setInput(input);

        byte[] buffer = new byte[1024];
        int length = inflater.inflate(buffer);
        inflater.end();

        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }

    @Test
    public void When_CompressionIsNotEnabled_Then_ItShouldNotBeNegotiated() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(createServer(false)));

        HttpResponse response = handshake(channel, "permessage-deflate; client_max_window_bits");

        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
        assertNull(response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS), "Should not negotiate any extension");
    }

    @Test
    public void When_ClientOffersPermessageDeflate_Then_ItShouldBeNegotiated() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(createServer(true)));

        HttpResponse response = handshake(channel, "permessage-deflate; client_max_window_bits");

        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
        assertEquals("permessage-deflate", response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS), "Should negotiate permessage-deflate");
    }

    @Test
    public void When_ClientRequestsServerNoContextTakeover_Then_ItShouldBeAccepted() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(createServer(true)));

        HttpResponse response = handshake(channel, "permessage-deflate; server_no_context_takeover");

        assertEquals("permessage-deflate;server_no_context_takeover", response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS),
            "Should accept server_no_context_takeover");
    }

    @Test
    public void When_ClientRequestsServerMaxWindowBits_Then_OfferShouldBeDeclined() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketServerChannelInitializer<>(createServer(true)));

        HttpResponse response = handshake(channel, "permessage-deflate; server_max_window_bits=10");

        assertEquals(HttpResponseStatus.SWITCHING_PROTOCOLS, response.status(), "Should switch protocols");
        assertNull(response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS), "Should decline the offer");
    }

    @Test
    public void When_CompressedMessageIsReceived_Then_HandlerShouldGetDecompressedMessage() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = connectWithCompression(createServer(true).onMessage((_, message) -> received.add(message)));

        Util.sendFromClient(channel, new TextWebSocketFrame(true, RSV1, compress("Hello")));

        assertEquals(List.of("Hello"), received, "Should receive the decompressed message");
    }

    @Test
    public void When_CompressionIsNegotiated_Then_SentMessageShouldBeCompressed() throws DataFormatException {
        List<WebSocketSession<String, Object>> opened = new ArrayList<>();
        EmbeddedChannel channel = connectWithCompression(createServer(true).onOpen(opened::add));

        opened.getFirst().sendMessage("Hello");

        WebSocketFrame frame = assertInstanceOf(TextWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(RSV1, frame.rsv(), "Should mark the message as compressed");
        assertEquals("Hello", decompress(frame.content()), "Should send the compressed message");
    }

    @Test
    public void When_CompressedMessageExpandsPastMaxMessageSize_Then_ConnectionShouldCloseWith1009() {
        List<String> received = new ArrayList<>();
        WebSocketServer<String, Object> server = createServer(true)
            .configure(configurer -> configurer.setMaxMessageSize(1024))
            .onMessage((_, message) -> received.add(message));

        EmbeddedChannel channel = connectWithCompression(server);

        // Send a message that compresses to a few bytes, but inflates to ten times the limit
        Util.sendFromClient(channel, new TextWebSocketFrame(true, RSV1, compress("a".repeat(10 * 1024))));

        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.MESSAGE_TOO_BIG.code(), closeFrame.statusCode(), "Should send message too big status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }

    @Test
    public void When_CompressedMessageIsCorrupted_Then_ConnectionShouldCloseWith1007() {
        List<String> received = new ArrayList<>();
        EmbeddedChannel channel = connectWithCompression(createServer(true).onMessage((_, message) -> received.add(message)));

        // 0xff starts a DEFLATE block of the reserved type 11, which cannot be inflated
        Util.sendFromClient(channel, new TextWebSocketFrame(true, RSV1, Unpooled.wrappedBuffer(new byte[]{(byte) 0xff, (byte) 0xff})));

        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.INVALID_PAYLOAD_DATA.code(), closeFrame.statusCode(), "Should send invalid payload data status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }

    // RSV1, RSV2 and RSV3, the decoder lets them through once compression is enabled
    @ParameterizedTest
    @ValueSource(ints = {0b100, 0b010, 0b001})
    public void When_FrameHasReservedBitSetWithoutNegotiatedCompression_Then_ConnectionShouldBeClosedWithProtocolError(int rsv) {
        List<String> received = new ArrayList<>();
        WebSocketServer<String, Object> server = createServer(true).onMessage((_, message) -> received.add(message));

        // Connect without offering compression
        EmbeddedChannel channel = Util.connect(server);

        Util.sendFromClient(channel, new TextWebSocketFrame(true, rsv, Unpooled.copiedBuffer("Hello", StandardCharsets.UTF_8)));

        // Assert connection was failed with a protocol error close frame
        CloseWebSocketFrame closeFrame = assertInstanceOf(CloseWebSocketFrame.class, Util.readFromServer(channel));
        assertEquals(WebSocketCloseStatus.PROTOCOL_ERROR.code(), closeFrame.statusCode(), "Should send protocol error status");
        assertFalse(channel.isOpen(), "Channel should be closed");
        assertTrue(received.isEmpty(), "Message should not be delivered");
    }
}
