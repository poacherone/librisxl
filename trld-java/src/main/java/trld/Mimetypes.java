/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/mimetypes.py
 */
package trld;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;


public class Mimetypes {
  public static final Map<String, String> SUFFIX_MIME_TYPE_MAP = Builtins.mapOf("ttl", "text/turtle", "trig", "application/trig", "jsonld", "application/ld+json", "xml", "application/rdf+xml", "rdf", "application/rdf+xml", "rdfs", "application/rdf+xml", "owl", "application/rdf+xml", "html", "text/html");

  public static /*@Nullable*/ String guessMimeType(String ref) {
    Integer i = (Integer) ref.lastIndexOf(".");
    if (i == -1) {
      return null;
    }
    String suffix = ref.substring(i + 1);
    return SUFFIX_MIME_TYPE_MAP.get(suffix);
  }
}
