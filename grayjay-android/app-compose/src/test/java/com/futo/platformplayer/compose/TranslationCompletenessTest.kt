package com.futo.platformplayer.compose

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslationCompletenessTest {
    @Test fun languagePickerAndAndroidLocaleConfigurationAgree() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/locales_config.xml"))
        val nodes = xml.getElementsByTagName("locale")
        val declared = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }.toSet()
        assertEquals(AppLanguageManager.supportedLanguages.map { it.tag }.toSet(), declared)
    }

    @Test fun polishPluralsHaveAllPolishFormsAndPreserveArguments() {
        val base = resources("values")
        resources("values-pl").filterKeys { it.startsWith("plurals:") }.forEach { (key, element) ->
            val nodes = element.getElementsByTagName("item")
            val forms = (0 until nodes.length).map { nodes.item(it) as Element }
            assertTrue("$key: missing Polish plural forms", forms.map { it.getAttribute("quantity") }.containsAll(listOf("one", "few", "many", "other")))
            val source = base.getValue(key).getElementsByTagName("item").item(0).textContent
            forms.forEach { form -> assertEquals(key, formats.findAll(source).map { it.value }.toList(), formats.findAll(form.textContent).map { it.value }.toList()) }
        }
    }
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
