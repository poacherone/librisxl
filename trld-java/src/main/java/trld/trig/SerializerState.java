/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/trig/serializer.py
 */
package trld.trig;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;

import trld.Output;
import static trld.Common.uuid4;
import static trld.jsonld.Base.BASE;
import static trld.jsonld.Base.CONTAINER;
import static trld.jsonld.Base.CONTEXT;
import static trld.jsonld.Base.GRAPH;
import static trld.jsonld.Base.ID;
import static trld.jsonld.Base.INDEX;
import static trld.jsonld.Base.LANGUAGE;
import static trld.jsonld.Base.LIST;
import static trld.jsonld.Base.PREFIX;
import static trld.jsonld.Base.PREFIX_DELIMS;
import static trld.jsonld.Base.REVERSE;
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.VALUE;
import static trld.jsonld.Base.VOCAB;
import static trld.trig.Serializer.*;


public class SerializerState {
  public Settings settings;
  public Output out;
  public /*@Nullable*/ SerializerState parent;
  public Map<String, Object> context;
  public /*@Nullable*/ String baseIri;
  public Map<String, String> prefixes;
  public KeyAliases aliases;
  public /*@Nullable*/ String bnodeSkolemBase = null;
  public String prefixKeyword;
  public String baseKeyword;
  public /*@Nullable*/ String graphKeyword = null;
  public String uniqueBnodeSuffix;
  public Integer bnodeCounter;

  public SerializerState(Output out, Settings settings, /*@Nullable*/ Object context) {
    this(out, settings, context, null);
  }
  public SerializerState(Output out, Settings settings, /*@Nullable*/ Object context, /*@Nullable*/ String baseIri) {
    this(out, settings, context, baseIri, null);
  }
  public SerializerState(Output out, Settings settings, /*@Nullable*/ Object context, /*@Nullable*/ String baseIri, SerializerState parent) {
    this.out = (out != null ? out : ((SerializerState) parent).out);
    this.baseIri = baseIri;
    this.parent = parent;
    this.settings = (parent != null ? parent.settings : settings);
    this.prefixKeyword = (String) this.kw("prefix");
    this.baseKeyword = (String) this.kw("base");
    if (this.settings.useGraphKeyword) {
      this.graphKeyword = (String) this.kw("graph");
    }
    this.uniqueBnodeSuffix = "";
    this.bnodeCounter = 0;
    this.aliases = new KeyAliases();
    this.context = new HashMap<>();
    this.prefixes = new HashMap<>();
    this.initContext(context);
  }

  protected String kw(String s) {
    return (this.settings.upcaseKeywords ? s.toUpperCase() : s);
  }

  public void initContext(/*@Nullable*/ Object ctx) {
    Map<String, Object> merged = new HashMap<>();
    if (this.context.size() > 0) {
      merged.putAll(this.context);
    }
    if (ctx instanceof List) {
      for (Object item : (List) ctx) {
        merged.putAll(((Map<String, Object>) item));
      }
    } else if (ctx instanceof Map) {
      merged.putAll((Map) ctx);
    }
    if (merged.size() > 0) {
      this.context = merged;
      this.prefixes = (Map<String, String>) collectPrefixes(merged);
    }
  }

  public void serialize(Object data) {
    if (data instanceof Map) {
      this.initContext(((Map) data).get(CONTEXT));
      this.prelude(this.prefixes);
      this.writeObject((Map) data);
    } else {
      assert data instanceof List;
      this.prelude(this.prefixes);
      for (Object item : (List) data) {
        this.writeObject(item);
      }
    }
  }

  public void prelude(Map<String, String> prefixes) {
    for (Map.Entry<String, String> k_v : prefixes.entrySet()) {
      String k = k_v.getKey();
      String v = k_v.getValue();
      if ((k == null && ((Object) BASE) == null || k != null && (k).equals(BASE))) {
        this.writeBase(v);
      } else {
        this.writeln(this.prefixKeyword + " " + k + ": <" + v + ">");
      }
    }
    if (this.baseIri != null) {
      this.writeBase(this.baseIri);
    }
    if (this.settings.prologueEndLine > 1) {
      this.writeln();
    }
  }

  public void writeBase(String iri) {
    this.writeln(this.baseKeyword + " <" + iri + ">");
  }

  public boolean isListContainer(String term) {
    return this.isContainer(term, LIST);
  }

  public boolean isLangContainer(String term) {
    return this.isContainer(term, LANGUAGE);
  }

  protected boolean isContainer(String term, String kind) {
    if (this.context != null) {
      Object termdef = (Object) this.context.get(term);
      if (termdef instanceof Map) {
        return (((Map) termdef).get(CONTAINER) == null && ((Object) kind) == null || ((Map) termdef).get(CONTAINER) != null && (((Map) termdef).get(CONTAINER)).equals(kind));
      }
    }
    return false;
  }

  public void writeGraph(/*@Nullable*/ String iri, Object graph) {
    this.writeGraph(iri, graph, 0);
  }
  public void writeGraph(/*@Nullable*/ String iri, Object graph, Integer depth) {
    if ((iri != null && this.settings.turtleDropNamed)) {
      return;
    }
    Boolean inGraphBlock = (iri != null || depth > 0);
    if (!(this.settings.turtleOnly)) {
      if (iri == null) {
        if (depth > 0) {
          this.writeln();
          this.writeln("{");
        }
      } else {
        this.writeln();
        if (this.graphKeyword != null) {
          this.write(this.graphKeyword + " ");
        }
        this.writeln(this.refRepr(iri) + " {");
      }
    }
    for (Object node : asList(graph)) {
      /*@Nullable*/ String via = (inGraphBlock ? this.aliases.graph : null);
      this.writeObject(((Map<String, Object>) node), depth, via);
    }
    if (!(this.settings.turtleOnly)) {
      if (inGraphBlock) {
        this.writeln();
        this.writeln("}");
      }
    }
  }

  public List<Map<String, Object>> writeObject(Object obj) {
    return this.writeObject(obj, 0);
  }
  public List<Map<String, Object>> writeObject(Object obj, Integer depth) {
    return this.writeObject(obj, depth, null);
  }
  public List<Map<String, Object>> writeObject(Object obj, Integer depth, /*@Nullable*/ String viaKey) {
    if ((depth > 0 && obj instanceof Map && ((Map) obj).containsKey(CONTEXT))) {
      throw new RuntimeException("Nested context not supported yet");
    }
    if ((viaKey != null && this.isLangContainer(viaKey) && obj instanceof Map)) {
      Boolean first = true;
      for (Map.Entry<String, Object> lang_value : ((Map<String, Object>) obj).entrySet()) {
        String lang = lang_value.getKey();
        Object value = lang_value.getValue();
        if (!(first)) {
          this.write(" , ");
        }
        this.toLiteral(Builtins.mapOf(this.aliases.value, value, this.aliases.lang, lang), viaKey);
        first = false;
      }
      return new ArrayList<>();
    }
    if ((!(obj instanceof Map) || ((Map) obj).containsKey(this.aliases.value))) {
      this.toLiteral(((Object) obj), viaKey);
      return new ArrayList<>();
    }
    Boolean explicitList = (Boolean) ((Map) obj).containsKey(this.aliases.list);
    if ((viaKey != null && this.isListContainer(viaKey))) {
      obj = Builtins.mapOf(this.aliases.list, obj);
    }
    /*@Nullable*/ String s = (/*@Nullable*/ String) ((/*@Nullable*/ String) ((Map) obj).get(this.aliases.id));
    Boolean isList = (Boolean) ((Map) obj).containsKey(this.aliases.list);
    Boolean startedList = isList;
    Boolean isBracketed = (Boolean) (isList || (viaKey == null && ((Object) this.aliases.annotation) == null || viaKey != null && (viaKey).equals(this.aliases.annotation)));
    if (((Map) obj).containsKey(this.aliases.graph)) {
      if ((s != null && this.settings.turtleDropNamed)) {
        return new ArrayList<>();
      }
      if ((((Map) obj).containsKey(CONTEXT) && depth > 0)) {
        this.prelude(collectPrefixes(((Map) obj).get(CONTEXT)));
      }
      this.writeGraph(s, ((Map) obj).get(this.aliases.graph), depth);
      return new ArrayList<>();
    }
    if (explicitList) {
      this.write("( ");
    }
    Boolean inGraph = (Boolean) ((viaKey == null && ((Object) this.aliases.graph) == null || viaKey != null && (viaKey).equals(this.aliases.graph)) && !(this.settings.turtleOnly));
    Integer inGraphAdd = (inGraph ? 1 : 0);
    if (((s != null || depth == 0) && this.hasKeys((Map) obj, 2))) {
      if (s == null) {
        this.write("[]");
      } else {
        if (depth == 0) {
          this.writeln();
        }
        if (inGraphAdd > 0) {
          this.write(this.getIndent(0));
        }
        this.write(this.refRepr(s));
      }
    } else if (depth > 0) {
      if (!(isBracketed)) {
        depth += 1;
        this.write("[");
      }
    } else {
      return new ArrayList<>();
    }
    String indent = this.getIndent(depth + inGraphAdd);
    Integer nestedDepth = depth + 1 + inGraphAdd;
    List<Map<String, Object>> topObjects = new ArrayList<>();
    Boolean first = true;
    Boolean endedList = false;
    for (Map.Entry<String, Object> key_vo : ((Map<String, Object>) obj).entrySet()) {
      String key = key_vo.getKey();
      Object vo = key_vo.getValue();
      /*@Nullable*/ String indexKey = (/*@Nullable*/ String) this.indexKeyFor(key);
      if (indexKey != null) {
        key = indexKey;
        vo = (vo instanceof Map ? new ArrayList(((Map) vo).values()) : vo);
      }
      String term = (String) this.termFor(key);
      /*@Nullable*/ String revKey = (term == null ? this.revKeyFor(key) : null);
      if ((term == null && revKey == null)) {
        continue;
      }
      if (((term == null && ((Object) this.aliases.id) == null || term != null && (term).equals(this.aliases.id)) || (term == null && ((Object) CONTEXT) == null || term != null && (term).equals(CONTEXT)))) {
        continue;
      }
      if ((term == null && ((Object) this.aliases.index) == null || term != null && (term).equals(this.aliases.index))) {
        continue;
      }
      if ((term == null && ((Object) this.aliases.annotation) == null || term != null && (term).equals(this.aliases.annotation))) {
        continue;
      }
      List vs = (vo instanceof List ? (List) vo : (vo != null ? new ArrayList<>(Arrays.asList(new Object[] {(Object) vo})) : new ArrayList<>()));
      vs = ((List) vs.stream().filter((x) -> x != null).collect(Collectors.toList()));
      if (vs.size() == 0) {
        continue;
      }
      Boolean inList = (isList || this.isListContainer(key));
      /*@Nullable*/ Map<String, Object> revContainer = null;
      if ((term == null && ((Object) this.aliases.reverse) == null || term != null && (term).equals(this.aliases.reverse))) {
        revContainer = ((/*@Nullable*/ Map<String, Object>) ((Map) obj).get(key));
      } else if (revKey != null) {
        revContainer = (Map<String, Object>) Builtins.mapOf(revKey, ((Map) obj).get(key));
      }
      if (revContainer != null) {
        for (Map.Entry<String, Object> revkey_rvo : revContainer.entrySet()) {
          String revkey = revkey_rvo.getKey();
          Object rvo = revkey_rvo.getValue();
          vs = (rvo instanceof List ? (List) rvo : (rvo != null ? new ArrayList<>(Arrays.asList(new Object[] {(Object) rvo})) : new ArrayList<>()));
          for (Object x : vs) {
            topObjects.add(this.makeTopObject(s, revkey, ((Map<String, Object>) x)));
          }
        }
      } else {
        String useIndent = indent;
        if (first) {
          useIndent = " ";
          first = false;
        } else {
          if ((startedList && !(inList) && !(endedList))) {
            endedList = true;
            this.write(" )");
          }
          this.writeln(" ;");
        }
        assert term instanceof String;
        if ((term == null && ((Object) this.aliases.type) == null || term != null && (term).equals(this.aliases.type))) {
          term = "a";
        }
        if (!term.equals(LIST)) {
          term = (String) this.toValidTerm((String) term);
          this.write(useIndent + term + " ");
        }
        for (int i = 0; i < vs.size(); i++) {
          Object v = (Object) vs.get(i);
          if (inList) {
            if (!(startedList)) {
              this.write("(");
              startedList = true;
            }
            this.write(" ");
          } else if (i > 0) {
            if (this.settings.predicateRepeatNewLine) {
              this.writeln(" ,");
              this.write(this.getIndent(nestedDepth));
            } else {
              this.write(" , ");
            }
          }
          if ((this.bnodeSkolemBase != null && v instanceof Map && !((Map) v).containsKey(this.aliases.id))) {
            s = (String) this.genSkolemId();
            ((Map) v).put(this.aliases.id, s);
          }
          if ((term == null && ((Object) "a") == null || term != null && (term).equals("a"))) {
            String t = (String) this.reprType(((Object) v));
            this.write(t);
          } else if ((v != null && v instanceof Map && ((Map) v).containsKey(this.aliases.id))) {
            topObjects.add((Map) v);
            this.write(this.refRepr(((Map) v).get(this.aliases.id)));
          } else if (v != null) {
            List<Map<String, Object>> objects = this.writeObject(v, nestedDepth, key);
            for (Map<String, Object> it : objects) {
              topObjects.add(it);
            }
          }
          this.writeAnnotation(v, depth);
        }
      }
    }
    if ((explicitList || ((!(isList) && startedList) && !(endedList)))) {
      this.write(" )");
    }
    if (depth == 0) {
      if (!(first)) {
        this.writeln(" .");
      }
      for (Map<String, Object> it : topObjects) {
        this.writeObject(it, depth, viaKey);
      }
      return new ArrayList<>();
    } else {
      indent = this.getIndent(nestedDepth - 1 + inGraphAdd);
      if (this.settings.bracketEndNewLine) {
        this.writeln();
        this.write(indent);
      } else {
        this.write(" ");
      }
      if (!(isBracketed)) {
        this.write("]");
      }
      return topObjects;
    }
  }

  public void writeAnnotation(Object v, Integer depth) {
    if (this.settings.dropRdfstar) {
      return;
    }
    if ((v instanceof Map && ((Map) v).containsKey(this.aliases.annotation))) {
      Map<String, Object> annotation = (Map<String, Object>) ((Map) v).get(this.aliases.annotation);
      if (annotation != null) {
        this.write(" {|");
        this.writeObject(annotation, depth + 2, this.aliases.annotation);
        this.write("|}");
      }
    }
  }

  public void toLiteral(Object obj) {
    this.toLiteral(obj, null);
  }
  public void toLiteral(Object obj, /*@Nullable*/ String viaKey) {
    this.toLiteral(obj, viaKey, null);
  }
  public void toLiteral(Object obj, /*@Nullable*/ String viaKey, Function write) {
    if (write == null) {
      write = (s) -> this.write(s);
    }
    Object value = obj;
    /*@Nullable*/ Object lang = (/*@Nullable*/ Object) this.context.get(LANGUAGE);
    /*@Nullable*/ String datatype = null;
    if (obj instanceof Map) {
      value = ((Map) obj).get(this.aliases.value);
      datatype = ((String) ((Map) obj).get(this.aliases.type));
      lang = ((Map) obj).get(this.aliases.lang);
    } else {
      /*@Nullable*/ Object kdef = null;
      if ((viaKey != null && this.context.containsKey(viaKey))) {
        kdef = ((Object) this.context.get(viaKey));
      }
      /*@Nullable*/ String coerceTo = null;
      if ((kdef instanceof Map && ((Map) kdef).containsKey(TYPE))) {
        coerceTo = ((String) ((Map) kdef).get(TYPE));
      }
      if ((coerceTo == null && ((Object) VOCAB) == null || coerceTo != null && (coerceTo).equals(VOCAB))) {
        Boolean next = false;
        for (Object v : asList(value)) {
          if (next) {
            write(" , ");
          } else {
            next = true;
          }
          write((v instanceof String ? this.refRepr((String) v, true) : this.toStr(v)));
        }
        return;
      } else if ((coerceTo == null && ((Object) ID) == null || coerceTo != null && (coerceTo).equals(ID))) {
        Boolean next = false;
        for (Object v : asList(value)) {
          if (next) {
            write(" , ");
          } else {
            next = true;
          }
          write(this.refRepr(v));
        }
        return;
      } else if (coerceTo != null) {
        datatype = coerceTo;
      } else if ((kdef instanceof Map && ((Map) kdef).containsKey(LANGUAGE))) {
        lang = ((Map) kdef).get(LANGUAGE);
      }
    }
    Boolean next = false;
    for (Object v : asList(value)) {
      if (next) {
        write(" , ");
      } else {
        next = true;
      }
      write(this.toStr(v, datatype, lang));
    }
  }

  public String toStr(Object v) {
    return this.toStr(v, null);
  }
  public String toStr(Object v, /*@Nullable*/ String datatype) {
    return this.toStr(v, datatype, null);
  }
  public String toStr(Object v, /*@Nullable*/ String datatype, /*@Nullable*/ Object lang) {
    if (v instanceof String) {
      List<String> parts = new ArrayList<>();
      String escaped = (String) ((String) v).replace("\\", "\\\\");
      String quote = "\"";
      if (escaped.indexOf("\n") > -1) {
        quote = "\"\"\"";
        if (escaped.endsWith("\"")) {
          escaped = escaped.substring(0, escaped.length() - 1) + "\\\"";
        }
      } else {
        escaped = escaped.replace("\"", "\\\"");
      }
      parts.add(quote);
      parts.add(escaped);
      parts.add(quote);
      if (datatype != null) {
        parts.add("^^" + this.toValidTerm(((String) this.termFor(datatype))));
      } else if (lang instanceof String) {
        parts.add("@" + lang);
      }
      return String.join("", parts);
    } else if (v instanceof Boolean) {
      return ((Boolean) v ? "true" : "false");
    } else {
      return v.toString();
    }
  }

  public /*@Nullable*/ String termFor(String key) {
    if (key.startsWith("@")) {
      return key;
    } else if ((key.indexOf(":") > -1 || key.indexOf("/") > -1 || key.indexOf("#") > -1)) {
      return key;
    } else if (this.context.containsKey(key)) {
      Object kdef = (Object) this.context.get(key);
      if (kdef == null) {
        return null;
      }
      Object term = null;
      if (kdef instanceof Map) {
        term = ((Map) kdef).getOrDefault(ID, key);
      } else {
        term = kdef;
      }
      assert term instanceof String;
      Integer ci = (Integer) ((String) term).indexOf(":");
      return (ci == -1 ? ":" + term : (String) term);
    } else {
      return ":" + key;
    }
  }

  public /*@Nullable*/ String revKeyFor(String key) {
    Object kdef = (Object) this.context.get(key);
    if ((kdef instanceof Map && ((Map) kdef).containsKey(REVERSE))) {
      return ((String) ((Map) kdef).get(REVERSE));
    }
    return null;
  }

  public /*@Nullable*/ String indexKeyFor(String key) {
    Object kdef = (Object) this.context.get(key);
    if ((kdef instanceof Map && (((Map) kdef).get(CONTAINER) == null && ((Object) INDEX) == null || ((Map) kdef).get(CONTAINER) != null && (((Map) kdef).get(CONTAINER)).equals(INDEX)))) {
      return ((String) ((Map) kdef).getOrDefault(ID, key));
    }
    return null;
  }

  public Map<String, Object> makeTopObject(/*@Nullable*/ String s, String revKey, Map it) {
    Map node = new HashMap(it);
    if (!node.containsKey(this.aliases.id)) {
      node.put(this.aliases.id, "_:bnode-" + this.bnodeCounter);
      this.bnodeCounter += 1;
    }
    node.put(revKey, Builtins.mapOf(this.aliases.id, s));
    return node;
  }

  public String reprType(Object t) {
    String tstr = (String) (t instanceof String ? (String) t : ((String) ((Map) t).get(TYPE)));
    return this.toValidTerm(((String) this.termFor(tstr)));
  }

  public String refRepr(/*@Nullable*/ Object refobj) {
    return this.refRepr(refobj, false);
  }
  public String refRepr(/*@Nullable*/ Object refobj, Boolean useVocab) {
    if (refobj == null) {
      return "[]";
    }
    if ((refobj instanceof Map && ((Map) refobj).containsKey(this.aliases.id))) {
      return this.reprTriple((Map) refobj);
    }
    String ref = (String) ((String) refobj);
    Integer c_i = (Integer) ref.indexOf(":");
    if (c_i > -1) {
      String pfx = ref.substring(0, c_i);
      if ((pfx == null && ((Object) "_") == null || pfx != null && (pfx).equals("_"))) {
        String nodeId = ref + this.uniqueBnodeSuffix;
        if (this.bnodeSkolemBase != null) {
          ref = this.bnodeSkolemBase + nodeId.substring(2);
        } else {
          return this.toValidTerm(nodeId);
        }
      } else if (this.context.containsKey(pfx)) {
        String local = ref.substring(c_i + 1);
        return pfx + ":" + this.escapePnameLocal(local);
      }
    } else if ((useVocab && ref.indexOf("/") == -1)) {
      return ":" + ref;
    }
    if ((this.context.containsKey(VOCAB) && ref.startsWith(((String) this.context.get(VOCAB))))) {
      return ":" + ref.substring(((String) this.context.get(VOCAB)).length());
    }
    ref = (String) this.cleanValue(ref);
    c_i = (Integer) ref.indexOf(":");
    if (c_i > -1) {
      String pfx = ref.substring(0, c_i);
      String rest = (String) ref.substring(c_i);
      if (this.context.containsKey(pfx)) {
        return ref;
      }
      if ((this.context.size() > 0 && rest.indexOf(":") == -1 && (WORD_START.matcher(rest).matches() ? rest : null) != null && (WORD_START.matcher(pfx).matches() ? pfx : null) != null)) {
        return ref;
      }
    }
    return "<" + ref + ">";
  }

  public String reprTriple(Map<String, Object> ref) {
    if (this.settings.dropRdfstar) {
      throw new RuntimeException("Triple nodes disallowed unless in RDF-star mode");
    }
    String s = (String) this.refRepr(((String) ref.get(this.aliases.id)));
    String p = "";
    Object obj = "";
    for (String k : ref.keySet()) {
      if ((k == null && ((Object) this.aliases.id) == null || k != null && (k).equals(this.aliases.id))) {
        continue;
      }
      if (!p.equals("")) {
        throw new RuntimeException("Quoted triples cannot contain multiple statements");
      }
      p = ((String) this.termFor(k));
      obj = ((Map<String, Object>) ref.get(k));
    }
    String o;
    if ((p == null && ((Object) this.aliases.type) == null || p != null && (p).equals(this.aliases.type))) {
      p = "a";
      o = (String) this.reprType(obj);
    } else {
      if (obj instanceof List) {
        throw new RuntimeException("Quoted triples must have one single object");
      }
      if ((this.isLangContainer(p) && obj instanceof Map)) {
        throw new RuntimeException("Language containers not yet supported in quoted triples");
      }
      if ((obj instanceof Map && ((Map) obj).containsKey(this.aliases.list))) {
        throw new RuntimeException("Quoted triples cannot contain Lists");
      }
      if ((!(obj instanceof Map) || ((Map) obj).containsKey(this.aliases.value))) {
        List<String> l = new ArrayList<>();
        this.toLiteral((Map) obj, p, (x) -> l.add(((String) x)));
        o = String.join("", l);
      } else {
        assert (obj instanceof Map && ((Map) obj).containsKey(this.aliases.id));
        o = (String) this.refRepr(((String) ((Map) obj).get(this.aliases.id)));
      }
    }
    return "<< " + s + " " + p + " " + o + " >>";
  }

  public String toValidTerm(String term) {
    term = (String) this.cleanValue(term);
    Integer c_i = (Integer) term.indexOf(":");
    /*@Nullable*/ String pfx = (c_i > -1 ? term.substring(0, c_i) : null);
    if ((!(this.context.containsKey(pfx)) && (term.indexOf("/") > -1 || term.indexOf("#") > -1 || (pfx != null && term.lastIndexOf(":") > pfx.length())))) {
      return "<" + term + ">";
    }
    if (pfx != null) {
      String local = term.substring(c_i + 1);
      return pfx + ":" + this.escapePnameLocal(local);
    }
    return this.escapePnameLocal(term);
  }

  public boolean hasKeys(Map<String, Object> obj) {
    return this.hasKeys(obj, 1);
  }
  public boolean hasKeys(Map<String, Object> obj, Integer atLeast) {
    Integer seen = 0;
    for (String k : obj.keySet()) {
      if (!k.equals(this.aliases.annotation)) {
        seen += 1;
        if ((seen == null && ((Object) atLeast) == null || seen != null && (seen).equals(atLeast))) {
          return true;
        }
      }
    }
    return false;
  }

  public String cleanValue(String v) {
    return v;
  }

  public String escapePnameLocal(String pnlocal) {
    return (PNAME_LOCAL_ESC.matcher(pnlocal).replaceAll("\\\\$1"));
  }

  public /*@Nullable*/ String genSkolemId() {
    if (this.bnodeSkolemBase == null) {
      return null;
    }
    return this.bnodeSkolemBase + uuid4();
  }

  public String getIndent(Integer depth) {
    List<String> chunks = new ArrayList<>();
    Integer i = -1;
    while (i < depth) {
      i += 1;
      chunks.add(this.settings.indentChars);
    }
    return String.join("", chunks);
  }

  public void write(String s) {
    this.out.write((s != null ? s : ""));
  }

  public void writeln() {
    this.writeln(null);
  }
  public void writeln(/*@Nullable*/ String s) {
    this.out.write((s != null ? s : "") + "\n");
  }

  protected /*@Nullable*/ String write(Object s) {
    this.write(((String) s));
    return null;
  }
}
