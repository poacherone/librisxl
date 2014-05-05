package se.kb.libris.whelks.component

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import groovy.transform.Synchronized

import java.util.concurrent.*

import se.kb.libris.whelks.Document
import se.kb.libris.whelks.exception.*
import se.kb.libris.whelks.plugin.BasicPlugin
import se.kb.libris.whelks.plugin.Plugin

import se.kb.libris.conch.Tools

import gov.loc.repository.pairtree.Pairtree

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.*

import com.google.common.io.Files

class PairtreeHybridDiskStorage extends PairtreeDiskStorage implements HybridStorage {
    Index index

    String indexName

    PairtreeHybridDiskStorage(Map settings) {
        super(settings)
    }

    void init(String stName) {
        super.init(stName)
        if (index) {
            indexName = "."+stName
            index.init(indexName)
            index.checkTypeMapping(indexName, "entry")
        }
    }

    @Override
    @groovy.transform.CompileStatic
    boolean store(Document doc) {
        boolean result = false
        try {
            result = super.store(doc)
            if (result) {
                index.index(doc.metadataAsJson.getBytes("utf-8"),
                    [
                        "index": ".libris",
                        "type": "entry",
                        "id": ((ElasticSearch)index).translateIdentifier(doc.identifier)
                    ]
                )
            }
        } catch (Exception e) {
            throw new WhelkAddException("Failed to store ${doc.identifier}", e, [doc.identifier])
        }

        return result
    }

    @Override
    Iterable<Document> getAll(String dataset = null, Date since = null, Date until = null) {
        if (dataset) {
            log.info("Loading documents by index query for dataset $dataset")
            def elasticResultIterator = index.metaEntryQuery(indexName, dataset, since, until)
            return new Iterable<Document>() {
                Iterator<Document> iterator() {
                    return new Iterator<Document>() {
                        public boolean hasNext() { elasticResultIterator.hasNext()}
                        public Document next() {
                            return super.get(elasticResultIterator.next())
                        }
                        public void remove() { throw new UnsupportedOperationException(); }
                    }
                }
            }
        } else if (since) {
        }
        return getAllRaw(dataset)
    }
    @Override
    void delete(URI uri, String whelkId) {
        super.delete(uri, whelkId)
        index.deleteFromEntry(uri, indexName)
    }

    @Override
    void rebuildIndex() {
        int count = 0
        List<Map<String,String>> entries = []
        log.info("Started rebuild of metaindex for $indexName.")
        for (document in getAllRaw()) {
            entries << [
                    "index":indexName,
                    "type": "entry",
                    "id": ((ElasticSearch)index).translateIdentifier(document.identifier),
                    "data":((Document)document).metadataAsJson
                ]
            if (count++ % 1000 == 0) {
                index.index(entries)
                entries = []
            }
            if (log.isInfoEnabled() && count % 10000 == 0) {
                log.info("[${new Date()}] Rebuilding metaindex for $indexName. $count sofar.")
            }
        }
        if (entries.size() > 0) {
            index.index(entries)
        }
        log.info("Meta index rebuilt.")
    }
}

class PairtreeDiskStorage extends BasicPlugin implements Storage {
    String baseStorageDir = "./storage"
    String storageDir = null
    String versionsStorageDir = null
    String baseStorageSuffix = null
    boolean enabled = true

    String id = "diskstorage"
    List contentTypes
    boolean versioning

    final static Pairtree pairtree = new Pairtree()

    static final String ENTRY_FILE_NAME = "entry.json"
    static final String DATAFILE_EXTENSION = ".data"
    static final String MAIN_STORAGE_DIR = "main"
    static final String VERSIONS_STORAGE_DIR = "versions"
    static final String FILE_NAME_KEY = "dataFileName"

    static final String VERSION_DIR_PREFIX = "version_"

    static final Map FILE_EXTENSIONS = [
        "application/json" : ".json",
        "application/ld+json" : ".jsonld",
        "application/x-marc-json" : ".json",
        "application/xml" : ".xml",
        "text/xml" : ".xml"
    ]

    static final Logger log = LoggerFactory.getLogger(PairtreeDiskStorage.class)

    // TODO: Add document counter

    PairtreeDiskStorage(Map settings) {
        StringBuilder dn = new StringBuilder(settings['storageDir'])
        while (dn[dn.length()-1] == '/') {
            dn.deleteCharAt(dn.length()-1)
        }
        this.baseStorageDir = dn.toString()
        this.contentTypes = settings.get('contentTypes', null)
        this.versioning = settings.get('versioning', false)
        this.baseStorageSuffix = settings.get('baseStorageSuffix', null)
    }

    void init(String stName) {
        if (!this.baseStorageSuffix) {
            this.baseStorageSuffix = this.id
        }
        if (versioning) {
            this.versionsStorageDir = this.baseStorageDir + "/" + stName + "_" + this.baseStorageSuffix + "/" + VERSIONS_STORAGE_DIR
        }
        this.storageDir = this.baseStorageDir + "/" + stName + "_" + this.baseStorageSuffix + "/" + MAIN_STORAGE_DIR
        log.info("Starting DiskStorage with storageDir $storageDir ${(versioning ? "and versions in $versionsStorageDir" : "")}")
    }

    def void enable() {this.enabled = true}
    def void disable() {this.enabled = false}

    OutputStream getOutputStreamFor(Document doc) {
        File file = new File(buildPath(doc.identifier))
        return file.newOutputStream()
    }

    @Override
    @groovy.transform.CompileStatic
    boolean store(Document doc) {
        if (doc && (handlesContent(doc.contentType) || doc.entry.deleted)) {
            if (this.versioning) {
                doc = checkAndUpdateExisting(doc)
            }
            String filePath = buildPath(doc.identifier)
            return writeDocumentToDisk(doc, filePath, getBaseFilename(doc.identifier))
        }
        return false
    }

    @groovy.transform.CompileStatic
    private boolean writeDocumentToDisk(Document doc, String filePath, String fileName) {
        String extension = FILE_EXTENSIONS.get(doc.contentType, DATAFILE_EXTENSION)
        log.trace("Using extension: $extension")
        String sourcefilePath = filePath + "/" + fileName + extension
        File sourcefile = new File(sourcefilePath)
        File metafile = new File(filePath + "/" + ENTRY_FILE_NAME)
        try {
            log.trace("Saving file with path ${sourcefile.path}")
            FileUtils.writeByteArrayToFile(sourcefile, doc.data)
            log.trace("Setting entry in document meta")
            doc.getEntry().put(FILE_NAME_KEY, fileName + extension)
            log.trace("Saving file with path ${metafile.path}")
            FileUtils.write(metafile, doc.metadataAsJson, "utf-8")
            return true
        } catch (IOException ioe) {
            log.error("Write failed: ${ioe.message}", ioe)
            throw ioe
        }
        return false
    }

    Document checkAndUpdateExisting(Document doc) {
        log.trace("checking for existingdoc with identifier ${doc.identifier}")
        Document existingDocument = get(doc.identifier)
        log.trace("found: $existingDocument")
        int version = 1
        if (existingDocument) {
            if (existingDocument.entry?.checksum == doc.entry?.checksum) {
                throw new DocumentException(DocumentException.IDENTICAL_DOCUMENT, "Identical document already stored.")
            }
            version = existingDocument.version + 1
            Map versions = existingDocument.entry.versions ?: [:]
            String lastVersion = existingDocument.version as String
            versions[lastVersion] = ["timestamp" : existingDocument.timestamp]
            if (existingDocument?.entry?.deleted) {
                versions.get(lastVersion).put("deleted", true)
            } else {
                versions.get(lastVersion).put("checksum",existingDocument.entry.checksum)
            }
            doc.entry.put("versions", versions)
            writeDocumentToDisk(existingDocument, buildPath(existingDocument.identifier, existingDocument.version), getBaseFilename(existingDocument.identifier))
        }
        log.trace("Setting document version: $version")
        return doc.withVersion(version)
    }

    @groovy.transform.CompileStatic
    String getBaseFilename(String identifier) {
        identifier.substring(identifier.lastIndexOf("/")+1)
    }

    @groovy.transform.CompileStatic
    Document get(String uri, String version=null) {
        return get(new URI(uri), version)
    }

    @Override
    @groovy.transform.CompileStatic
    Document get(URI uri, String version = null) {
        log.trace("Received GET request for ${uri.toString()} with version $version")
        String filePath = buildPath(uri.toString(), (version ? version as int : 0))
        String fileName =  getBaseFilename(uri.toString())
        try {
            log.trace("filePath: $filePath")
            File metafile = new File(filePath + "/" + ENTRY_FILE_NAME)
            def document = new Document(FileUtils.readFileToString(metafile, "utf-8"))
            File sourcefile = new File(filePath + "/" + fileName + FILE_EXTENSIONS.get(document.contentType, DATAFILE_EXTENSION))
            return document.withData(FileUtils.readFileToByteArray(sourcefile))
        } catch (FileNotFoundException fnfe) {
            log.trace("Files on $filePath not found.")
            if (version) {
                log.debug("Trying to see if requested version is actually current version.")
                def document = get(uri)
                if (document && document.version == version as int) {
                    log.debug("Why, yes it was!")
                    return document
                } else {
                    log.debug("Nah, it wasn't")
                }
            }
            return null
        }
    }

    void benchmark() {
        int count = 0
        long startTime = System.currentTimeMillis()
        long runningTime = 0

        File baseDir = new File(this.storageDir)
        def files = Files.fileTreeTraverser().preOrderTraversal(baseDir)

        for (file in files) {
            if (file.name == PairtreeDiskStorage.ENTRY_FILE_NAME) {
                count++
                runningTime = System.currentTimeMillis() - startTime
                def velocityMsg = "Current velocity: ${count/(runningTime/1000)}."
                Tools.printSpinner("Benchmarking ${this.id}. ${count} documents read sofar. $velocityMsg", count)
            }
        }
    }

    @Override
    Iterable<Document> getAll(String dataset = null) {
        return getAllRaw(dataset)
    }

    @groovy.transform.CompileStatic
    Iterable<Document> getAllRaw(String dataset = null) {
        File baseDir = (dataset != null ? new File(this.storageDir + "/" + dataset) : new File(this.storageDir))
        log.info("Starting reading for getAllRaw() at ${baseDir.getPath()}.")
        final Iterator<File> fileIterator = Files.fileTreeTraverser().preOrderTraversal(baseDir).iterator()
        return new Iterable<Document>() {
            Iterator<Document> iterator() {
                return new Iterator<Document>() {
                    static final Logger log = LoggerFactory.getLogger("se.kb.libris.whelks.component.PairtreeDiskStorage")
                    File lastValidEntry = null

                    public boolean hasNext() {
                        while (fileIterator.hasNext()) {
                            File f = fileIterator.next()
                            if (f.name == PairtreeDiskStorage.ENTRY_FILE_NAME) {
                                lastValidEntry = f
                                return true
                            }
                        }
                        return false
                    }

                    public Document next() {
                        if (lastValidEntry) {
                            Document document = new Document(FileUtils.readFileToString(lastValidEntry, "utf-8"))
                            try {
                                document.withData(FileUtils.readFileToByteArray(new File(lastValidEntry.getParentFile(), document.getEntry().get(PairtreeDiskStorage.FILE_NAME_KEY))))
                            } catch (FileNotFoundException fnfe) {
                                log.trace("File not found using ${document.getEntry().get(PairtreeDiskStorage.FILE_NAME_KEY)} as filename. Will try to use it as path.")
                                document.withData(FileUtils.readFileToByteArray(new File(document.getEntry().get(PairtreeDiskStorage.FILE_NAME_KEY))))
                            } catch (InterruptedException e) {
                                e.printStackTrace()
                            }
                            lastValidEntry = null
                            return document
                        }
                        throw new NoSuchElementException()
                    }

                    public void remove() { throw new UnsupportedOperationException() }
                }
            }
        }
    }

    @Override
    void delete(URI uri, String whelkId) {
        if (versioning) {
            store(createTombstone(uri))
        } else {
            try {
                def fn = buildPath(uri.toString())
                log.debug("Deleting $fn")
                if (!new File(fn).deleteDir()) {
                    log.error("Failed to delete $uri")
                    throw new WhelkRuntimeException("" + this.getClass().getName() + " failed to delete $uri")
                }
            } catch (Exception e) {
                throw new WhelkRuntimeException(e)
            }
        }
    }

    @groovy.transform.CompileStatic
    String buildPath(String id, int version = 0) {
        int pos = id.lastIndexOf("/")
        String path
        String baseDir = (version > 0 ? this.versionsStorageDir : this.storageDir)
        String encasingDir = (version > 0 ? VERSION_DIR_PREFIX + version : null)
        if (pos != -1) {
            path = pairtree.mapToPPath(baseDir + id.substring(0, pos), id.substring(pos+1), encasingDir)
        } else {
            path = pairtree.mapToPPath(baseDir, id, encasingDir)
        }
        return path
    }

    private Document createTombstone(uri) {
        def tombstone = new Document().withIdentifier(uri).withData("DELETED ENTRY")
        tombstone.entry['deleted'] = true
        return tombstone
    }


    @Override
    boolean handlesContent(String ctype) {
        return (ctype == "*/*" || !this.contentTypes || this.contentTypes.contains(ctype))
    }
}


@Deprecated
class DiskStorage extends PairtreeDiskStorage implements Storage {

    String docFolder = "_"
    int PATH_CHUNKS=4

    DiskStorage(Map settings) {
       super(settings)
    }


    @Override
    String buildPath(String id, int version = 0) {
        def path = this.storageDir + "/" + id.substring(0, id.lastIndexOf("/"))
        def basename = id.substring(id.toString().lastIndexOf("/")+1)

        for (int i=0; i*PATH_CHUNKS+PATH_CHUNKS < basename.length(); i++) {
            path = path + "/" + basename[i*PATH_CHUNKS .. i*PATH_CHUNKS+PATH_CHUNKS-1].replaceAll(/[\.]/, "")
        }

        if (this.docFolder) {
            path = path + "/" + this.docFolder + "/" + basename
        }
        return path.replaceAll(/\/+/, "/") //+ "/" + basename
    }
}

class FlatDiskStorage extends DiskStorage {


    FlatDiskStorage(Map settings) {
        super(settings)
    }


    @Override
    String buildPath(String id, boolean createDirectories, int version = 0) {
        def path = (this.storageDir + "/" + new URI(id).path).replaceAll(/\/+/, "/")
        if (createDirectories) {
            new File(path).mkdirs()
        }
        return path
    }
}

/*
class FileWalker implements Iterator<Document> {
    final BlockingQueue<Document> bq
    FileWalker(final File fileStart, final int size) throws Exception {
        bq = new ArrayBlockingQueue<Document>(size)
        Thread thread = new Thread(new Runnable() {
            static final Logger log = LoggerFactory.getLogger("se.kb.libris.whelks.component.PairtreeDiskStorage")
            public void run() {
                try {
                    Files.walkFileTree(fileStart.toPath(), new FileVisitor<Path>() {
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            return FileVisitResult.CONTINUE
                        }
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                            File file = path.toFile()
                            if (file.name != PairtreeDiskStorage.ENTRY_FILE_NAME) {
                                return FileVisitResult.CONTINUE
                            }
                            Document document = new Document(FileUtils.readFileToString(file, "utf-8"))
                            try {
                                document.withData(FileUtils.readFileToByteArray(new File(file.getParentFile(), document.getEntry().get(PairtreeDiskStorage.FILE_NAME_KEY))))
                            } catch (FileNotFoundException fnfe) {
                                log.trace("File not found using ${document.getEntry().get(PairtreeDiskStorage.FILE_NAME_KEY)} as filename. Will try to use it as path.")
                                document.withData(FileUtils.readFileToByteArray(new File(document.getEntry().get(PairtreeDiskStorage.FILE_NAME_KEY))))
                            } catch (InterruptedException e) {
                                e.printStackTrace()
                            }
                            log.trace("Offering document ${document.identifier} to queue.")
                            super.bq.offer(document, 4242, TimeUnit.HOURS)
                            return FileVisitResult.CONTINUE
                        }
                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE
                        }
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE
                        }
                    })
                } catch (IOException e) {
                    e.printStackTrace()
                }
            }
        })
        thread.setDaemon(true)
        thread.start()
        thread.join(200)
    }
    public boolean hasNext() {
        boolean hasNext = false
        long dropDeadMS = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < dropDeadMS) {
            if (bq.peek() != null) {
                hasNext = true
                break
            }
            try {
                Thread.sleep(1)
            } catch (InterruptedException e) {
                e.printStackTrace()
            }
        }
        return hasNext
    }
    public Document next() {
        Document document = null
        try {
            document = bq.take()
        } catch (InterruptedException e) {
            e.printStackTrace()
        }
        return document
    }
    public void remove() {
        throw new UnsupportedOperationException()
    }

}
*/
