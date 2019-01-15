rdaTypeSlugMap = [
    'ContentType': 'content',
    'MediaType': 'media',
    'CarrierType': 'carrier'
]

brokenBase = 'http://id.kb.se/term/rda/'
correctBase = 'https://id.kb.se/term/rda/'

correctIdMap = [:]

String findCorrectId(String id) {
    if (id in correctIdMap) {
        return correctIdMap[id]
    }
    String correctId = correctIdMap[id] = findCanonicalId(id)
    return correctId
}

List asList(o) {
    return (o instanceof List) ? (List) o : o != null ? [o] : []
}

boolean fixRdaLink(ref) {
    Map brokenRef = ref.sameAs?.find { it[ID].startsWith(brokenBase) }
    String fixedId = null

    if (brokenRef) {
        def brokenId = brokenRef[ID]
        fixedId = correctBase + brokenId.substring(brokenBase.size())
    } else {
        def rdatype = rdaTypeSlugMap[ref[TYPE]]
        def tokens = asList(ref.code) ?: asList(ref.label)
        boolean otherTermList = ref.termGroup &&
                ref.termGroup.toLowerCase().indexOf('rda') == -1
        if (rdatype && !otherTermList && tokens) {
            fixedId = correctBase + rdatype + '/' + tokens[0]
        }
    }

    if (fixedId) {
        String correctId = findCorrectId(fixedId)
        if (correctId) {
            ref.clear()
            ref[ID] = correctId
        } else if (brokenRef) {
            // Not a correct term, but shape of URI is correct
            // TODO: really though? We could just remove it...
            brokenRef[ID] = fixedId
        }
        return true
    }
    return false
}

boolean fixRdaLinks(Map owner, String link, isPart = false) {
    List<Map> references = owner[link]
    boolean fixed = false
    references?.each {
        if (fixRdaLink(it)) {
            fixed = true
        }
    }
    if (fixed) {
        // Eliminate duplicate references:
        Set seenRefs = new HashSet()
        references.removeAll { !seenRefs.add(it[ID]) }

        // Move to hasPart
        def ownerParts = owner.hasPart
        if (ownerParts && !(ownerParts instanceof List)) {
            ownerParts = owner.hasPart = ownerParts ? [ownerParts] : []
        }

        ownerParts.each {
            fixRdaLinks(it, link, true)
        }

        return true
    }
    return false
}

selectBySqlWhere('''
    data#>'{@graph,1}' ??| array['carrierType', 'mediaType'] OR
    data#>'{@graph,2}' ?? 'contentType'
''') { data ->
    def (record, instance, work) = data.graph

    if (!isInstanceOf(instance, 'Instance')) {
        return
    }
    if (!work) {
        return
    }
    assert work['@id'] == instance.instanceOf['@id']

    if (fixRdaLinks(instance, 'mediaType')) {
        data.scheduleSave(loud: false)
    }
    if (fixRdaLinks(instance, 'carrierType')) {
        data.scheduleSave(loud: false)
    }
    if (fixRdaLinks(work, 'contentType')) {
        data.scheduleSave(loud: false)
    }
}
