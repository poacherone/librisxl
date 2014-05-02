package se.kb.libris.whelks.component

import groovy.util.logging.Slf4j as Log
import org.codehaus.jackson.map.ObjectMapper
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.*
import org.elasticsearch.common.transport.*
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.xcontent.ToXContent
import org.elasticsearch.node.NodeBuilder
import org.elasticsearch.common.settings.*
import org.elasticsearch.common.settings.*
import org.elasticsearch.search.highlight.*
import org.elasticsearch.action.admin.indices.flush.*
import org.elasticsearch.action.count.CountResponse
import org.elasticsearch.search.facet.FacetBuilders
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.*
import org.elasticsearch.search.sort.FieldSortBuilder
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.index.query.FilterBuilders.*
import org.elasticsearch.action.delete.*
import org.elasticsearch.action.get.*
import org.elasticsearch.action.search.*

import static org.elasticsearch.index.query.QueryBuilders.*
import static org.elasticsearch.node.NodeBuilder.*
import static org.elasticsearch.common.xcontent.XContentFactory.*

import se.kb.libris.whelks.*
import se.kb.libris.whelks.basic.*
import se.kb.libris.whelks.plugin.*
import se.kb.libris.whelks.result.*
import se.kb.libris.whelks.exception.*

import static se.kb.libris.conch.Tools.*

class ElasticSearchClientIndex extends ElasticSearchClient implements Index { }

@Log
class ElasticSearchClient extends ElasticSearch {

    // Force one-client-per-whelk
    ElasticSearchClient() {
        String elastichost, elasticcluster
        if (System.getProperty("elastic.host")) {
            elastichost = System.getProperty("elastic.host")
            elasticcluster = System.getProperty("elastic.cluster")
            log.info "Connecting to $elastichost:9300 using cluster $elasticcluster"
            def sb = ImmutableSettings.settingsBuilder()
            .put("client.transport.ping_timeout", 30000)
            .put("client.transport.sniff", true)
            if (elasticcluster) {
                sb = sb.put("cluster.name", elasticcluster)
            }
            Settings settings = sb.build();
            client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress(elastichost, 9300))
            log.debug("... connected")
        } else {
            throw new WhelkRuntimeException("Unable to initialize elasticsearch. Need at least system property \"elastic.host\" and possibly \"elastic.cluster\".")
        }
    }
}

//TODO: Move all settings (general and index level) to config files and make creation of index and changing of settings to separate operation tasks

@Log
class ElasticSearchNode extends ElasticSearch implements Index {

    ElasticSearchNode() {
        this(null)
    }

    ElasticSearchNode(String dataDir) {
        log.info "Starting elastic node"
        def elasticcluster = System.getProperty("elastic.cluster")
        ImmutableSettings.Builder sb = ImmutableSettings.settingsBuilder()
        sb.put("node.name", "Parasite")
        if (elasticcluster) {
            sb = sb.put("cluster.name", elasticcluster)
        } else {
            sb = sb.put("cluster.name", "bundled_whelk_index")
        }
        if (dataDir != null) {
            sb.put("path.data", dataDir)
        }
        sb.build()
        Settings settings = sb.build()
        NodeBuilder nBuilder = nodeBuilder().settings(settings)
        // start it!
        def node = nBuilder.build().start()
        client = node.client()
        log.info "Client connected to new (local) ES node."
    }
}

@Log
abstract class ElasticSearch extends BasicPlugin {

    def mapper

    Client client

    boolean enabled = true
    String id = "elasticsearch"
    int WARN_AFTER_TRIES = 1000
    int RETRY_TIMEOUT = 300
    int MAX_RETRY_TIMEOUT = 60*60*1000
    static int MAX_NUMBER_OF_FACETS = 100

    String URI_SEPARATOR = "::"

    String defaultType = "record"
    String elasticIndex
    String elasticMetaEntryIndex
    String currentIndex

    def defaultMapping, es_settings

    @Override
    void init(String indexName) {
        this.elasticIndex = indexName
        this.elasticMetaEntryIndex = "."+indexName
        if (!performExecute(client.admin().indices().prepareExists(indexName)).exists) {
            createNewCurrentIndex()
            log.debug("Will create alias $indexName -> $currentIndex")
            performExecute(client.admin().indices().prepareAliases().addAlias(currentIndex, indexName))
        } else {
            this.currentIndex = getRealIndexFor(indexName)
            log.info("Using currentIndex: $currentIndex")
            if (this.currentIndex == null) {
                throw new WhelkRuntimeException("Unable to find a real current index for $indexName")
            }
        }
        // Check for metaentryindex
        log.info("Checking meta entry index")
        if (!performExecute(client.admin().indices().prepareExists(elasticMetaEntryIndex)).exists) {
            log.info("Not found, creating metaentry index.")
            performExecute(client.admin().indices().prepareCreate(elasticMetaEntryIndex).setSettings(es_settings))
            setTypeMapping(elasticMetaEntryIndex, "entry")
        }
        log.info("LatestIndex: " + getLatestIndex(indexName))
    }

    String getRealIndexFor(String alias) {
        def aliases = performExecute(client.admin().cluster().prepareState()).state.metaData.aliases()
        log.debug("aliases: $aliases")
        def ri = null
        if (aliases.containsKey(alias)) {
            ri = aliases.get(alias)?.keys().iterator().next()
        }
        log.trace("ri: ${ri.value} (${ri.value.getClass().getName()})")
        return (ri ? ri.value : alias)
    }

    String getLatestIndex(String prefix) {
        def indices = performExecute(client.admin().cluster().prepareState()).state.metaData.indices
        log.debug("indexes: $indices")
        def li = new TreeSet<String>()
        for (idx in indices.keys()) {
            log.debug("idx: $idx.value")
            if (idx.value.startsWith(prefix)) {
                li << idx.value
            }
        }
        return li.last()
    }

    void createNewCurrentIndex() {
        log.info("Creating index ...")
        es_settings = loadJson("es_settings.json")
        this.currentIndex = "${elasticIndex}-" + new Date().format("yyyyMMdd.HHmmss")
        log.debug("Will create index $currentIndex.")
        performExecute(client.admin().indices().prepareCreate(currentIndex).setSettings(es_settings))
        setTypeMapping(currentIndex, defaultType)
        log.info("LatestIndex: " + getLatestIndex(elasticIndex))
    }

    void reMapAliases(String indexAlias) {
        def oldIndex = getRealIndexFor(indexAlias)
        log.debug("Resetting alias \"$indexAlias\" from \"$oldIndex\" to \"$currentIndex\".")
        performExecute(client.admin().indices().prepareAliases().addAlias(currentIndex, indexAlias).removeAlias(oldIndex, indexAlias))
    }

    void flush() {
        log.debug("Flushing indices.")
        def flushresponse = performExecute(new FlushRequestBuilder(client.admin().indices()))
        log.debug("Flush response: $flushresponse")
    }

    def loadJson(String file) {
        def json
        mapper = mapper ?: new ObjectMapper()
        try {
            json = getClass().classLoader.getResourceAsStream(file).withStream {
                mapper.readValue(it, Map)
            }
        } catch (NullPointerException npe) {
            log.trace("File $file not found.")
        }
        return json
    }

    @Override
    void delete(URI uri) {
        log.debug("Peforming deletebyquery to remove documents extracted from $uri")
        def delQuery = termQuery("extractedFrom.@id", uri.toString())
        log.debug("DelQuery: $delQuery")

        def response = performExecute(client.prepareDeleteByQuery(elasticIndex).setQuery(delQuery))

        log.debug("Delbyquery response: $response")
        for (r in response.iterator()) {
            log.debug("r: $r success: ${r.successfulShards} failed: ${r.failedShards}")
        }

        log.debug("Deleting object with identifier ${translateIdentifier(uri.toString())}.")

        client.delete(new DeleteRequest(elasticIndex, determineDocuentTypeBasedOnURI(uri.toString()), translateIdentifier(uri.toString())))


        // Kanske en matchall-query filtrerad på _type och _id?
    }

    @Override
    void index(Document doc) {
        if (doc && doc.isJson()) {
            addDocument(doc)
        }
    }

    @Override
    void bulkIndex(Iterable<Document> docs) {
        addDocuments(docs)
    }

    @Override
    InputStream rawQuery(String query) {

    }

    @Override
    SearchResult query(Query q) {
        def indexType = null
        if (q instanceof ElasticQuery) {
            indexType = q.indexType
        }
        return query(q, elasticIndex, indexType)
    }

    SearchResult query(Query q, String indexName, String indexType) {
        log.trace "Querying index $indexName and indextype $indexType"
        log.trace "Doing query on $q"
        def idxlist = [indexName]
        if (indexName.contains(",")) {
            idxlist = indexName.split(",").collect{it.trim()}
        }
        log.trace("Searching in indexes: $idxlist")
        def jsonDsl = q.toJsonQuery()
        def response = client.search(new SearchRequest(idxlist as String[], jsonDsl.getBytes("utf-8")).searchType(SearchType.DFS_QUERY_THEN_FETCH).types(indexType)).actionGet()
        log.trace("SearchResponse: " + response)

        def results = new SearchResult(0)

        if (response) {
            log.trace "Total hits: ${response.hits.totalHits}"
            results.numberOfHits = response.hits.totalHits
            response.hits.hits.each {
                if (q.highlights) {
                    results.addHit(createResultDocumentFromHit(it, indexName), convertHighlight(it.highlightFields))
                } else {
                    results.addHit(createResultDocumentFromHit(it, indexName))
                }
            }
            if (q.facets) {
                results.facets = convertFacets(response.facets.facets(), q)
            }
        }
        return results
    }

    Iterator<String> metaEntryQuery(String dataset, Date since, Date until) {
        def query = boolQuery()
        if (dataset) {
            query = query.must(termQuery("entry.dataset", dataset))
        }
        if (since || until) {
            def timeRangeQuery = rangeQuery("entry.timestamp")
            if (since) {
                timeRangeQuery = timeRangeQuery.from(since.getTime())
            }
            if (until) {
                timeRangeQuery = timeRangeQuery.to(since.getTime())
            }
            query = query.must(timeRangeQuery)
        }
        def srb = client.prepareSearch(elasticMetaEntryIndex)
            .setSearchType(SearchType.SCAN)
            .setScroll(new TimeValue(60000))
            .setTypes(["entry"] as String[])
            .setQuery(query)
            .setSize(100)

        def list = []
        log.debug("MetaEntryQuery: $srb")
        def scrollResp = performExecute(srb)
        return new Iterator<String>() {
            public boolean hasNext() {
                if (list.size() == 0) {
                    scrollResp = performExecute(super.client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(60000)))
                    list.addAll(scrollResp.hits.hits.collect { translateIndexIdTo(it.id) })
                }
                return list.size()
            }
            public String next() { list.pop() }
            public void remove() { throw new UnsupportedOperationException(); }
        }

        /*
        while (true) {
            log.trace("start loop")
            log.trace("Adding to list")
            if (scrollResp.hits.hits.length == 0) {
                log.debug("break loop")
                break
            }
        }
        return list
        */
    }

    def setTypeMapping(indexName, itype) {
        log.info("Creating mappings for $indexName/$itype ...")
        //XContentBuilder mapping = jsonBuilder().startObject().startObject("mappings")
        if (!defaultMapping) {
            defaultMapping = loadJson("default_mapping.json")
        }
        def typeMapping = loadJson("${itype}_mapping.json") ?: defaultMapping
        // Append special mapping for @id-fields
        if (!typeMapping.dynamic_templates) {
            typeMapping['dynamic_templates'] = []
        }
        if (!typeMapping.dynamic_templates.find { it.containsKey("id_template") }) {
            log.debug("Found no id_template. Creating.")
            typeMapping.dynamic_templates << ["id_template":["match":"@id","match_mapping_type":"string","mapping":["type":"string","index":"not_analyzed"]]]
        }

        String mapping = mapper.writeValueAsString(typeMapping)
        log.debug("mapping for $indexName/$itype: " + mapping)
        def response = performExecute(client.admin().indices().preparePutMapping(indexName).setType(itype).setSource(mapping))
        log.debug("mapping response: ${response.acknowledged}")
    }

    def performExecute(def requestBuilder) {
        int failcount = 0
        def response = null
        while (response == null) {
            try {
                response = requestBuilder.execute().actionGet()
            } catch (NoNodeAvailableException n) {
                log.trace("Retrying server connection ...")
                if (failcount++ > WARN_AFTER_TRIES) {
                    log.warn("Failed to connect to elasticsearch after $failcount attempts.")
                }
                if (failcount % 100 == 0) {
                    log.info("Server is not responsive. Still trying ...")
                }
                Thread.sleep(RETRY_TIMEOUT + failcount > MAX_RETRY_TIMEOUT ? MAX_RETRY_TIMEOUT : RETRY_TIMEOUT + failcount)
            }
        }
        return response
    }

    void checkTypeMapping(indexName, indexType) {
        def mappings = performExecute(client.admin().cluster().prepareState()).state.metaData.index(indexName).getMappings()
        log.debug("Mappings: $mappings")
        if (!mappings.containsKey(indexType)) {
            log.debug("Mapping for $indexName/$indexType does not exist. Creating ...")
            setTypeMapping(indexName, indexType)
        }
    }

    String determineDocumentType(Document doc) {
        def idxType = doc.entry['dataset']?.toLowerCase()
        log.debug("dataset in entry is ${idxType} for ${doc.identifier}")
        if (!idxType) {
            idxType = determineDocuentTypeBasedOnURI(doc.identifier)
        }
        log.debug("Using type $idxType for document ${doc.identifier}")
        return idxType
    }


    String determineDocuentTypeBasedOnURI(String identifier) {
        def idxType
        log.debug("Using identifier to determine type.")
        try {
            def identParts = identifier.split("/")
            idxType = (identParts[1] == elasticIndex && identParts.size() > 3 ? identParts[2] : identParts[1])
        } catch (Exception e) {
            log.error("Tried to use first part of URI ${identifier} as type. Failed: ${e.message}", e)
        }
        if (!idxType) {
            idxType = defaultType
        }
        log.trace("Using type $idxType for ${identifier}")
        return idxType
    }

    void addDocument(Document doc) {
        addDocuments([doc])
    }


    void addDocuments(documents) {
        try {
            if (documents) {
                def breq = client.prepareBulk()

                def checkedTypes = [defaultType]

                log.debug("Bulk request to index " + documents?.size() + " documents.")

                for (doc in documents) {
                    log.debug("Working on ${doc.identifier}")
                    if (doc && doc.isJson()) {
                        def indexType = determineDocumentType(doc)
                        def checked = indexType in checkedTypes
                        if (!checked) {
                            checkTypeMapping(elasticIndex, indexType)
                            checkedTypes << indexType
                        }
                        def elasticIdentifier = translateIdentifier(doc.identifier)
                        breq.add(client.prepareIndex(elasticIndex, indexType, elasticIdentifier).setSource(doc.data))
                        if (!doc.entry['origin']) {
                            breq.add(client.prepareIndex(elasticMetaEntryIndex, "entry", elasticIdentifier).setSource(doc.metadataAsJson.getBytes("utf-8")))
                        }
                    } else {
                        log.debug("Doc is null or not json (${doc.contentType})")
                    }
                }
                def response = performExecute(breq)
                if (response.hasFailures()) {
                    log.error "Bulk import has failures."
                    def fails = []
                    for (re in response.items) {
                        if (re.failed) {
                            log.error "Fail message for id ${re.id}, type: ${re.type}, index: ${re.index}: ${re.failureMessage}"
                            if (log.isTraceEnabled()) {
                                for (doc in documents) {
                                    if (doc.identifier.toString() == "/"+re.index+"/"+re.id) {
                                        log.trace("Failed document: ${doc.dataAsString}")
                                    }
                                }
                            }
                            try {
                                fails << translateIndexIdTo(re.id)
                            } catch (Exception e1) {
                                log.error("TranslateIndexIdTo cast an exception", e1)
                                fails << "Failed translation for \"$re\""
                            }
                        }
                    }
                    throw new WhelkAddException(fails)
                }
            }
        } catch (Exception e) {
            log.error("Exception thrown while adding documents", e)
            throw e
        }
    }

    def translateIdentifier(String uri) {
        def idelements = new URI(uri).path.split("/") as List
        idelements.remove(0)
        return idelements.join(URI_SEPARATOR)
    }

    def Map<String, String[]> convertHighlight(Map<String, HighlightField> hfields) {
        def map = new TreeMap<String, String[]>()
        hfields.each {
            map.put(it.value.name, it.value.fragments)
        }
        return map
    }

    def convertFacets(eFacets, query) {
        def facets = new HashMap<String, Map<String, Integer>>()
        for (def f : eFacets) {
            def termcounts = [:]
            try {
                for (def entry : f.entries) {
                    termcounts[entry.term] = entry.count
                }
                facets.put(f.name, termcounts.sort { a, b -> b.value <=> a.value })
            } catch (MissingMethodException mme) {
                def group = query.facets.find {it.name == f.name}.group
                termcounts = facets.get(group, [:])
                if (f.count) {
                    termcounts[f.name] = f.count
                }
                facets.put(group, termcounts.sort { a, b -> b.value <=> a.value })
            }
        }
        return facets
    }

    Document createResultDocumentFromHit(hit, queriedIndex = null) {
        def emei = elasticMetaEntryIndex
        if (queriedIndex) {
            emei = ".$queriedIndex"
        }
        def grb = new GetRequestBuilder(client, emei).setType("entry").setId(hit.id)
        def result = performExecute(grb)
        if (result.exists) {
            return new Document(result.sourceAsMap).withData(hit.source()).withIdentifier(translateIndexIdTo(hit.id))
        } else {
            return new Document().withData(hit.source()).withIdentifier(translateIndexIdTo(hit.id))
        }
    }

    String translateIndexIdTo(id) {
        def pathelements = []
        id.split(URI_SEPARATOR).each {
            pathelements << java.net.URLEncoder.encode(it, "UTF-8")
        }
        return  new String("/"+pathelements.join("/"))
    }
}
