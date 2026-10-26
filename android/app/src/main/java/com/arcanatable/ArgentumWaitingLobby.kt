package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Host-side admission model for a future 2–4-seat Argentum waiting room.
 *
 * No engine or socket is started while this room is open. The transport must first validate
 * the guest's deck with OfflineGameSetup.lanSeat, and only then call [seatGuest].
 * Messages expose names/commanders and seat identities, never decklists or private zones.
 */
internal class ArgentumWaitingLobby(
    private val host: LocalSeat,
    private val aiOptions: List<LocalSeat>,
    val seatCount: Int,
) {
    init {
        require(seatCount in 2..4) { "Argentum tables support two to four seats." }
        require(aiOptions.size >= seatCount - 1) { "Each opponent seat needs an AI fallback." }
    }

    enum class Kind { FRIEND, AI }

    private val ids: List<EntityId> = listOf(
        LocalArgentumHost.HUMAN_ID,
        LocalArgentumHost.AI_ID,
        EntityId("offline-ai-2"),
        EntityId("offline-ai-3"),
    ).take(seatCount)
    private val kinds = MutableList(seatCount - 1) { Kind.AI }
    private val admitted = mutableMapOf<Int, LocalSeat>()
    private var started = false

    /** Configure unoccupied opponent slots; callers must not evict connected guests implicitly. */
    fun setKind(index: Int, kind: Kind): Boolean {
        if (started || index !in 1 until seatCount || index in admitted) return false
        kinds[index - 1] = kind
        return true
    }

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

    /** An unstarted guest may leave without affecting any other reserved seat. */
    fun guestLeft(seatId: EntityId): Boolean {
        if (started) return false
        val index = ids.indexOf(seatId)
        return index > 0 && admitted.remove(index) != null
    }

    fun openFriendSeats(): Int = (1 until seatCount).count {
        kinds[it - 1] == Kind.FRIEND && it !in admitted
    }

    fun ready(): Boolean = !started && openFriendSeats() == 0

    /** A snapshot for *every* waiting phone; decks and other private data never leave the host. */
    fun lobbyMessage(): JSONObject = JSONObject()
        .put("type", "lanLobby")
        .put("seatCount", seatCount)
        .put("canStart", ready())
        .put("openSeats", openFriendSeats())
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
                    .put("name", if (kind == "waiting") "Waiting for a friend" else seat.name)
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

    /** Freeze the roster only after every requested human seat has joined. */
    fun start(): Roster? {
        if (!ready()) return null
        val players = ids.indices.map { i ->
            if (i == 0) host else admitted[i] ?: aiOptions[i - 1]
        }
        val guests = admitted.map { (i, seat) -> ids[i] to seat }.toMap()
        started = true
        return Roster(players, guests)
    }
}
