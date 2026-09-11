package com.engine.veloce.network;

import com.engine.veloce.engine.MatchingEngine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-throughput, non-blocking Java NIO TCP socket server for external order submission.
 * Receives framed binary orders from algorithmic trading clients and routes them directly
 * to the matching engine.
 */
public final class TcpOrderServer implements Runnable {

    private final int requestedPort;
    private final MatchingEngine engine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread serverThread;
    private int boundPort;

    public TcpOrderServer(int port, MatchingEngine engine) {
        this.requestedPort = port;
        this.engine = engine;
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress("0.0.0.0", requestedPort));
        this.boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.running.set(true);
        this.serverThread = new Thread(this, "veloce-tcp-listener-" + boundPort);
        this.serverThread.setDaemon(true);
        this.serverThread.start();
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        if (selector != null) {
            selector.wakeup();
        }
        try {
            if (serverChannel != null) serverChannel.close();
            if (selector != null) selector.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                int ready = selector.select(100);
                if (ready == 0) {
                    continue;
                }

                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            } catch (IOException e) {
                if (!running.get()) {
                    break;
                }
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
            ClientConnection connection = new ClientConnection(clientChannel, engine);
            clientChannel.register(selector, SelectionKey.OP_READ, connection);
        }
    }

    private void handleRead(SelectionKey key) {
        ClientConnection connection = (ClientConnection) key.attachment();
        try {
            boolean keepOpen = connection.readAndProcess();
            if (!keepOpen) {
                key.cancel();
                connection.close();
            }
        } catch (IOException e) {
            key.cancel();
            connection.close();
        }
    }

    public int getBoundPort() {
        return boundPort;
    }

    public boolean isRunning() {
        return running.get();
    }
}
