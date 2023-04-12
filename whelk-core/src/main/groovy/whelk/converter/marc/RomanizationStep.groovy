package whelk.converter.marc

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor
import groovy.transform.NullCheck
import groovy.util.logging.Log4j2 as Log
import whelk.filter.LanguageLinker
import whelk.util.DocumentUtil

import static whelk.Document.deepCopy
import static whelk.JsonLd.asList

@Log
class RomanizationStep extends MarcFramePostProcStepBase {
    private static final String TARGET_SCRIPT = 'Latn'
    private static final String MATCH_T_TAG = "-${TARGET_SCRIPT}-t-"

    @CompileStatic
    @NullCheck(includeGenerated = true)
    static class LanguageResources {
        LanguageLinker languageLinker
        Map languages
        Map transformedLanguageForms
    }

    boolean requiresResources = true
    MarcFrameConverter converter
    LanguageResources languageResources

    Map langAliases
    Map byLangToBase

    Map langIdToLangTag

    // Note: MARC standard allows ISO 15924 in $6 but Libris practice doesn't
    private static final Map MARC_SCRIPT_CODES =
            [
                    'Arab': '/(3/r',
                    'Cyrl': '/(N',
                    'Cyrs': '/(N',
                    'Grek': '/(S',
                    'Hang': '/$1',
                    'Hani': '/$1',
                    'Hans': '/$1',
                    'Hant': '/$1',
                    'Hebr': '/(2/r'
            ]

    String OG_MARK = '**OG**'

    String HAS_BIB880 = 'marc:hasBib880'
    String BIB880 = 'marc:bib880'
    String PART_LIST = 'marc:partList'
    String FIELDREF = 'marc:fieldref'
    String SUBFIELDS = 'subfields'
    String IND1 = 'ind1'
    String IND2 = 'ind2'
    String BIB250_REF = 'marc:bib250-fieldref'

    List FIELD_REFS = [FIELDREF, BIB250_REF]

    void modify(Map record, Map thing) {
        try {
            _modify(record, thing)
        } catch (Exception e) {
            log.error("Failed to convert 880: $e", e)
        }
    }

    void _modify(Map record, Map thing) {
        if (!languageResources)
            return

        // TODO: Do we really want to remove everything? What about "00" fields?
        // https://katalogverk.kb.se/katalogisering/Formathandboken/Lankning/index.html
        def hasBib880 = thing.remove(HAS_BIB880)

        if (!hasBib880) {
            return
        }

        Map bib880ByField = [:]

        asList(hasBib880).each { bib880 ->
            def linkField = bib880[PART_LIST][0][FIELDREF].split('-')
            def tag = linkField[0]
            def seqNum = linkField[1].take(2)
            if (seqNum != '00') {
                def entry = bib880ByField.computeIfAbsent(tag, s -> [])
                entry.add(['ref': '880-' + seqNum, 'bib880': bib880])
            }
        }

        Map bib880Map = [:]
        Map sameField = [:]

        bib880ByField.each { tag, data ->
            def marcJson = null
            try {
                marcJson = data.collect { bib880ToMarcJson(it.bib880) }
            } catch (Exception e) {
                return
            }

            def marc = [leader: "00887cam a2200277 a 4500", fields: marcJson]
            def converted = converter.runConvert(marc)

            def refs = data.collect {
                bib880Map[it.ref] = converted
                it.ref
            } as Set

            refs.each {
                sameField[it] = refs
            }
        }

        Set handled = []
        def handle880Ref = { ref, path ->
            def converted = bib880Map[ref]
            def mainEntity = DocumentUtil.getAtPath(converted, ['@graph', 1])
            if (sameField[ref]?.intersect(handled) || (mainEntity && mergeAltLanguage(mainEntity, thing))) {
                handled.add(ref)
                return new DocumentUtil.Remove()
            }
        }

        if (bib880Map) {
            FIELD_REFS.each {
                DocumentUtil.findKey(thing, it, handle880Ref)
            }
        }
    }

    void unmodify(Map record, Map thing) {
        try {
            _unmodify(record, thing)
        } catch (Exception e) {
            log.error("Failed to convert 880: $e", e)
        }
    }

    void _unmodify(Map record, Map thing) {
        def byLangPaths = findByLangPaths(thing)
        def uniqueTLangs = findUniqueTLangs(thing, byLangPaths)

        unmodifyTLangs(thing, uniqueTLangs, byLangPaths, record)

        byLangPaths.each { putRomanizedLiteralInNonByLang(thing, it as List) }
    }

    private def unmodifyTLangs(def thing, def tLangs, def byLangPaths, def record) {
        def bib880ToRef = [:]

        tLangs.each { tLang ->
            def copy = deepCopy(record)
            def thingCopy = copy.mainEntity

            byLangPaths.each { putOriginalLiteralInNonByLang(thingCopy, it as List, tLang) }

            prepareForRevert(thingCopy)

            def reverted = converter.runRevert(copy)
            def romanizedFieldsByTmpRef = findRomanizedFields(reverted)

            DocumentUtil.findKey(thingCopy, FIELD_REFS) { value, path ->
                def romanizedField = romanizedFieldsByTmpRef[value]
                if (romanizedField) {
                    def fieldNumber = romanizedField.keySet()[0]
                    def field = romanizedField[fieldNumber]

                    def ref = new Ref(
                            toField: fieldNumber,
                            path: path.dropRight(1),
                            scriptCode: marcScript(tLang)
                    )

                    def bib880 =
                            [
                                    (TYPE)          : 'marc:Bib880',
                                    (PART_LIST)     : [[(FIELDREF): fieldNumber]] + field[SUBFIELDS],
                                    (BIB880 + '-i1'): field[IND1],
                                    (BIB880 + '-i2'): field[IND2]
                            ]

                    bib880ToRef[bib880] = ref
                }
                return new DocumentUtil.Remove()
            }
        }

        if (bib880ToRef) {
            def sorted = bib880ToRef.sort {it.key[PART_LIST][0][FIELDREF] }
            sorted.eachWithIndex { bib880, ref, i ->
                bib880[PART_LIST][0][FIELDREF] = ref.from880(i + 1)
                def t = DocumentUtil.getAtPath(thing, ref.path)
                t[ref.propertyName()] = (asList(t[ref.propertyName()]) << ref.to880(i + 1)).unique()
            }

            thing[HAS_BIB880] = sorted.collect { it.key }
        }
    }

    private String marcScript(String tLang) {
        MARC_SCRIPT_CODES.findResult { tLang.contains(it.key) ? it.value : null } ?: ''
    }

    private static String stripMark(String s, String mark) {
        // Multiple properties can become one MARC subfield. So marks can also occur inside strings.
        s.startsWith(mark)
                ? s.replace(mark, '')
                : s
    }

    @MapConstructor
    private class Ref {
        String toField
        String scriptCode
        List path

        String from880(int occurenceNumber) {
            "$toField-${String.format("%02d", occurenceNumber)}${scriptCode ?: ''}"
        }

        String to880(int occurenceNumber) {
            "880-${String.format("%02d", occurenceNumber)}"
        }

        String propertyName() {
            return toField == '250' ? BIB250_REF : FIELDREF
        }
    }

    boolean mergeAltLanguage(Map converted, Map thing) {
        // Since the 880s do not specify which language they are in, we assume that they are in the first work language
        def workLang = thing.instanceOf.subMap('language')
        languageResources.languageLinker.linkAll(workLang)
        def lang = asList(workLang.language).findResult { it[ID] } ?: 'https://id.kb.se/language/und'

        return addAltLang(thing, converted, lang)
    }

    boolean addAltLang(Map thing, Map converted, String lang) {
        if (!langIdToLangTag[lang]) {
            return false
        }
        def nonByLangPaths = []
        DocumentUtil.findKey(converted, langAliases.keySet()) { value, path ->
            nonByLangPaths.add(path.collect())
            return
        }
        nonByLangPaths.each { path ->
            def containingObject = DocumentUtil.getAtPath(thing, path.dropRight(1))
            if (!containingObject) {
                //TODO: No romanized version seems to exists...
                return
            }

            asList(containingObject).each {
                def k = path.last()
                if (it[k]) {
                    def byLangProp = langAliases[k]
                    def tag = langIdToLangTag[lang]
                    it[byLangProp] =
                            [
                                    (tag)               : DocumentUtil.getAtPath(converted, path),
                                    "${tag}-Latn-t-$tag": it[k]
                            ]
                }
                it.remove(k)
            }
        }

        return true
    }

    Map findRomanizedFields(Map reverted) {
        Map byTmpRef = [:]

        reverted.fields.each {
            def fieldNumber = it.keySet()[0]
            def field = it[fieldNumber]
            if (field instanceof Map) {
                def sf6 = field[SUBFIELDS].find { it.containsKey('6') }
                if (sf6 && field[SUBFIELDS].any { Map sf -> sf.values().any { it.startsWith(OG_MARK) } }) {
                    field[SUBFIELDS] = (field[SUBFIELDS] - sf6).collect {
                        def subfield = it.keySet()[0]
                        [(BIB880 + '-' + subfield): stripMark(it[subfield], OG_MARK)]
                    }
                    def tmpRef = sf6['6'].replaceAll(/[^0-9]/, "")
                    byTmpRef[tmpRef] = it
                }
            }
        }

        return byTmpRef
    }

    def putLiteralInNonByLang(Map thing, List byLangPath, Closure handler) {
        def key = byLangPath.last()
        def path = byLangPath.dropRight(1)
        Map parent = DocumentUtil.getAtPath(thing, path)

        def base = byLangToBase[key]
        if (base && parent[key] && !parent[base]) {
            handler(parent, key, base)
        }
        parent.remove(key)
    }

    def putRomanizedLiteralInNonByLang(Map thing, List byLangPath) {
        putLiteralInNonByLang(thing, byLangPath) { Map parent, String key, String base ->
            def langContainer = parent[key] as Map
            if (langContainer.size() == 1) {
                parent[base] = langContainer.values().first()
            } else {
                pickRomanization(langContainer).values()
                        .with(RomanizationStep::unpackSingle)
                        ?.with { parent[base] = it }
            }
        }
    }

    static Map pickRomanization(Map langContainer) {
        // For now we just take the first tag in alphabetical order
        // works for picking e.g. yi-Latn-t-yi-Hebr-m0-alaloc over yi-Latn-t-yi-Hebr-x0-yivo
        langContainer.findAll { String langTag, literal -> langTag.contains(MATCH_T_TAG) }.sort()?.take(1) ?: [:]
    }

    static def unpackSingle(Collection l) {
        return l.size() == 1 ? l[0] : l
    }

    def prepareForRevert(Map thing) {
        def tmpRef = 1
        DocumentUtil.traverse(thing) { value, path ->
            // marc:nonfilingChars is only valid for romanized string
            if (path && path.last() == 'marc:nonfilingChars') {
                return new DocumentUtil.Remove()
            }
            if (value instanceof Map) {
                value[FIELDREF] = tmpRef.toString()
                tmpRef += 1
            }
            return DocumentUtil.NOP
        }
        if (thing['editionStatement']) {
            thing[BIB250_REF] = tmpRef.toString()
        }
    }

    def putOriginalLiteralInNonByLang(Map thing, List byLangPath, String tLang) {
        putLiteralInNonByLang(thing, byLangPath) { Map parent, String key, String base ->
            def romanized = parent[key].find { langTag, literal -> langTag == tLang }
            def original = parent[key].find { langTag, literal -> tLang.contains("${MATCH_T_TAG}${langTag}") }?.value
            if (romanized && original) {
                parent[base] = original instanceof List
                        ? original.collect { OG_MARK + it }
                        : OG_MARK + original
            }
        }
    }

    def findByLangPaths(Map thing) {
        List paths = []

        DocumentUtil.findKey(thing, byLangToBase.keySet()) { value, path ->
            paths.add(path.collect())
            return
        }

        paths = paths.findAll { DocumentUtil.getAtPath(thing, it).keySet().any { it =~ '-t-' } }

        return paths
    }

    Set<String> findUniqueTLangs(Map thing, List<List> byLangPaths) {
        Set tLangs = []

        byLangPaths.each {
            Map<String, ?> langContainer = DocumentUtil.getAtPath(thing, it)
            pickRomanization(langContainer).with { tLangs.addAll(it.keySet()) }
        }

        return tLangs
    }

    Map bib880ToMarcJson(Map bib880) {
        def parts = bib880[PART_LIST]
        def tag = parts[0][FIELDREF].split('-')[0]
        return [(tag): [
                (IND1)     : bib880["$BIB880-i1"],
                (IND2)     : bib880["$BIB880-i2"],
                (SUBFIELDS): parts[1..-1].collect {
                    def subfields = it.collect { key, value ->
                        [(key.replace('marc:bib880-', '')): value]
                    }
                    return subfields.size() == 1 ? subfields[0] : subfields
                }
        ]]
    }

    void init() {
        if (ld) {
            this.langAliases = ld.langContainerAlias
            this.byLangToBase = langAliases.collectEntries { k, v -> [v, k] }
        }

        if (!languageResources) {
            return
        }
        this.langIdToLangTag = languageResources.languages
                .findAll { k, v -> v.langTag }.collectEntries { k, v -> [k, v.langTag] }
    }
}
