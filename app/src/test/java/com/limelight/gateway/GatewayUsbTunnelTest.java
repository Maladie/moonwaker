package com.limelight.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GatewayUsbTunnelTest {
    private static final GatewayConnection CONNECTION = new GatewayConnection(
            "https://host:8785", "secret",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "kid");

    private static InputStream answer(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test public void upgradeCarriesTheAuthenticationOfEveryOtherCall() {
        GatewayTransport transport = new GatewayTransport(() -> "usb-request");
        Map<String, String> headers = transport.buildRequestHeaders(CONNECTION, false, false);
        headers.put("Connection", "Upgrade");
        headers.put("Upgrade", GatewayTransport.USB_TUNNEL_PROTOCOL);
        String request = new String(GatewayTransport.upgradeRequest(
                "/api/v1/usb/attachments/" + "a".repeat(32) + "/tunnel", "host:8785", headers),
                StandardCharsets.UTF_8);
        assertTrue(request.startsWith("GET /api/v1/usb/attachments/" + "a".repeat(32)
                + "/tunnel HTTP/1.1\r\nHost: host:8785\r\n"));
        assertTrue(request.contains("Authorization: Bearer secret\r\n"));
        assertTrue(request.contains("X-WakePlay-Profile: kid\r\n"));
        assertTrue(request.contains("X-MoonWaker-Profile-Session: "));
        assertTrue(request.contains("X-Request-Id: usb-request\r\n"));
        assertTrue(request.contains("\r\nConnection: Upgrade\r\n"));
        assertTrue(request.contains("\r\nUpgrade: moonwaker-usbip/1\r\n"));
        assertTrue(request.endsWith("\r\n\r\n"));
    }

    @Test public void switchingProtocolsLeavesTheTunnelBytesUnread() throws IOException {
        InputStream input = answer("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: moonwaker-usbip/1\r\nConnection: Upgrade\r\n\r\nUSBIP");
        GatewayTransport.readUpgradeAnswer(input);
        assertEquals('U', input.read());
    }

    @Test public void aRefusalArrivesWithItsReason() throws IOException {
        String body = "{\"ok\":false,\"reason\":\"usb_child_session_required\"}";
        try {
            GatewayTransport.readUpgradeAnswer(answer("HTTP/1.0 403 Forbidden\r\n"
                    + "Content-Type: application/json\r\nContent-Length: " + body.length()
                    + "\r\n\r\n" + body));
            fail("A refusal must throw.");
        } catch (GatewayTransport.GatewayException refused) {
            assertEquals(403, refused.statusCode());
            assertEquals("usb_child_session_required", refused.reason());
        }
    }

    @Test public void anotherProtocolOrAnUnboundedHeadIsRefused() throws IOException {
        try {
            GatewayTransport.readUpgradeAnswer(answer(
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n\r\n"));
            fail("Another protocol must not be accepted.");
        } catch (ProtocolException expected) {
            // Refused.
        }
        try {
            GatewayTransport.readUpgradeAnswer(answer("HTTP/1.1 200 OK\r\nX: "
                    + "y".repeat(20_000) + "\r\n\r\n"));
            fail("An unbounded head must not be read.");
        } catch (GatewayTransport.ResponseTooLargeException expected) {
            // Bounded.
        }
        try {
            GatewayTransport.readUpgradeAnswer(answer("garbage\r\n\r\n"));
            fail("A malformed status line must not be accepted.");
        } catch (ProtocolException expected) {
            // Refused.
        }
    }
}
