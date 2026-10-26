package com.arcanatable

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wi-Fi play for offline Argentum: one phone runs the engine (the host), the other (the guest)
 * plays through it over the local network. Messages are the same `/game` JSON the offline host
 * already speaks, one object per line over TCP. The host announces itself by UDP broadcast so the
 * guest can find it without typing an address.
 */
object ArgentumLan {
    const val GAME_PORT = 38472
    const val DISCOVERY_PORT = 38473
    private const val TAG = "ARCANA-ARGENTUM"

    /** This phone's address on the local network ("" when it has none). */
    fun localAddress(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { n -> n.name.lowercase().let { if (it.startsWith("wlan") || it.startsWith("wifi") || it.startsWith("ap")) 1 else 0 } }
            .asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress.orEmpty()
    }.getOrDefault("")

    fun announcement(name: String) = "$TAG|${name.replace('|', ' ').take(32)}|$GAME_PORT"

    /** A host found on the network: its address and the name its player gave. */
    data class FoundHost(val address: String, val name: String)

    /** Listens for host announcements for [timeoutMs]; returns the hosts heard, newest name per address. */
    fun discover(timeoutMs: Long = 3_000): List<FoundHost> {
        val found = linkedMapOf<String, FoundHost>()
        runCatching {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(DISCOVERY_PORT))
                val end = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(512)
                while (System.currentTimeMillis() < end) {
                    socket.soTimeout = (end - System.currentTimeMillis()).coerceIn(50, 1_000).toInt()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val parts = String(packet.data, 0, packet.length).split('|')
                    if (parts.size >= 2 && parts[0] == TAG) {
                        val address = packet.address.hostAddress ?: continue
                        found[address] = FoundHost(address, parts[1])
                    }
                }
            }
        }
        return found.values.toList()
    }
}

/** The host phone's link to its guest (over Wi-Fi or online). */
interface ArgentumHostLink {
    fun send(message: JSONObject): Boolean
    fun close()
}

/** The guest phone's link to the host (over Wi-Fi or online). */
interface ArgentumGuestLink {
    var onMessage: (JSONObject) -> Unit
    var onClosed: () -> Unit
    fun send(message: JSONObject): Boolean
    fun close()
}

/** One line-delimited JSON connection, with a background reader and an ordered writer. */
private class LanLink(private val socket: Socket, private val onMessage: (JSONObject) -> Unit, private val onClosed: () -> Unit) {
    private val open = AtomicBoolean(true)
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val writes = Executors.newSingleThreadExecutor { r -> Thread(r, "argentum-lan-write").apply { isDaemon = true } }

    fun start() {
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (open.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    runCatching { JSONObject(line) }.getOrNull()?.let(onMessage)
                }
            } catch (_: Exception) {
            } finally { close() }
        }, "argentum-lan-read").apply { isDaemon = true }.start()
    }

    fun send(message: JSONObject): Boolean {
        if (!open.get()) return false
        val text = message.toString()
        writes.execute {
            try { writer.write(text); writer.write("\n"); writer.flush() } catch (_: Exception) { close() }
        }
        return true
    }

    fun sendAndClose(message: JSONObject) {
        if (!open.get()) return
        try {
            writes.execute {
                try {
                    writer.write(message.toString())
                    writer.write("\\n")
                    writer.flush()
                } catch (_: Exception) {
                } finally {
                    close()
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            close()
        }
    }

    fun close() {
        if (!open.getAndSet(false)) return
        runCatching { socket.close() }
        writes.shutdown()
        onClosed()
    }
}

/**
 * The host phone's side: accepts one guest, announces itself until someone joins, and hands the
 * guest's first message ("lanHello", with their name and deck) to [onGuest].
 */
class ArgentumLanServer(
    private val hostName: String,
    private val onGuest: (hello: JSONObject) -> Unit,
    private val onGuestMessage: (JSONObject) -> Unit,
    private val onGuestLeft: () -> Unit,
) : ArgentumHostLink {
    private val running = AtomicBoolean(false)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var guest: LanLink? = null
    @Volatile private var helloSeen = false
    // Released once the listening socket is bound, or binding has failed.
    private val bound = CountDownLatch(1)

    /** Why the game port couldn't be opened (e.g. already in use), or null. Set before [awaitReady] returns. */
    @Volatile var bindError: Throwable? = null
        private set

    /**
     * Waits until the host is accepting guests. True once the game port is bound and listening;
     * false if binding failed (see [bindError]), the server was closed, or [timeoutMs] passed.
     */
    fun awaitReady(timeoutMs: Long): Boolean =
        bound.await(timeoutMs, TimeUnit.MILLISECONDS) && bindError == null && server != null

    fun start() {
        if (running.getAndSet(true)) return
        Thread({
            try {
                val socketServer = try {
                    ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(ArgentumLan.GAME_PORT)) }
                        .also { server = it }
                } catch (e: Exception) {
                    bindError = e
                    throw e
                } finally {
                    bound.countDown()
                }
                while (running.get()) {
                    val socket = socketServer.accept()
                    if (guest != null) { runCatching { socket.close() }; continue }
                    socket.keepAlive = true
                    socket.tcpNoDelay = true
                    lateinit var link: LanLink
                    link = LanLink(socket, onMessage = { message ->
                        if (!helloSeen) {
                            if (message.optString("type") == "lanHello") { helloSeen = true; onGuest(message) }
                        } else onGuestMessage(message)
                    }, onClosed = {
                        if (guest === link) { guest = null; if (helloSeen && running.get()) onGuestLeft() }
                    })
                    guest = link
                    link.start()
                }
            } catch (_: Exception) {
            }
        }, "argentum-lan-accept").apply { isDaemon = true }.start()
        Thread({
            // Announce until a guest has joined, so the other phone can find this one.
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val payload = ArgentumLan.announcement(hostName).toByteArray()
                    while (running.get()) {
                        if (!helloSeen) runCatching {
                            socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), ArgentumLan.DISCOVERY_PORT))
                        }
                        Thread.sleep(1_000)
                    }
                }
            }
        }, "argentum-lan-announce").apply { isDaemon = true }.start()
    }

    override fun send(message: JSONObject): Boolean = guest?.send(message) == true

    override fun close() {
        running.set(false)
        guest?.close(); guest = null
        runCatching { server?.close() }
        server = null
    }
}

/** The guest phone's side: one connection to the host, messages in both directions. */
class ArgentumLanGuest : ArgentumGuestLink {
    @Volatile override var onMessage: (JSONObject) -> Unit = {}
    @Volatile override var onClosed: () -> Unit = {}
    @Volatile private var link: LanLink? = null

    /** Connects to the host at [address] and sends [hello]. Blocking; call off the main thread. */
    fun connect(address: String, hello: JSONObject) {
        val socket = Socket()
        socket.connect(InetSocketAddress(address.trim(), ArgentumLan.GAME_PORT), 8_000)
        socket.keepAlive = true
        socket.tcpNoDelay = true
        val newLink = LanLink(socket, onMessage = { onMessage(it) }, onClosed = { onClosed() })
        link = newLink
        newLink.start()
        newLink.send(hello)
    }

    override fun send(message: JSONObject): Boolean = link?.send(message) == true

    override fun close() { link?.close(); link = null }
}

/**
 * Four-player LAN transport, kept separate from the established one-guest server.
 * Every socket is assigned one stable Argentum opponent seat. A socket's first
 * lanHello must pass host-side deck validation before [admitGuest] permits game traffic.
 * A private state update MUST use [sendTo], never a broadcast.
 */
class ArgentumLanMultiServer(
    private val hostName: String,
    private val guestSeats: List<com.wingedsheep.sdk.model.EntityId>,
    private val onGuest: (com.wingedsheep.sdk.model.EntityId, JSONObject) -> Unit,
    private val onGuestMessage: (com.wingedsheep.sdk.model.EntityId, JSONObject) -> Unit,
    private val onGuestLeft: (com.wingedsheep.sdk.model.EntityId) -> Unit,
) : ArgentumHostLink {
    init {
        val valid = setOf(
            com.arcanatable.engine.LocalArgentumHost.AI_ID,
            com.wingedsheep.sdk.model.EntityId("offline-ai-2"),
            com.wingedsheep.sdk.model.EntityId("offline-ai-3"),
        )
        require(guestSeats.isNotEmpty() && guestSeats.size <= 3 &&
            guestSeats.distinct().size == guestSeats.size && guestSeats.all { it in valid }) {
            "A LAN host supports one to three distinct remote opponent seats."
        }
    }

    private val running = AtomicBoolean(false)
    private val guard = Any()
    private val links = linkedMapOf<com.wingedsheep.sdk.model.EntityId, LanLink>()
    private val greeted = mutableSetOf<com.wingedsheep.sdk.model.EntityId>()
    private val admitted = mutableSetOf<com.wingedsheep.sdk.model.EntityId>()
    @Volatile private var server: ServerSocket? = null
    private val bound = CountDownLatch(1)
    @Volatile var bindError: Throwable? = null
        private set

    fun awaitReady(timeoutMs: Long): Boolean =
        bound.await(timeoutMs, TimeUnit.MILLISECONDS) && bindError == null && server != null

    val openSeats: Int get() = synchronized(guard) { guestSeats.size - links.size }

    fun start() {
        if (running.getAndSet(true)) return
        Thread({
            try {
                val listener = try {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(ArgentumLan.GAME_PORT))
                    }.also { server = it }
                } catch (error: Exception) {
                    bindError = error
                    throw error
                } finally {
                    bound.countDown()
                }
                while (running.get()) {
                    val socket = listener.accept()
                    val seat = synchronized(guard) { guestSeats.firstOrNull { it !in links } }
                    if (seat == null) {
                        // Send a visible full-table error, then close only this extra phone.
                        runCatching {
                            socket.getOutputStream().bufferedWriter(Charsets.UTF_8).apply {
                                write(JSONObject().put("type", "error").put("code", "WIFI")
                                    .put("message", "This table is full.").toString())
                                newLine()
                                flush()
                            }
                        }
                        runCatching { socket.close() }
                        continue
                    }
                    socket.keepAlive = true
                    socket.tcpNoDelay = true
                    // Dead sockets cannot indefinitely reserve scarce lobby seats.
                    socket.soTimeout = 15_000
                    lateinit var link: LanLink
                    link = LanLink(socket, onMessage = { message ->
                        val isCurrent = synchronized(guard) { links[seat] === link }
                        if (isCurrent) {
                            if (message.optString("type") == "lanHello") {
                                val fresh = synchronized(guard) { greeted.add(seat) }
                                if (fresh) {
                                    socket.soTimeout = 0
                                    onGuest(seat, message)
                                }
                            } else if (synchronized(guard) { seat in admitted }) {
                                onGuestMessage(seat, message)
                            }
                        }
                    }, onClosed = {
                        val departed = synchronized(guard) {
                            if (links[seat] === link) {
                                links.remove(seat)
                                greeted.remove(seat)
                                admitted.remove(seat)
                            } else false
                        }
                        if (departed && running.get()) onGuestLeft(seat)
                    })
                    synchronized(guard) { links[seat] = link }
                    link.start()
                }
            } catch (_: Exception) {
                // awaitReady exposes a bind error. Closing the host exits accept().
            }
        }, "argentum-lan-multi-accept").apply { isDaemon = true }.start()
        Thread({
            runCatching {
                DatagramSocket().use { announcements ->
                    announcements.broadcast = true
                    while (running.get()) {
                        val vacant = openSeats
                        if (vacant > 0) {
                            // Optional fourth discovery field; legacy clients ignore it.
                            val bytes = (ArgentumLan.announcement(hostName) + "|$vacant").toByteArray()
                            runCatching {
                                announcements.send(DatagramPacket(bytes, bytes.size,
                                    InetAddress.getByName("255.255.255.255"), ArgentumLan.DISCOVERY_PORT))
                            }
                        }
                        Thread.sleep(1_000)
                    }
                }
            }
        }, "argentum-lan-multi-announce").apply { isDaemon = true }.start()
    }

    /** Validated hello only. Guest messages sent before this are silently ignored. */
    fun admitGuest(seat: com.wingedsheep.sdk.model.EntityId): Boolean = synchronized(guard) {
        if (seat !in greeted || seat !in links) false
        else { admitted.add(seat); true }
    }

    fun rejectGuest(seat: com.wingedsheep.sdk.model.EntityId, reason: String) {
        val target = synchronized(guard) { links[seat] }
        target?.sendAndClose(JSONObject().put("type", "error").put("code", "WIFI")
            .put("message", reason.take(200)))
    }

    fun sendTo(seat: com.wingedsheep.sdk.model.EntityId, message: JSONObject): Boolean {
        val target = synchronized(guard) { links[seat]?.takeIf { seat in admitted } }
        return target?.send(message) == true
    }

    /** Room rosters contain no hands/decklists. Private engine state must use sendTo. */
    fun broadcastLobby(message: JSONObject): Boolean {
        if (message.optString("type") != "lanLobby") return false
        val targets = synchronized(guard) { admitted.mapNotNull(links::get) }
        return targets.all { it.send(message) }
    }

    /** Never guess a recipient for a masked engine view. */
    override fun send(message: JSONObject): Boolean = false

    override fun close() {
        running.set(false)
        val closing = synchronized(guard) {
            links.values.toList().also {
                links.clear()
                greeted.clear()
                admitted.clear()
            }
        }
        closing.forEach(LanLink::close)
        runCatching { server?.close() }
        server = null
    }
}
