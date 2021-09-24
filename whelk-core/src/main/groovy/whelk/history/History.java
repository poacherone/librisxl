package whelk.history;

import whelk.JsonLd;

import java.util.*;

public class History {
    private HashMap<List<Object>, Ownership> m_pathOwnership;

    // The last version added to this history, needed for diffing the next one against.
    private DocumentVersion m_lastVersion;

    private JsonLd m_jsonLd;

    /**
     * Reconstruct a records history given a (backwards chronologically ordered "DESC")
     * list of versions of said record
     */
    public History(List<DocumentVersion> versions, JsonLd jsonLd) {
        m_jsonLd = jsonLd;
        m_pathOwnership = new HashMap<>();

        // The list we get is sorted backwards chronologically,
        // this needs to be the case for other uses of the same
        // list, so "deal with it".
        for (int i = versions.size()-1; i > -1 ; --i) {
            DocumentVersion version = versions.get(i);
            addVersion(version);
        }
    }

    public void addVersion(DocumentVersion version) {
        if (m_lastVersion == null) {
            m_pathOwnership.put( new ArrayList<>(), new Ownership(version, null) );
        } else {
            examineDiff(new ArrayList<>(), version, version.doc.data, m_lastVersion.doc.data, null);
        }
        m_lastVersion = version;
    }

    public Ownership getOwnership(List<Object> path) {
        List<Object> temp = new ArrayList<>(path);
        while (!temp.isEmpty()) {
            Ownership value = m_pathOwnership.get(temp);
            if (value != null)
                return value;
            temp.remove(temp.size()-1);
        }
        return m_pathOwnership.get(new ArrayList<>()); // The root (first) owner
    }

    /**
     * Examine differences between (what would presumably be) the same entity
     * in two versions of a record.
     *
     * 'version' is the new version (whole Record),
     * 'previousVersion' is the old one (whole Record),
     * 'path' is where in the record(s) we are,
     * 'examining' is the object (entity?) being compared,
     * 'correspondingPrevious' is the "same" object in the old version
     * 'compositePath' is null or a (shorter/higher) path to the latest
     * found enclosing "composite object", such as for example a Title,
     * which is considered _one value_ even though it is structured and
     * has subcomponents. The point of this is that changing (for example)
     * a subTitle should result in ownership of the whole title (not just
     * the subtitle).
     */
    private void examineDiff(List<Object> path,
                             DocumentVersion version,
                             Object examining, Object correspondingPrevious,
                             List<Object> compositePath) {
        if (examining instanceof Map) {

            if (! (correspondingPrevious instanceof Map) ) {
                setOwnership(path, compositePath, version);
                return;
            }

            Set k1 = ((Map) examining).keySet();
            Set k2 = ((Map) correspondingPrevious).keySet();

            // Is this a composite object ?
            Object type = ((Map)examining).get("@type");
            if ( type instanceof String &&
                    ( m_jsonLd.isSubClassOf( (String) type, "StructuredValue") ||
                            m_jsonLd.isSubClassOf( (String) type, "QualifiedRole") ) ) {
                compositePath = new ArrayList<>(path);
            }

            // Key added!
            if (!k2.containsAll(k1)) {
                Set newKeys = new HashSet(k1);
                newKeys.removeAll(k2);

                for (Object key : newKeys) {
                    List<Object> newPath = new ArrayList(path);
                    newPath.add(key);
                    setOwnership(newPath, compositePath, version);
                }
            }
        }

        if (examining instanceof List) {
            if (! (correspondingPrevious instanceof List) ) {
                setOwnership(path, compositePath, version);
                return;
            }
        }

        if (examining instanceof String ||
                examining instanceof Float || examining instanceof Boolean) {
            if (!examining.equals(correspondingPrevious)) {
                setOwnership(path, compositePath, version);
                return;
            }
        }

        // Keep scanning
        if (examining instanceof List) {
            // Create copies of the two lists (so that they can be manipulated)
            // and remove from them any elements that _have an identical copy_ in the
            // other list.
            // This way, only elements that differ somehow remain to be checked, and
            // they remain in their relative order to one another.
            // Without this, removal or addition of a list element results in every
            // _following_ element being compared with the wrong element in the other list.
            List tempNew = new LinkedList((List) examining);
            List tempOld = new LinkedList((List) correspondingPrevious);
            for (int i = 0; i < tempNew.size(); ++i) {
                for (int j = 0; j < tempOld.size(); ++j) {
                    if (tempNew.get(i).equals(tempOld.get(j))) { // Equals will recursively check the entire subtree!
                        tempNew.remove(i);
                        tempOld.remove(j);
                        --i;
                        --j;
                        break;
                    }
                }
            }

            for (int i = 0; i < tempNew.size(); ++i) {
                List<Object> childPath = new ArrayList(path);
                if ( tempOld.size() > i ) {
                    childPath.add(new Integer(i));
                    examineDiff(childPath, version,
                            tempNew.get(i), tempOld.get(i),
                            compositePath);
                }
            }
        } else if (examining instanceof Map) {
            for (Object key : ((Map) examining).keySet() ) {
                List<Object> childPath = new ArrayList(path);
                if ( ((Map)correspondingPrevious).get(key) != null ) {
                    childPath.add(key);
                    examineDiff(childPath, version,
                            ((Map) examining).get(key), ((Map) correspondingPrevious).get(key),
                            compositePath);
                }
            }
        }
    }

    private void setOwnership(List<Object> newPath, List<Object> compositePath,
                              DocumentVersion version) {
        List<Object> path;
        if (compositePath != null) {
            path = compositePath;
        } else {
            path = newPath;
        }
        m_pathOwnership.put( path, new Ownership(version, m_pathOwnership.get(path)) );
    }

    // DEBUG CODE BELOW THIS POINT
    public String toString() {
        StringBuilder b = new StringBuilder();
        toString(b, m_lastVersion.doc.data, 0, new ArrayList<>());
        return b.toString();
    }

    private void toString(StringBuilder b, Object current, int indent, List<Object> path) {
        if (current instanceof List) {
            for (int i = 0; i < ((List) current).size(); ++i) {
                beginLine(b, indent, path);
                b.append("[\n");
                List<Object> childPath = new ArrayList(path);
                childPath.add(new Integer(i));
                toString(b, ((List)current).get(i), indent + 1, childPath);
                b.setLength(b.length()-1); // drop newline
                b.append(",\n");
                beginLine(b, indent, path);
                b.append("]\n");
            }
        } else if (current instanceof Map) {
            beginLine(b, indent, path);
            b.append("{\n");
            for (Object key : ((Map) current).keySet() ) {
                List<Object> childPath = new ArrayList(path);
                childPath.add(key);
                beginLine(b, indent+1, childPath);
                b.append( "\"" + key + "\"" + " : \n");
                toString(b, ((Map) current).get(key), indent + 1, childPath);
                b.setLength(b.length()-1); // drop newline
                b.append(",\n");
            }
            beginLine(b, indent, path);
            b.append("}\n");
        } else {
            // Bool, string, number
            beginLine(b, indent, path);
            b.append("\"");
            b.append(current.toString());
            b.append("\"");
        }
    }

    private void beginLine(StringBuilder b, int indent, List<Object> path) {
        Formatter formatter = new Formatter(b);
        formatter.format("%1$-50s| ", getOwnership(path));
        for (int i = 0; i < indent; ++i) {
            b.append("  ");
        }
    }
}
