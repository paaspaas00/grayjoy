package com.futo.platformplayer.compose

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslationCompletenessTest {
    private val formats = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[a-zA-Z]")
    private fun resources(directory: String): Map<String, Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val result = mutableMapOf<String, Element>()
        File("src/main/res/$directory").listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
            val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                if (node.tagName !in setOf("string", "plurals", "string-array") || node.getAttribute("translatable") == "false") continue
                val key = node.tagName + ":" + node.getAttribute("name")
                assertNull("Duplicate $directory/$key", result.put(key, node))
            }
        }
        return result
    }
    @Test fun everyAdvertisedLanguageContainsEveryResourceIncludingWizardAndHelp() {
        val base = resources("values")
        assertTrue(base.size > 800)
        AppLanguageManager.supportedLanguages.filter { it.tag != "en" }.forEach { locale ->
            val target = resources("values-${locale.tag}")
            assertTrue("${locale.tag}: missing ${base.keys - target.keys}", target.keys.containsAll(base.keys))
            base.forEach { (key, source) ->
                val translated = target.getValue(key)
                if (source.tagName == "string") {
                    assertEquals("${locale.tag}/$key", formats.findAll(source.textContent).map { it.value }.sorted().toList(),
                        formats.findAll(translated.textContent).map { it.value }.sorted().toList())
                    assertFalse("${locale.tag}/$key: unresolved token", translated.textContent.contains("__GJ"))
                }
            }
        }
    }
}
