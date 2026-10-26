package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

/** The host must hear a phone that takes over a freed seat, and still drop real repeats. */
class RelayInboxTest {
    @Test fun repeatsFromTheSamePhoneAreDropped() {
        val inbox = RelayInbox()
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(1, "alice", 1))
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(1, "alice", 2))
        // Replayed after a reconnect: already handled, must not be applied twice.
        assertEquals(RelayInbox.Verdict.DUPLICATE, inbox.accept(1, "alice", 2))
        assertEquals(RelayInbox.Verdict.DUPLICATE, inbox.accept(1, "alice", 1))
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(1, "alice", 3))
    }

    @Test fun aNewPhoneInAFreedSeatIsHeardFromItsFirstMessage() {
        val inbox = RelayInbox()
        for (seq in 1L..40L) inbox.accept(1, "rejected-deck", seq)
        // Same person rejoining with a fixed deck, or a different friend: numbering restarts.
        assertEquals(RelayInbox.Verdict.NEW_SENDER, inbox.accept(1, "rejoined", 1))
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(1, "rejoined", 2))
        assertEquals(RelayInbox.Verdict.DUPLICATE, inbox.accept(1, "rejoined", 2))
    }

    @Test fun seatsAreCountedSeparately() {
        val inbox = RelayInbox()
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(1, "alice", 5))
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(2, "bob", 1))
        assertEquals(RelayInbox.Verdict.SAME_SENDER, inbox.accept(3, "carol", 1))
    }
}
