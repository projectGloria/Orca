package io.github.nihil.orca

import androidx.test.platform.app.InstrumentationRegistry
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Serves the tiny generated DASH fixture without depending on any third-party website. */
class FixtureServer : AutoCloseable {
    private val socket = ServerSocket(0)
    val url = "http://127.0.0.1:${socket.localPort}/dash/sample.mpd"
    private val worker = thread(isDaemon = true) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            client.use {
                runCatching {
                    val reader = it.getInputStream().bufferedReader()
                    val request = reader.readLine().split(' ')
                    var line = reader.readLine()
                    while (!line.isNullOrEmpty()) line = reader.readLine()
                    val path = request[1].substringBefore('?').removePrefix("/")
                    val data = InstrumentationRegistry.getInstrumentation().context.assets.open(path).use { stream -> stream.readBytes() }
                    val mime = if (path.endsWith(".mpd")) "application/dash+xml" else if (path.endsWith(".html")) "text/html" else "application/octet-stream"
                    val output = it.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    if (request[0] != "HEAD") output.write(data)
                    output.flush()
                }
            }
        }
    }
    override fun close() { socket.close(); worker.join(1000) }
}
