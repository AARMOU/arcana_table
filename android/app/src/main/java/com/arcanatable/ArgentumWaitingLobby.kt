package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.arcanatable.engine.ai.AiDifficulty
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side admission model for a 2–4-seat Argentum waiting room.
 *
 * No engine or socket is started while this room is open. The transport must first validate
 * the guest's deck with OfflineGameSetup.lanSeat, and only then call [seatGuestAt].
 * Messages expose names/commanders and seat identities, never decklists or private zones.
 *
 * The host can change the table before START: the number of seats, and whether an *unoccupied*
 * opponent seat waits for a friend or plays as AI. A seated guest is never moved or evicted
 * implicitly; only [removeGuest] takes a guest out. Every seated guest must mark themselves
 * ready ([setReady]) before [start] succeeds. The host and AI seats are always ready.
 */
internal class ArgentumWaitingLobby(
    private val host: LocalSeat,
    aiOptions: List<LocalSeat>,
    seatCount: Int,
) {
    init {
        require(seatCount in 2..4) { "Argentum tables support two to four seats." }
        require(aiOptions.size >= seatCount - 1) { "Each opponent seat needs an AI fallback." }
    }

    enum class Kind { FRIEND, AI }

    var seatCount: Int = seatCount
        private set

    private val allIds: List<EntityId> = listOf(
        LocalArgentumHost.HUMAN_ID,
        LocalArgentumHost.AI_ID,
        EntityId("offline-ai-2"),
        EntityId("offline-ai-3"),
    )
    private val aiOptions = aiOptions.toMutableList()
    private val ids: List<EntityId> get() = allIds.take(seatCount)
    // One entry per possible opponent seat (1..3), so resizing keeps each seat's choice.
    private val kinds = MutableList(3) { Kind.AI }
    private val admitted = mutableMapOf<Int, LocalSeat>()
    private val readySeats = mutableSetOf<Int>()
    private var started = false

    /** Configure unoccupied opponent slots; callers must not evict connected guests implicitly. */
    fun setKind(index: Int, kind: Kind): Boolean {
        if (started || index !in 1 until seatCount || index in admitted) return false
        kinds[index - 1] = kind
        return true
    }

    fun kindOf(index: Int): Kind? = if (index in 1 until seatCount) kinds[index - 1] else null

    /** Host-selected difficulty for an AI seat; ignored for friend seats and after START. */
    fun setAiDifficulty(index: Int, difficulty: AiDifficulty): Boolean {
        if (started || index !in 1 until seatCount || kinds[index - 1] != Kind.AI || index in admitted) return false
        aiOptions[index - 1] = aiOptions[index - 1].copy(difficulty = difficulty)
        return true
    }

    /** Whether [newCount] seats is allowed now: no seated guest may be dropped by shrinking. */
    fun canResize(newCount: Int): Boolean =
        !started && newCount in 2..4 && aiOptions.size >= newCount - 1 &&
            admitted.keys.all { it < newCount }

    /** Change the table size before START. Seats beyond the new size are simply not played. */
    fun resize(newCount: Int): Boolean {
        if (!canResize(newCount)) return false
        seatCount = newCount
        return true
    }

    /** Opponent seat kinds for the transport ("friend" or "ai"), seat 1 first. */
    fun transportKinds(): List<String> =
        (1 until seatCount).map { if (kinds[it - 1] == Kind.FRIEND) "friend" else "ai" }

    /** Returns the stable seat ID allocated to a validated guest; null means no capacity. */
    fun seatGuest(seat: LocalSeat): EntityId? {
        if (started || seat.name.isBlank() || seat.deck.isEmpty()) return null
        val index = (1 until seatCount).firstOrNull {
            kinds[it - 1] == Kind.FRIEND && it !in admitted
        } ?: return null
        admitted[index] = seat
        return ids[index]
    }

    /** Reserve the transport-assigned seat after the specific phone's deck validated. */
    fun seatGuestAt(seatId: EntityId, seat: LocalSeat): Boolean {
        val index = ids.indexOf(seatId)
        if (started || index !in 1 until seatCount || kinds[index - 1] != Kind.FRIEND ||
            index in admitted || seat.name.isBlank() || seat.deck.isEmpty()) return false
        admitted[index] = seat
        return true
    }

    /** Stable slots chosen by the host for separate remote TCP connections. */
    fun friendSeatIds(): List<EntityId> =
        (1 until seatCount).filter { kinds[it - 1] == Kind.FRIEND }.map(ids::get)

    fun isSeated(seatId: EntityId): Boolean = ids.indexOf(seatId).let { it > 0 && it in admitted }

    /** An unstarted guest may leave without affecting any other reserved seat. */
    fun guestLeft(seatId: EntityId): Boolean {
        if (started) return false
        val index = ids.indexOf(seatId)
        readySeats.remove(index)
        return index > 0 && admitted.remove(index) != null
    }

    /** The host takes a seated guest out before START. The seat reopens for another friend. */
    fun removeGuest(seatId: EntityId): Boolean = guestLeft(seatId)

    /** A seated guest marks themselves ready (or not). Unseated seats and the host cannot. */
    fun setReady(seatId: EntityId, ready: Boolean): Boolean {
        val index = ids.indexOf(seatId)
        if (started || index !in admitted) return false
        if (ready) readySeats.add(index) else readySeats.remove(index)
        return true
    }

    fun isReady(seatId: EntityId): Boolean = ids.indexOf(seatId) in readySeats

    fun openFriendSeats(): Int = (1 until seatCount).count {
        kinds[it - 1] == Kind.FRIEND && it !in admitted
    }

    /** Seated guests who have not marked ready yet, by name, in seat order. */
    fun waitingForReady(): List<String> =
        admitted.keys.filter { it < seatCount && it !in readySeats }.sorted().map { admitted.getValue(it).name }

    /** Every requested friend seat is filled. */
    fun ready(): Boolean = !started && openFriendSeats() == 0

    /** Full and every seated guest is ready: the host may start. */
    fun canStart(): Boolean = ready() && waitingForReady().isEmpty()

    /** A snapshot for *every* waiting phone; decks and other private data never leave the host. */
    fun lobbyMessage(): JSONObject = JSONObject()
        .put("type", "lanLobby")
        .put("seatCount", seatCount)
        .put("canStart", canStart())
        .put("openSeats", openFriendSeats())
        .put("notReady", JSONArray(waitingForReady()))
        .put("canResize", JSONArray((2..4).filter(::canResize)))
        .put("seats", JSONArray().apply {
            for (index in ids.indices) {
                val seat = when (index) {
                    0 -> host
                    else -> admitted[index] ?: aiOptions[index - 1]
                }
                val kind = when {
                    index == 0 -> "host"
                    index in admitted -> "human"
                    kinds[index - 1] == Kind.FRIEND -> "waiting"
                    else -> "ai"
                }
                put(JSONObject()
                    .put("seatId", ids[index].value)
                    .put("kind", kind)
                    .put("ready", kind != "waiting" && (kind != "human" || index in readySeats))
                    .put("name", if (kind == "waiting") "Waiting for a friend" else seat.name)
                    .put("difficulty", if (kind == "ai") seat.difficulty.name else JSONObject.NULL)
                    .put("commander", if (kind == "waiting") JSONObject.NULL else seat.commander ?: JSONObject.NULL))
            }
        })

    data class Roster(
        val players: List<LocalSeat>,
        val humanGuests: Map<EntityId, LocalSeat>,
    ) {
        val opponent: LocalSeat get() = players[1]
        val extraOpponents: List<LocalSeat> get() = players.drop(2)
    }

    /** Freeze the roster only after every requested human seat has joined and marked ready. */
    fun start(): Roster? {
        if (!canStart()) return null
        val players = ids.indices.map { i ->
            if (i == 0) host else admitted[i] ?: aiOptions[i - 1]
        }
        val guests = admitted.filterKeys { it < seatCount }.map { (i, seat) -> ids[i] to seat }.toMap()
        started = true
        return Roster(players, guests)
    }
}
