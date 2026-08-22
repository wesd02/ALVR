package com.questhub.gamepadrepair;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.adb.android.AndroidUtils;

public final class AdbClient {
    private static final int MAX_SHELL_COMMAND_CHARS = 96;
    private AdbClient() {}

    public static final class DiscoveredPort {
        public final String host;
        public final int port;
        DiscoveredPort(String host, int port) { this.host = host; this.port = port; }
    }

    public static DiscoveredPort discoverPairingPort(Context context, long timeoutMs) throws InterruptedException {
        return discover(context, AdbMdns.SERVICE_TYPE_TLS_PAIRING, timeoutMs);
    }

    public static DiscoveredPort discoverConnectionPort(Context context, long timeoutMs) throws InterruptedException {
        return discover(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, timeoutMs);
    }

    private static DiscoveredPort discover(Context context, String serviceType, long timeoutMs) throws InterruptedException {
        AtomicReference<String> host = new AtomicReference<>("");
        AtomicInteger port = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);
        AdbMdns mdns = new AdbMdns(context.getApplicationContext(), serviceType, (InetAddress address, int discoveredPort) -> {
            if (discoveredPort > 0) {
                host.set(address == null ? "" : address.getHostAddress());
                port.set(discoveredPort);
                latch.countDown();
            }
        });
        mdns.start();
        try { latch.await(Math.max(1000L, timeoutMs), TimeUnit.MILLISECONDS); }
        finally { mdns.stop(); }
        return new DiscoveredPort(host.get(), port.get());
    }

    public static boolean pair(Context context, int pairingPort, String pairingCode) throws Exception {
        if (pairingPort <= 0 || pairingPort > 65535) throw new IllegalArgumentException("invalid pairing port");
        if (pairingCode == null || !pairingCode.matches("[0-9]{6}")) throw new IllegalArgumentException("pairing code must be six digits");
        String host = AndroidUtils.getHostIpAddress(context.getApplicationContext());
        return AdbConnectionManager.getInstance(context).pair(host, pairingPort, pairingCode);
    }

    public static boolean autoConnect(Context context) throws Exception {
        AdbConnectionManager manager = AdbConnectionManager.getInstance(context);
        try { if (manager.autoConnect(context.getApplicationContext(), 6000)) return true; }
        catch (AdbPairingRequiredException ignored) { return false; }
        DiscoveredPort found = discoverConnectionPort(context, 5000L);
        if (found.port <= 0) return false;
        String host = found.host == null || found.host.isEmpty() ? AndroidUtils.getHostIpAddress(context.getApplicationContext()) : found.host;
        return manager.connect(host, found.port);
    }

    public static boolean connect(Context context, int port) throws Exception {
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("invalid ADB port");
        return AdbConnectionManager.getInstance(context).connect(AndroidUtils.getHostIpAddress(context.getApplicationContext()), port);
    }

    public static boolean isConnected(Context context) {
        try { return AdbConnectionManager.getInstance(context).isConnected(); }
        catch (Exception ignored) { return false; }
    }

    public static String shell(Context context, String command) throws Exception {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("empty command");
        if (command.length() > MAX_SHELL_COMMAND_CHARS) throw new IllegalArgumentException("command too long for safe local ADB transport");
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) throw new IllegalArgumentException("invalid command characters");
        try (AdbStream stream = AdbConnectionManager.getInstance(context).openStream("shell:" + command);
             InputStream in = stream.openInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
                if (out.size() > 4 * 1024 * 1024) throw new IllegalStateException("shell output exceeded 4 MiB limit");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    public static void disconnect(Context context) {
        try { AdbConnectionManager.getInstance(context).disconnect(); }
        catch (Exception ignored) {}
    }
}
