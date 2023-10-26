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

import static trld.platform.Common.uuid4;
import trld.platform.Output;
import static trld.jsonld.Base.BASE;
import static trld.jsonld.Base.CONTAINER;
import static trld.jsonld.Base.CONTEXT;
import static trld.jsonld.Base.GRAPH;
import static trld.jsonld.Base.ID;
import static trld.jsonld.Base.INDEX;
import static trld.jsonld.Base.LANGUAGE;
import static trld.jsonld.Base.LIST;
import static trld.jsonld.Base.NONE;
import static trld.jsonld.Base.PREFIX;
import static trld.jsonld.Base.PREFIX_DELIMS;
import static trld.jsonld.Base.REVERSE;
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.VALUE;
import static trld.jsonld.Base.VOCAB;
import static trld.jsonld.Star.ANNOTATION;
import static trld.jsonld.Star.ANNOTATED_TYPE_KEY;
import static trld.trig.Serializer.*;


public class KeyAliases {
  public String id = ID;
  public String value = VALUE;
  public String type = TYPE;
  public String lang = LANGUAGE;
  public String graph = GRAPH;
  public String list = LIST;
  public String reverse = REVERSE;
  public String index = INDEX;
  public String annotation = ANNOTATION;
  public KeyAliases() {
    this(ID, VALUE, TYPE, LANGUAGE, GRAPH, LIST, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id) {
    this(id, VALUE, TYPE, LANGUAGE, GRAPH, LIST, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value) {
    this(id, value, TYPE, LANGUAGE, GRAPH, LIST, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type) {
    this(id, value, type, LANGUAGE, GRAPH, LIST, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type, String lang) {
    this(id, value, type, lang, GRAPH, LIST, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type, String lang, String graph) {
    this(id, value, type, lang, graph, LIST, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type, String lang, String graph, String list) {
    this(id, value, type, lang, graph, list, REVERSE, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type, String lang, String graph, String list, String reverse) {
    this(id, value, type, lang, graph, list, reverse, INDEX, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type, String lang, String graph, String list, String reverse, String index) {
    this(id, value, type, lang, graph, list, reverse, index, ANNOTATION);
  }
  public KeyAliases(String id, String value, String type, String lang, String graph, String list, String reverse, String index, String annotation) {
    this.id = id;
    this.value = value;
    this.type = type;
    this.lang = lang;
    this.graph = graph;
    this.list = list;
    this.reverse = reverse;
    this.index = index;
    this.annotation = annotation;
  }
}
