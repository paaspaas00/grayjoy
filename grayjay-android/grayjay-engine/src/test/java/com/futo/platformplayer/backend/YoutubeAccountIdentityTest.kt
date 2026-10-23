package com.futo.platformplayer.backend

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class YoutubeAccountIdentityTest {
    private fun name(json: String) = youtubeActiveAccountName(JsonParser.parseString(json))

    @Test fun readsActiveNameNotAnotherAccountOrVideoAuthor() {
        assertEquals("My channel", name("""{"author":"Other creator","items":[
            {"accountItemRenderer":{"accountName":{"simpleText":"Wrong account"}}},
            {"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"My "},{"text":"channel"}]},
                "email":{"simpleText":"my@example.test"}}}]}"""))
    }
    @Test fun emailIsFallbackForActiveAccount() {
        assertEquals("me@example.test", name("""{"activeAccountHeaderRenderer":{"email":{"simpleText":"me@example.test"}}}"""))
    }
    @Test fun doesNotChooseUnselectedAccount() {
        assertNull(name("""{"accountItemRenderer":{"accountName":{"simpleText":"Someone else"}}}"""))
        assertEquals("Selected", name("""{"accountItemRenderer":{"isSelected":true,"accountName":{"simpleText":"Selected"}}}"""))
    }
    @Test fun unrelatedPageTextIsNotAnIdentity() {
        assertNull(name("""{"accountName":"Do not use","email":"do-not-use@example.test","channelMetadataRenderer":{"title":"A video channel"}}"""))
    }
    @Test fun accountListUsesOnlySelectedChannel() {
        assertEquals("Selected channel", name("""{"accountItemSectionRenderer":{"contents":[
            {"accountItem":{"isSelected":false,"accountName":{"simpleText":"Other"}}},
            {"accountItem":{"isSelected":true,"accountName":{"simpleText":"Selected channel"}}}
        ]}}"""))
    }
    @Test fun malformedFieldsAndSignedOutPagesHaveNoIdentity() {
        listOf("null", "[]", """{"activeAccountHeaderRenderer":{"accountName":false,"email":[]}}""",
            """{"accountItemRenderer":{"isSelected":{},"accountName":{"simpleText":"No"}}}""")
            .forEach { assertNull(name(it)) }
    }
    @Test fun visibleIdentityIsBoundedAndControlCharactersRemoved() {
        assertEquals("Name", name("""{"accountHeaderRenderer":{"accountName":{"simpleText":"  Na\nme  "}}}"""))
        val json = """{"activeAccountHeaderRenderer":{"accountName":{"simpleText":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}}}"""
        assertEquals(320, name(json)?.length)
    }
    @Test fun contextParserHandlesMultipleBlocksEscapesAndDelegatedSession() {
        val config = youtubeAccountConfig("""
            ytcfg.set({"SESSION_INDEX":2});
            ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"clientName":"WEB","clientVersion":"v"}},"DELEGATED_SESSION_ID":"page","test":"brace } and \"quote"});
        """)!!
        assertEquals(2, config.get("SESSION_INDEX").asInt)
        assertEquals("page", config.get("DELEGATED_SESSION_ID").asString)
        assertEquals("WEB", config.getAsJsonObject("INNERTUBE_CONTEXT").getAsJsonObject("client").get("clientName").asString)
    }
    @Test fun missingContextDoesNotInventClientVersionOrAccount() {
        assertNull(youtubeAccountConfig("<html>sign in</html>"))
        assertNull(youtubeAccountConfig("""ytcfg.set({"LOGGED_IN":false});"""))
        assertNull(youtubeAccountConfig("ytcfg.set({broken);"))
    }
}
