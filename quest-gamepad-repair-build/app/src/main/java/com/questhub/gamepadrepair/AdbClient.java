package com.questhub.gamepadrepair;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.adb.android.AndroidUtils;

public final class AdbClient {
    private static final int MAX_SHELL_COMMAND_CHARS = 96;
    private static final int MAX_SHELL_OUTPUT_BYTES = 4 * 1024 * 1024;
    private static final long SHELL_TIMEOUT_SECONDS = 12L;
    private static final SecureRandom RANDOM = new SecureRandom();

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
        validateShellCommand(command);
        String marker = marker();
        String script = ShellFraming.script(command, marker);
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "qgr-adb-shell-reader");
            thread.setDaemon(true);
            return thread;
        });

        try (AdbStream stream = AdbConnectionManager.getInstance(context).openStream("shell:");
             InputStream in = stream.openInputStream();
             OutputStream out = stream.openOutputStream()) {
            Future<String> transcriptFuture = readerExecutor.submit(() -> readUntilComplete(in, marker));
            out.write(script.getBytes(StandardCharsets.UTF_8));
            out.flush();
            try {
                String transcript = transcriptFuture.get(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return ShellFraming.extract(transcript, marker);
            } catch (TimeoutException e) {
                transcriptFuture.cancel(true);
                throw new IllegalStateException("ADB shell command timed out after " + SHELL_TIMEOUT_SECONDS + " seconds", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw new IllegalStateException("ADB shell reader failed", cause);
            }
        } finally {
            readerExecutor.shutdownNow();
        }
    }

    private static String readUntilComplete(InputStream in, String marker) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (true) {
            int read = in.read(buffer);
            if (read < 0) break;
            if (read == 0) continue;
            bytes.write(buffer, 0, read);
            if (bytes.size() > MAX_SHELL_OUTPUT_BYTES) throw new IllegalStateException("shell output exceeded 4 MiB limit");
            String transcript = bytes.toString(StandardCharsets.UTF_8.name());
            if (ShellFraming.isComplete(transcript, marker)) return transcript;
        }
        String transcript = bytes.toString(StandardCharsets.UTF_8.name());
        if (ShellFraming.isComplete(transcript, marker)) return transcript;
        throw new IllegalStateException("ADB shell stream closed before command completion marker");
    }

    private static void validateShellCommand(String command) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("empty command");
        if (command.length() > MAX_SHELL_COMMAND_CHARS) throw new IllegalArgumentException("command too long for safe local ADB transport");
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) throw new IllegalArgumentException("invalid command characters");
    }

    private static String marker() {
        byte[] random = new byte[12];
        RANDOM.nextBytes(random);
        StringBuilder out = new StringBuilder("QGR_");
        for (byte value : random) out.append(String.format("%02X", value & 0xff));
        return out.toString();
    }

    public static void disconnect(Context context) {
        try { AdbConnectionManager.getInstance(context).disconnect(); }
        catch (Exception ignored) {}
    }
}
