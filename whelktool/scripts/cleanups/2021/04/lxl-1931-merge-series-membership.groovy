/**
 * Merge split SeriesMembership entities.
 *
 * Match on properties that correspond to marc subfields $a $v $x in 490 and 830 respectively
 * $a have to match, plus either $v or $x given that the other also match or is non-existent
 * We compare only strings
 *
 * $a is considered a match if all words match (although we allow minor differences when comparing words)
 * $v if all numeric value match
 * $x if all numeric value match
 *
 * See LXL-1931 for more info
 *
 */

import java.text.Normalizer

PrintWriter multipleMatches = getReportWriter("multiple-matches.txt")

String where = "collection = 'bib' AND data#>'{@graph,1,seriesMembership,1}' IS NOT NULL"

selectBySqlWhere(where) { data ->
    String id = data.doc.shortId

    List seriesMembership = data.graph[1]["seriesMembership"]

    // Skip if any of the concerned properties have multiple values, we want only 1-1 comparison
    if (seriesMembership.any { multipleValues(it) })
        return

    List from490 = []
    List from830 = []

    seriesMembership.each {
        it.containsKey("seriesStatement") ? from490 << it : from830 << it
    }

    if (from490.isEmpty() || from830.isEmpty())
        return


    // There can be multiple matches, keep track of these
    Map matchCount = seriesMembership.collectEntries { [it, 0] }
    List matchedPairs = []

    from490.each { Map sm490 ->
        String a490 = asList(sm490["seriesStatement"])[0]
        String v490 = asList(sm490["seriesEnumeration"])[0]
        String x490 = sm490.inSeries?.identifiedBy?.find { it["@type"] == "ISSN" }?.value

        from830.eachWithIndex { Map sm830, int idx ->
            // Always only one Title. mainTitle sometimes (rarely) a list.
            String a830 = asList(asList(sm830.inSeries?.instanceOf)[0]?.hasTitle?.find { it["@type"] == "Title" }?.mainTitle)[0]
            String v830 = asList(sm830["seriesEnumeration"])[0]
            String x830 = sm830.inSeries?.identifiedBy?.find { it["@type"] == "ISSN" }?.value

            boolean aMatched = a490 && a830 ? aMatch(a490, a830) : null
            boolean vMatched = v490 && v830 ? vMatch(v490, v830) : null
            boolean xMatched = x490 && x830 ? xMatch(x490, x830) : null

            List accepted = [[true, true, true], [true, null, true], [true, true, null]]

            if ([aMatched, vMatched, xMatched] in accepted) {
                matchedPairs << [sm490, sm830]
                matchCount[sm490] += 1
                matchCount[sm830] += 1
            }
        }
    }

    List validPairs = matchedPairs.findAll {
        matchCount[it[0]] == 1 && matchCount[it[1]] == 1
    }

    // Remove matched objects and add the merged pair
    validPairs.each {
        seriesMembership.remove(it[0])
        seriesMembership.remove(it[1])
        seriesMembership << it[0] + it[1]
    }

    // Save a report of ids with multiple matches, needs a closer look
    if (matchCount.any { it.value > 1 })
        multipleMatches.println(id)


    if (!(validPairs.isEmpty()))
        data.scheduleSave()
}

boolean multipleValues(Map sm) {
    List values =
            [
                    sm.seriesEnumeration,
                    sm.seriesStatement,
                    sm.inSeries?.instanceOf,
                    sm.inSeries?.identifiedBy,
                    asList(sm.inSeries?.instanceOf)[0]?.hasTitle?.find { it["@type"] == "Title" }?.mainTitle
            ]

    if (values.any { it instanceof List && it.size() > 1 })
        return true

    return false
}

boolean aMatch(String a490, String a830) {
    if (a490 == a830)
        return true

    List a490words = normalize(a490)
    List a830words = normalize(a830)

    if (a490words == a830words)
        return true

    boolean match = true
    int a490len = a490words.size()
    int a830len = a830words.size()
    int i = 0

    // Compare the string, word for word
    // Accept spacing between words, e.g. "super hero" = "superhero"
    // Require an exact match when comparing concatenated (e.g. "för färdiga" != "förfärliga")
    while (match && i < Math.min(a490len, a830len)) {
        if (matchWords(a490words[i], a830words[i]))
            i += 1
        else if (i + 1 < a490len && a490words[i] + a490words[i + 1] == a830words[i]) {
            a490words.remove(i + 1)
            a490len -= 1
            i += 1
        } else if (i + 1 < a830len && a490words[i] == a830words[i] + a830words[i + 1]) {
            a830words.remove(i + 1)
            a830len -= 1
            i += 1
        } else
            match = false
    }

    // All words match and the number of words are the same ("super hero" counts as one word if matched with "superhero")
    if (match && a490len == a830len)
        return true

    return false
}

boolean vMatch(String v490, String v830) {
    if (v490 == v830)
        return true

    List v490numbers = getNumbers(v490).collect { it.replaceFirst(/^0+/, "") }
    List v830numbers = getNumbers(v830).collect { it.replaceFirst(/^0+/, "") }

    if (v490numbers.isEmpty() || v830numbers.isEmpty())
        return false

    if (v490numbers == v830numbers)
        return true

    return false
}

boolean xMatch(String x490, String x830) {
    if (x490 == x830)
        return true

    List x490numbers = getNumbers(x490)
    List x830numbers = getNumbers(x830)

    if (x490numbers == x830numbers)
        return true

    return false
}

/**
 Calculate the levenshtein distance and use this value to decide whether the strings are a good enough match
 */
boolean matchWords(String w1, String w2) {
    int w1length = w1.size()
    int w2length = w2.size()

    int rows = w1length + 1
    int cols = w2length + 1

    List<List> distMatrix = []

    for (i in 0..<rows) {
        distMatrix << []
        for (j in 0..<cols)
            distMatrix[i] << 0
    }

    for (i in 0..<rows) {
        for (j in 0..<cols) {
            if (i == 0)
                distMatrix[i][j] = j
            else if (j == 0)
                distMatrix[i][j] = i
            else if (w1[i - 1] == w2[j - 1]) {
                distMatrix[i][j] = distMatrix[i - 1][j - 1]
            } else {
                distMatrix[i][j] = 1 + [distMatrix[i][j - 1],
                                        distMatrix[i - 1][j],
                                        distMatrix[i - 1][j - 1]].min()
            }
        }
    }


    int editDist = distMatrix.last().last()
    // The edit distance doesn't consider string lengths
    // E.g. "hej" and "bra" gets the same value (3) as "hej" and "hejsan"
    // So we need to normalize to get a good metric
    // The normalized value for "hej" and "bra" is 0, and for "hej" and "hejsan" it's 1/2
    int longest = Math.max(w1length, w2length)
    def normalizedDist = (longest - editDist) / longest

    // Max value is 1 (exact match) but we consider 3/4 good enough
    if (normalizedDist >= 3 / 4)
        return true

    return false
}


List normalize(String s) {
    String lowerCase = s.toLowerCase()
    String stripped = asciiFold(lowerCase)
    List words = getWords(stripped)
    if (words[0] in ["the", "il", "el", "le", "la", "die", "das"])
        words.remove(0)
    return words
}

String asciiFold(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll('\\p{M}', '')
}

List getWords(String str) {
    List words = str.split(/(:|\/|,|-| (& |and )?|\.|;|\[|\]|\(.*\))+/)
    if (words[0] == "")
        words.remove(0)
    return words
}

List getNumbers(String str) {
    List numbers = str.split(/\D+/)
    if (numbers[0] == "")
        numbers.remove(0)
    return numbers
}

List asList(Object o) {
    return o instanceof List ? o : [o]
}
