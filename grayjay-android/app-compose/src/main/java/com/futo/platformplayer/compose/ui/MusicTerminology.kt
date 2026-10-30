package com.futo.platformplayer.compose.ui

import android.content.res.Resources
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Translate app-owned templates before interpolation. Content titles, names and URLs passed
 * as arguments are never rewritten. The bounded cache contains templates, not user content. */
internal object MusicTerminology {
    private data class Key(val language: String, val resource: String, val template: String, val plural: Boolean, val quantity: Int?, val numbers: List<Number>)
    private val cache = object : LinkedHashMap<Key, String>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, String>?) = size > 512
    }
    private val video = Regex("\\bvideo\\b", RegexOption.IGNORE_CASE)
    private val creator = Regex("\\bcreators?\\b", RegexOption.IGNORE_CASE)
    private val countPrefix = Regex("%(?:(\\d+)\\$)?d\\s*$")
    private val italianPluralBefore = Regex("(?:^|\\b)(?:i|dei|ai|dai|nei|sui|gli|questi|quei|alcuni|tutti|ultimi|nuovi|altri)\\s+$", RegexOption.IGNORE_CASE)
    private val italianSingularBefore = Regex("(?:^|\\b)(?:il|del|al|dal|nel|sul|un|nessun|alcun|ogni|questo|quel|quale|ciascun)\\s+$", RegexOption.IGNORE_CASE)
    private val italianPluralAfter = Regex("^\\s+(?:selezionati|scaricati|caricati|guardati|piaciuti|salvati|disponibili|correlati|riprodotti|seguiti|preferiti|indipendenti|non musicali)\\b", RegexOption.IGNORE_CASE)
    private val dictionaries = mapOf(
        "en" to listOf("videos" to "tracks", "video" to "track", "channels" to "artists", "channel" to "artist", "creators" to "artists", "creator" to "artist"),
        "de" to listOf("Videos" to "Musikstücke", "Video" to "Musikstück", "Kanälen" to "Künstlern", "Kanäle" to "Künstler", "Kanals" to "Künstlers", "Kanal" to "Künstler", "Erstellern" to "Künstlern", "Erstellers" to "Künstlers", "Ersteller" to "Künstler", "Creators" to "Künstler", "Creator" to "Künstler"),
        "es" to listOf("vídeos" to "temas", "videos" to "temas", "vídeo" to "tema", "video" to "tema", "canales" to "artistas", "canal" to "artista", "creadores" to "artistas", "creador" to "artista"),
        "fr" to listOf("vidéos" to "pistes", "vidéo" to "piste", "chaînes" to "artistes", "chaîne" to "artiste", "créateurs" to "artistes", "créateur" to "artiste", "créatrices" to "artistes", "créatrice" to "artiste"),
        "pt" to listOf("vídeos" to "temas", "videos" to "temas", "vídeo" to "tema", "video" to "tema", "canais" to "artistas", "canal" to "artista", "criadores" to "artistas", "criador" to "artista"),
        "pl" to listOf("filmami" to "utworami", "filmach" to "utworach", "filmów" to "utworów", "filmom" to "utworom", "filmie" to "utworze", "filmu" to "utworu", "filmem" to "utworem", "filmy" to "utwory", "film" to "utwór", "kanałami" to "artystami", "kanałach" to "artystach", "kanałów" to "artystów", "kanałom" to "artystom", "kanału" to "artysty", "kanałem" to "artystą", "kanale" to "artyście", "kanały" to "artyści", "kanał" to "artysta", "twórców" to "artystów", "twórcy" to "artyści", "twórca" to "artysta", "twórcę" to "artystę"),
        "tr" to listOf("videoların" to "parçaların", "videoları" to "parçaları", "videolar" to "parçalar", "videonun" to "parçanın", "videoyu" to "parçayı", "videoya" to "parçaya", "videoda" to "parçada", "videodan" to "parçadan", "video" to "parça", "kanalların" to "sanatçıların", "kanalları" to "sanatçıları", "kanallar" to "sanatçılar", "kanalın" to "sanatçının", "kanalı" to "sanatçıyı", "kanala" to "sanatçıya", "kanaldan" to "sanatçıdan", "kanal" to "sanatçı", "içerik üreticileri" to "sanatçılar", "içerik üreticisi" to "sanatçı"),
        "ru" to listOf("каналами" to "исполнителями", "каналах" to "исполнителях", "каналов" to "исполнителей", "каналам" to "исполнителям", "канала" to "исполнителя", "каналу" to "исполнителю", "каналом" to "исполнителем", "канале" to "исполнителе", "каналы" to "исполнители", "канал" to "исполнитель", "авторов" to "исполнителей", "автора" to "исполнителя", "авторы" to "исполнители", "автор" to "исполнитель"),
    )
    private val phrasePatterns = ConcurrentHashMap<String, Regex>()
    private val compiledDictionaries = ConcurrentHashMap<String, List<Pair<Regex, String>>>()
    private val candidatePatterns = ConcurrentHashMap<String, Regex>()
    private fun dictionary(language: String) = compiledDictionaries.getOrPut(language) {
        (dictionaries[language] ?: dictionaries.getValue("en")).map { (from, to) ->
            Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(from)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE) to to
        }
    }
    private fun hasTerms(text: String, language: String) = candidatePatterns.getOrPut(language) {
        Regex(when (language) {
            "it" -> "\\b(?:video|canal[ei]|creators?)\\b"
            "de" -> "video|kanal|kanäl|creator|ersteller"
            "es" -> "v[ií]deo|canal|creador"
            "pt" -> "v[ií]deo|canal|canais|criador"
            "fr" -> "vidéo|chaîne|créate"
            "pl" -> "film|wideo|kanał|twórc"
            "ru" -> "видео|канал|автор"
            "tr" -> "video|kanal|üretici"
            "ar" -> "فيديو|قنا|منشئ|صانع|صناع"
            "ja" -> "動画|ビデオ|チャンネル|クリエイター"
            "ko" -> "영상|비디오|채널|크리에이터"
            "zh" -> "视频|視頻|影片|频道|頻道|创作者|創作者"
            else -> "\\b(?:videos?|channels?|creators?)\\b"
        }, RegexOption.IGNORE_CASE)
    }.containsMatchIn(text)
    private fun casing(original: String, replacement: String, locale: Locale): String = when {
        original.any(Char::isLetter) && original.filter(Char::isLetter).all(Char::isUpperCase) -> replacement.uppercase(locale)
        original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.titlecase(locale) }
        else -> replacement
    }
    private fun replace(text: String, from: String, to: String, locale: Locale): String =
        phrasePatterns.getOrPut(from) { Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(from)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE) }
            .replace(text) { casing(it.value, to, locale) }

    fun template(text: String, locale: Locale, resource: String, plural: Boolean = false, quantity: Int? = null,
        args: Array<out Any> = emptyArray()): String {
        if (!hasTerms(text, locale.language)) return text
        val key = Key(locale.language, resource, text, plural, quantity, args.filterIsInstance<Number>())
        synchronized(cache) { cache[key]?.let { return it } }
        var result = text
        val pluralHint = plural || resource in MusicPluralTemplates.names
        when (locale.language) {
            "it" -> {
                result = replace(result, "titoli video", "titoli dei brani", locale)
                result = replace(result, "qualità video", "qualità di riproduzione", locale)
                result = replace(result, "riproduzione video", "riproduzione dei brani", locale)
                for ((from, to) in listOf("ai canali" to "agli artisti", "dei canali" to "degli artisti", "dai canali" to "dagli artisti", "nei canali" to "negli artisti", "sui canali" to "sugli artisti", "i canali" to "gli artisti", "il canale" to "l’artista", "del canale" to "dell’artista", "al canale" to "all’artista", "dal canale" to "dall’artista", "nel canale" to "nell’artista", "sul canale" to "sull’artista", "i creator" to "gli artisti", "dei creator" to "degli artisti", "il creator" to "l’artista", "del creator" to "dell’artista")) result = replace(result, from, to, locale)
                result = replace(result, "canali", "artisti", locale)
                result = replace(result, "canale", "artista", locale)
                val creatorTemplate = result
                result = creator.replace(creatorTemplate) { match ->
                    val before = creatorTemplate.substring(0, match.range.first)
                    val after = creatorTemplate.substring(match.range.last + 1)
                    val number = countPrefix.find(before)?.let { found -> args.getOrNull((found.groupValues[1].toIntOrNull() ?: 1) - 1) as? Number }?.toInt() ?: quantity
                    val many = when {
                        number != null -> number != 1
                        italianSingularBefore.containsMatchIn(before) -> false
                        italianPluralBefore.containsMatchIn(before) || italianPluralAfter.containsMatchIn(after) -> true
                        else -> pluralHint
                    }
                    casing(match.value, if (many) "artisti" else "artista", locale)
                }
                val original = result
                result = video.replace(original) { match ->
                    val before = original.substring(0, match.range.first)
                    val after = original.substring(match.range.last + 1)
                    val count = countPrefix.find(before)?.let { found ->
                        args.getOrNull((found.groupValues[1].toIntOrNull() ?: 1) - 1) as? Number
                    }?.toInt() ?: quantity
                    val many = when {
                        count != null -> count != 1
                        italianSingularBefore.containsMatchIn(before) -> false
                        italianPluralBefore.containsMatchIn(before) || italianPluralAfter.containsMatchIn(after) -> true
                        else -> pluralHint
                    }
                    casing(match.value, if (many) "brani" else "brano", locale)
                }
                result = replace(result, "brano o canzone", "brano", locale)
                result = replace(result, "brano o brano", "brano", locale)
                result = replace(result, "brani e brani", "brani", locale)
                result = replace(result, "artista o artista", "artista", locale)
                result = replace(result, "brani, musica e", "musica e", locale)
            }
            "ja" -> for ((from, to) in listOf("クリエイター" to "アーティスト", "チャンネル" to "アーティスト", "ビデオ" to "曲", "動画" to "曲")) result = result.replace(from, to)
            "zh" -> for ((from, to) in listOf("创作者" to "艺人", "創作者" to "藝人", "频道" to "艺人", "頻道" to "藝人", "视频" to "曲目", "影片" to "曲目", "視頻" to "曲目")) result = result.replace(from, to)
            "ko" -> {
                for ((from, to) in listOf("채널들을" to "아티스트들을", "채널들이" to "아티스트들이", "채널들" to "아티스트들", "채널을" to "아티스트를", "채널은" to "아티스트는", "채널이" to "아티스트가", "채널과" to "아티스트와", "채널" to "아티스트", "크리에이터" to "아티스트", "비디오를" to "트랙을", "비디오가" to "트랙이", "비디오는" to "트랙은", "동영상" to "트랙", "비디오" to "트랙", "영상" to "트랙")) result = result.replace(from, to)
            }
            "ar" -> {
                for ((from, to) in listOf("القنوات" to "الفنانين", "القناة" to "الفنان", "قنوات" to "فنانين", "قناة" to "فنان", "منشئي المحتوى" to "الفنانين", "منشئو المحتوى" to "الفنانون", "منشئ المحتوى" to "الفنان", "المنشئ" to "الفنان", "منشئ" to "فنان", "صناع المحتوى" to "الفنانين", "صانع المحتوى" to "الفنان", "صانع" to "فنان", "الفيديوهات" to "المقاطع", "فيديوهات" to "مقاطع", "الفيديو" to "المقطع", "فيديو" to if (quantity != null && quantity != 1 && quantity in 3..10) "مقاطع" else "مقطع")) result = result.replace(from, to)
                result = result.replace("هذه الفنان", "هذا الفنان").replace("الفنان التي", "الفنان الذي").replace("فنان واحدة", "فنان واحد")
            }
            else -> {
                if (locale.language == "de") for ((from, to) in listOf("Videoqualität" to "Wiedergabequalität", "Videotitel" to "Musiktitel", "Videowiedergabe" to "Musikwiedergabe", "Kanaleinstellungen" to "Künstlereinstellungen")) result = replace(result, from, to, locale)
                for ((pattern, to) in dictionary(locale.language)) result = pattern.replace(result) { casing(it.value, to, locale) }
                if (locale.language == "ru") {
                    result = replace(result, "видео", when {
                        quantity != null && quantity % 100 !in 11..14 && quantity % 10 == 1 -> "трек"
                        quantity != null && quantity % 100 !in 11..14 && quantity % 10 in 2..4 -> "трека"
                        quantity != null -> "треков"
                        pluralHint -> "треки"
                        else -> "трек"
                    }, locale)
                    for ((from, to) in listOf("это трек" to "этот трек", "следующее трек" to "следующий трек", "для трек" to "для трека", "этого трек" to "этого трека", "о трек" to "о треке", "на трек" to "на треке", "из трек" to "из трека", "выберите исполнитель" to "выберите исполнителя", "открыть исполнитель" to "открыть исполнителя")) result = replace(result, from, to, locale)
                }
                if (locale.language == "fr") {
                    for (article in listOf("le", "la")) result = replace(result, "$article artiste", "l’artiste", locale)
                    for ((from, to) in listOf("cette artiste" to "cet artiste", "une artiste" to "un artiste", "toutes les artistes" to "tous les artistes", "artiste suivie" to "artiste suivi", "artistes suivies" to "artistes suivis", "artiste sélectionnée" to "artiste sélectionné", "artistes sélectionnées" to "artistes sélectionnés", "artiste inconnue" to "artiste inconnu")) result = replace(result, from, to, locale)
                }
                if (locale.language == "pl") {
                    result = replace(result, "wideo", when {
                        quantity == 1 -> "utwór"
                        quantity != null && quantity % 100 !in 12..14 && quantity % 10 in 2..4 -> "utwory"
                        quantity != null -> "utworów"
                        pluralHint -> "utwory"
                        else -> "utwór"
                    }, locale)
                    for (verb in listOf("otwórz", "wybierz", "udostępnij", "obserwuj")) result = replace(result, "$verb artysta", "$verb artystę", locale)
                    result = replace(result, "obserwowane artyści", "obserwowani artyści", locale)
                }
                if (locale.language == "en" || locale.language !in dictionaries.keys) {
                    result = replace(result, "a artist", "an artist", locale)
                    result = replace(result, "track or song", "track", locale)
                    result = replace(result, "tracks and songs", "tracks", locale)
                    result = replace(result, "artist or artist", "artist", locale)
                }
            }
        }
        synchronized(cache) { cache[key] = result }
        return result
    }
}

internal fun musicProfileString(resources: Resources, id: Int, music: Boolean, vararg args: Any): String {
    if (!music) return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    val mapped = com.futo.platformplayer.compose.ui.screens.musicTerminologyResource(id, true)
    val template = MusicTerminology.template(resources.getString(mapped), resources.configuration.locales[0], resources.getResourceEntryName(mapped), args = args)
    return if (args.isEmpty()) template else String.format(resources.configuration.locales[0], template, *args)
}

internal fun musicProfileQuantity(resources: Resources, id: Int, quantity: Int, music: Boolean, vararg args: Any): String {
    if (!music) return if (args.isEmpty()) resources.getQuantityString(id, quantity) else resources.getQuantityString(id, quantity, *args)
    val template = MusicTerminology.template(resources.getQuantityString(id, quantity), resources.configuration.locales[0], resources.getResourceEntryName(id), quantity = quantity, args = args)
    return if (args.isEmpty()) template else String.format(resources.configuration.locales[0], template, *args)
}
