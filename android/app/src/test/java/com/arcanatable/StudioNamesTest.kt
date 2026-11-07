package com.arcanatable

import org.junit.Assert.*
import org.junit.Test

/** Owner's Studio log, Nov 7: items named just "Control", and the game log screen named "Table Dialogs 22". */
class StudioNamesTest {
    @Test fun wordlessItemsGetPlainNamesButItemsWithWordsKeepThem() {
        assertEquals("Seat tab",StudioNames.orFallback("native:ArgentumUi.clickable.1/Table and player/0","Control"))
        assertEquals("Card",StudioNames.orFallback("native:TableBoardViews.combinedClickable.9/Table and player/3","Control"))
        assertEquals("Grizzly Bears",StudioNames.orFallback("native:TableBoardViews.combinedClickable.9/Table and player/3","Grizzly Bears"))
        assertEquals("Control",StudioNames.orFallback("native:Unknown.clickable.1/x/0","Control"))
    }

    @Test fun gameLogScreenHasAPlainNameAndOnlyItSharesEntryEdits() {
        assertEquals("Game log",StudioNames.screen(StudioNames.GAME_LOG_SCREEN))
        assertEquals("Table Dialogs 9",StudioNames.screen("Table Dialogs 9"))
        assertTrue(StudioNames.isGameLog("Table Dialogs 22"))
        assertFalse("\"Dialogs\" contains \"log\", but other dialogs are not the game log",StudioNames.isGameLog("Table Dialogs 9"))
    }
}
