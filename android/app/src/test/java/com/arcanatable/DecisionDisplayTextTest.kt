package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionDisplayTextTest {
    @Test fun jsonNullAndMissingLabelsNeverRenderAsNull() {
        val data = JSONObject().put("prompt", JSONObject.NULL)
            .put("yesText", JSONObject.NULL)
            .put("noText", JSONObject.NULL)
        assertEquals("Argentum needs a choice", data.decisionDisplayText("prompt", "Argentum needs a choice"))
        assertEquals("YES", data.decisionDisplayText("yesText", "YES"))
        assertEquals("NO", data.decisionDisplayText("noText", "NO"))
        assertEquals("SELECT NONE", data.decisionDisplayText("declineLabel", "SELECT NONE"))
    }

    @Test fun whitespaceAndLiteralNullLabelsUseFallback() {
        val data = JSONObject().put("yesText", "   ").put("noText", "null")
        assertEquals("YES", data.decisionDisplayText("yesText", "YES"))
        assertEquals("NO", data.decisionDisplayText("noText", "NO"))
    }

    @Test fun realCustomLabelsArePreserved() {
        val data = JSONObject().put("prompt", "  Sacrifice a Food?  ")
            .put("yesText", "Pay the cost")
            .put("noText", "Don't pay")
        assertEquals("Sacrifice a Food?", data.decisionDisplayText("prompt", "fallback"))
        assertEquals("Pay the cost", data.decisionDisplayText("yesText", "YES"))
        assertEquals("Don't pay", data.decisionDisplayText("noText", "NO"))
    }
}
