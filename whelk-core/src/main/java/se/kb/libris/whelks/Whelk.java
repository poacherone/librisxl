package se.kb.libris.whelks;

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import se.kb.libris.whelks.plugin.Plugin;
import se.kb.libris.whelks.result.SearchResult;
import se.kb.libris.whelks.result.SparqlResult;

public interface Whelk {
    // storage
    public URI add(Document d);
    public URI add(byte[] data,
            Map<String, Object> entrydata,
            Map<String, Object> metadata);
    public void bulkAdd(List<Document> d, String contentType);
    public Document get(URI identifier);
    public void remove(URI identifier);

    // search/lookup
    public SearchResult search(Query query);
    public Iterable<Document> loadAll();
    public Iterable<Document> loadAll(Date since);

    public InputStream sparql(String query);

    // maintenance
    public String getId(); // Whelk ID

    public void addPlugin(Plugin plugin);
    public Iterable<? extends Plugin> getPlugins();

    public void flush();

    public URI mintIdentifier(Document document);

    // ecosystem
    public Map getGlobal();
}
