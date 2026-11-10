package io.github.dovecoteescapee.byedpi.core

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Embedded ByeDPI SOCKS listener.
 *
 * Native JNI symbols and GPL-3.0 native sources are derived from
 * dovecoteescapee/ByeDPIAndroid at 43db0955de687d43f9473374dc7c5548eaa55359.
 */
class ByeDpiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    @Volatile private var socketFd: Int = -1
    @Volatile private var worker: Thread? = null

    fun startProxy(commandLine: String): Boolean {
        val fd: Int
        synchronized(this) {
            if (socketFd >= 0) return true

            // Native parameters are global. Reclaim them only after the previous
            // event-loop worker has fully exited.
            val previousWorker = worker
            if (previousWorker != null) {
                if (previousWorker.isAlive) return false
                jniResetProxyState()
                worker = null
            }

            val parsed = shellSplit(commandLine)
            if (parsed.isEmpty()) return false
            val args = arrayOf("ciadpi") + parsed
            fd = jniCreateSocketWithCommandLine(args)
            if (fd < 0) return false

            socketFd = fd
            val nativeWorker = Thread({
                try {
                    jniStartProxy(fd)
                } finally {
                    // These fields are volatile. Do not take the monitor here:
                    // stopProxy joins this worker while holding that monitor.
                    if (socketFd == fd) socketFd = -1
                    // Keep the Thread reference until stop/start reaps it and
                    // resets the native global parameters after event_loop exits.
                }
            }, "ByeDPI-native-proxy")
            nativeWorker.isDaemon = true
            worker = nativeWorker
            nativeWorker.start()
        }

        // Verify outside the monitor: the native worker must be able to publish
        // its exit state while the listener is being checked.
        if (!waitForListener(fd)) {
            stopProxy()
            return false
        }
        return true
    }

    private fun waitForListener(expectedFd: Int): Boolean {
        val deadline = System.currentTimeMillis() + 1500L
        while (System.currentTimeMillis() < deadline) {
            if (socketFd != expectedFd || worker?.isAlive != true) return false
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", 1080), 250)
                    socket.soTimeout = 500
                    // Complete a SOCKS5 no-auth greeting instead of treating
                    // any process listening on TCP/1080 as a healthy proxy.
                    socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
                    socket.getOutputStream().flush()
                    val version = socket.getInputStream().read()
                    val method = socket.getInputStream().read()
                    if (version == 0x05 && method == 0x00) return true
                }
            } catch (_: Exception) {
                Thread.sleep(50)
            }
        }
        return false
    }

    @Synchronized
    fun stopProxy(): Boolean {
        val fd = socketFd
        val currentWorker = worker ?: return false

        if (fd >= 0) jniStopProxy(fd)
        if (fd >= 0) socketFd = -1

        // The native event loop must finish before reset_params() frees its
        // global buffers. Never clean up native state while that thread is live.
        currentWorker.join(3000)
        val stopped = !currentWorker.isAlive
        if (stopped) {
            jniResetProxyState()
            if (worker === currentWorker) worker = null
        }
        return stopped
    }

    fun isRunning(): Boolean = socketFd >= 0

    private fun shellSplit(value: String): Array<String> {
        val result = ArrayList<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var started = false

        for (ch in value) {
            if (escaped) {
                token.append(ch)
                escaped = false
                started = true
                continue
            }
            if (ch == '\\' && quote != '\'') {
                escaped = true
                started = true
                continue
            }
            if (quote != null) {
                if (ch == quote) quote = null else token.append(ch)
                started = true
                continue
            }
            if (ch == '\'' || ch == '"') {
                quote = ch
                started = true
            } else if (ch.isWhitespace()) {
                if (started) {
                    result.add(token.toString())
                    token.setLength(0)
                    started = false
                }
            } else {
                token.append(ch)
                started = true
            }
        }
        if (escaped) token.append('\\')
        if (quote != null) throw IllegalArgumentException("Unclosed quote in ByeDPI arguments")
        if (started) result.add(token.toString())
        return result.toTypedArray()
    }

    private external fun jniCreateSocketWithCommandLine(args: Array<String>): Int
    private external fun jniStartProxy(fd: Int): Int
    private external fun jniStopProxy(fd: Int): Int
    private external fun jniResetProxyState()
}
