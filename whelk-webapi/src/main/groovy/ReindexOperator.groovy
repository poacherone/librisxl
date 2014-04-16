package se.kb.libris.whelks.api

import groovy.util.logging.Slf4j as Log

import org.codehaus.jackson.map.*
import org.codehaus.jackson.map.SerializationConfig.Feature

import java.util.concurrent.*

import se.kb.libris.whelks.*
import se.kb.libris.whelks.exception.*
import se.kb.libris.whelks.importers.*
import se.kb.libris.whelks.plugin.*

import se.kb.libris.conch.Tools

@Log
class ReindexOperator extends AbstractOperator {
    String oid = "reindex"

    // Unique for this operator
    List<String> selectedComponents = null
    String startAt = null
    String fromStorage = null
    ExecutorService queue

    boolean showSpinner = false

    @Override
    void setParameters(Map parameters) {
        super.setParameters(parameters)
        if (parameters.selectedComponents) {
            this.selectedComponents = parameters.get("selectedComponents").split(",") as List<String>
        }
        this.fromStorage = parameters.get("fromStorage", null)
        this.showSpinner = parameters.get("showSpinner", false)
    }

    void doRun(long startTime) {
        List<Document> docs = []
        queue = Executors.newSingleThreadExecutor()
        if (!dataset) {
            log.debug("Requesting new index for ${whelk.index.id}.")
            whelk.index.createNewCurrentIndex()
        }
        if (fromStorage) {
            log.info("Rebuilding storage from $fromStorage")
        }
        for (doc in whelk.loadAll(dataset, null, fromStorage)) {
            log.trace("Adding doc ${doc.identifier} with type ${doc.contentType}")
            if (!doc.entry.deleted) {
                if (fromStorage) {
                    try {
                        docs << whelk.addToStorage(doc, fromStorage)
                    } catch (WhelkAddException wae) {
                        log.trace("Expected exception ${wae.message}")
                    }
                } else {
                    docs << doc
                }
            } else {
                log.warn("Document ${doc.identifier} is deleted. Don't try to add it.")
            }
            if (++count % 1000 == 0) { // Bulk index 1000 docs at a time
                doTheIndexing(docs)
                docs = []
            }
            runningTime = System.currentTimeMillis() - startTime
            if (showSpinner) {
                def velocityMsg = "Current velocity: ${count/(runningTime/1000)}."
                Tools.printSpinner("Rebuilding from ${fromStorage}. ${count} documents rebuilt sofar. $velocityMsg", count)
            }
            if (cancelled) {
                break
            }
        }
        log.debug("Went through all documents. Processing remainder.")
        if (docs.size() > 0) {
            log.trace("Reindexing remaining ${docs.size()} documents")
            try {
                whelk.addToGraphStore(docs)
            } catch (WhelkAddException wae) {
                //errorMessages << new String(wae.message + " (" + wae.failedIdentifiers + ")")
                log.warn("Failed adding identifiers to graphstore: ${wae.failedIdentifiers as String}")
            }
            try {
                whelk.addToIndex(docs)
            } catch (WhelkAddException wae) {
                //errorMessages << new String(wae.message + " (" + wae.failedIdentifiers + ")")
                log.warn("Failed adding identifiers to graphstore: ${wae.failedIdentifiers as String}")
            }
        }
        log.info("Reindexed $count documents in " + ((System.currentTimeMillis() - startTime)/1000) + " seconds." as String)
        if (!dataset) {
            if (cancelled) {
                log.info("Process cancelled, resetting currentIndex")
                whelk.index.currentIndex = whelk.index.getRealIndexFor(whelk.index.elasticIndex)
            } else {
                whelk.index.reMapAliases()
            }
        }
        operatorState=OperatorState.FINISHING
        queue.execute({
            this.whelk.flush()
        } as Runnable)
        queue.shutdown()
    }

    void doTheIndexing(final List docs) {
        queue.execute({
            try {
                whelk.addToGraphStore(docs)
            } catch (WhelkAddException wae) {
                //errorMessages << new String(wae.message + " (" + wae.failedIdentifiers + ")")
                log.warn("Failed adding identifiers to graphstore: ${wae.failedIdentifiers}")
            }
            try {
                whelk.addToIndex(docs)
            } catch (WhelkAddException wae) {
                //errorMessages << new String(wae.message + " (" + wae.failedIdentifiers + ")")
                log.warn("Failed indexing identifiers: ${wae.failedIdentifiers}")
            } catch (PluginConfigurationException pce) {
                log.error("System badly configured", pce)
                throw pce
            }
        } as Runnable)
    }

    @Override
    Map getStatus() {
        def map = super.getStatus()
        if (hasRun) {
            if (fromStorage) {
                map.get("lastrun").put("fromStorage", fromStorage)
            }
            if (selectedComponents) {
                map.get("lastrun").put("selectedComponents", selectedComponents)
            }
        } else {
            if (fromStorage) {
                map.put("fromStorage", fromStorage)
            }
            if (selectedComponents) {
                map.put("selectedComponents", selectedComponents)
            }
        }
        return map
    }
}
