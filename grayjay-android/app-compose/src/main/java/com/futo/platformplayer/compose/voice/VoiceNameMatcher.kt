package com.futo.platformplayer.compose.voice

import java.text.Normalizer
import java.util.Locale
import org.apache.commons.codec.language.DoubleMetaphone

internal data class VoiceNameCandidate(val id: String, val title: String)
internal data class VoiceNameMatch(val candidate: VoiceNameCandidate, val score: Double,
    val exact: Boolean, val phoneticExact: Boolean)

/** Bounded, dictionary-free comparison: English phonetic codes plus Italian/Latin sound rules. */
internal object VoiceNameMatcher {
    private val metaphone = DoubleMetaphone().apply { maxCodeLen = 48 }
    private val numbers = listOf(
        "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty",
        "zero uno due tre quattro cinque sei sette otto nove dieci undici dodici tredici quattordici quindici sedici diciassette diciotto diciannove venti",
    ).flatMap { line -> line.split(' ').mapIndexed { index, word -> word to index.toString() } }.toMap()
    private val numberPattern = Regex("\\b(?:${numbers.keys.joinToString("|")})\\b")
    fun normalized(value: String): String {
        val plain = Normalizer.normalize(value.take(240).lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("(?<=\\p{L})['’](?=\\p{L})"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
        return numberPattern.replace(plain) { numbers.getValue(it.value) }
    }

    fun rank(query: String, candidates: List<VoiceNameCandidate>, titleWindows: Boolean = false): List<VoiceNameMatch> {
        val heard = normalized(query)
        if (heard.isBlank()) return emptyList()
        return candidates.map { candidate -> score(heard, candidate, titleWindows) }
            .filter { it.score > 0.0 }.sortedByDescending { it.score }
    }
    fun automatic(matches: List<VoiceNameMatch>): VoiceNameCandidate? {
        val best = matches.firstOrNull() ?: return null
        val margin = best.score - (matches.getOrNull(1)?.score ?: 0.0)
        if (best.exact && margin > .001) return best.candidate
        return best.candidate.takeIf { margin >= .10 && (best.score >= .92 || best.phoneticExact && best.score >= .80) }
    }
    private fun score(heard: String, candidate: VoiceNameCandidate, titleWindows: Boolean): VoiceNameMatch {
        val label = normalized(candidate.title)
        if (label.isBlank()) return VoiceNameMatch(candidate, 0.0, false, false)
        // Do not play season 18 when the requested name says season 17.
        val heardNumbers = Regex("\\d+").findAll(heard).map { it.value }.toList()
        val labelNumbers = Regex("\\d+").findAll(label).map { it.value }.toList()
        if (heardNumbers.isNotEmpty() && labelNumbers.isNotEmpty() && heardNumbers != labelNumbers)
            return VoiceNameMatch(candidate, 0.0, false, false)
        if (heard == label) return VoiceNameMatch(candidate, 1.0, true, true)
        val surfaces = mutableListOf(label)
        val possessiveStem = normalized(candidate.title.replace(Regex("(?i)(?<=\\p{L})['’]s\\b"), ""))
        if (possessiveStem != label) surfaces.add(possessiveStem)
        val leet = label.mapIndexed { index, char ->
            if (index > 0 && index < label.lastIndex && label[index - 1].isLetter() && label[index + 1].isLetter())
                when (char) { '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '7' -> 't'; else -> char }
            else char
        }.joinToString("")
        if (leet != label) surfaces.add(leet)
        val tokens = label.split(' ')
        val count = heard.split(' ').size.coerceAtMost(16)
        if (titleWindows && tokens.size > count) {
            for (size in maxOf(1, count - 1)..minOf(tokens.size, count + 1))
                for (start in 0..tokens.size - size) surfaces.add(tokens.subList(start, start + size).joinToString(" "))
        } else if (" $label ".contains(" $heard ")) surfaces.add(heard)
        var best = 0.0
        var phoneticExact = false
        for (surface in surfaces.distinct()) {
            val lexical = similarity(heard, surface, false)
            val left = sounds(heard); val right = sounds(surface)
            val soundScore = left.maxOf { a -> right.maxOf { b -> similarity(a, b, true) } }
            val leftCodes = codes(heard); val rightCodes = codes(surface)
            val phoneticCode = leftCodes.maxOf { a -> rightCodes.maxOf { b -> similarity(a, b, false) } }
            val exactSound = left.any { it.isNotBlank() && it in right } ||
                lexical >= .60 && leftCodes.any { it.length >= 3 && it in rightCodes }
            val value = (.78 * (.65 * soundScore + .35 * phoneticCode) + .22 * lexical) -
                (if (surface != label) .035 else 0.0)
            if (value > best) { best = value; phoneticExact = exactSound }
        }
        return VoiceNameMatch(candidate, best.coerceIn(0.0, 1.0), false, phoneticExact)
    }
    private fun codes(value: String): List<String> = listOf(
        metaphone.doubleMetaphone(value), metaphone.doubleMetaphone(value, true)
    ).filter(String::isNotBlank).ifEmpty { listOf(value) }

    private fun sounds(value: String): List<String> {
        val base = value.replace(" ", "").replace("ph", "f").replace("th", "t")
            .replace("oo", "u").replace("ou", "u").replace("ee", "i").replace("ea", "i")
            .replace("ay", "ei").replace("ey", "ei").replace("y", "i")
            .replace("sch", "sk").replace("sh", "x").replace(Regex("sc(?=[ie])"), "x")
            .replace("gn", "n").replace("gli", "li").replace("qu", "k")
            .replace("ch", "k").replace("gh", "g")
            .replace(Regex("c(?=[ie])"), "j").replace("c", "k")
            .replace(Regex("g(?=[ie])"), "j").replace("h", "")
            .replace("z", "s").replace(Regex("(.)\\1+"), "$1").take(128)
        return listOf(base.replace("w", "u"), base.replace("w", "v")).distinct()
    }
    private fun similarity(first: String, second: String, phonetic: Boolean): Double {
        val a = first.take(128); val b = second.take(128)
        if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
        fun weight(char: Char) = if (phonetic && char in "aeiou") .55 else 1.0
        fun substitution(x: Char, y: Char): Double = when {
            x == y -> 0.0
            phonetic && x in "aeiou" && y in "aeiou" -> .35
            phonetic && when (x) {
                'f' -> y == 'v'; 'v' -> y == 'f'; 'p' -> y == 'b'; 'b' -> y == 'p'
                't' -> y == 'd'; 'd' -> y == 't'; 'k' -> y == 'g'; 'g' -> y == 'k'
                's' -> y == 'z'; 'z' -> y == 's'; else -> false
            } -> .35
            else -> 1.0
        }
        var previousPrevious = DoubleArray(b.length + 1)
        var previous = DoubleArray(b.length + 1)
        var current = DoubleArray(b.length + 1)
        for (j in 1..b.length) previous[j] = previous[j - 1] + weight(b[j - 1])
        for (i in 1..a.length) {
            current[0] = previous[0] + weight(a[i - 1])
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + weight(a[i - 1]), current[j - 1] + weight(b[j - 1]),
                    previous[j - 1] + substitution(a[i - 1], b[j - 1]))
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
                    current[j] = minOf(current[j], previousPrevious[j - 2] + .65)
            }
            val recycled = previousPrevious; previousPrevious = previous; previous = current; current = recycled
        }
        return (1 - previous.last() / maxOf(a.sumOf(::weight), b.sumOf(::weight))).coerceIn(0.0, 1.0)
    }
}
