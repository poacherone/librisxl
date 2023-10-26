/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/jsonld/rdf.py
 */
package trld.jsonld;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;

import static trld.platform.Common.jsonEncodeCanonical;
import static trld.platform.Common.jsonDecode;
import static trld.jsonld.Base.*;
import trld.jsonld.InvalidBaseDirectionError;
import trld.jsonld.InvalidLanguageTaggedStringError;
import trld.jsonld.BNodes;
import static trld.jsonld.Flattening.makeNodeMap;
import static trld.Rdfterms.RDF_TYPE;
import static trld.Rdfterms.RDF_VALUE;
import static trld.Rdfterms.RDF_LIST;
import static trld.Rdfterms.RDF_FIRST;
import static trld.Rdfterms.RDF_REST;
import static trld.Rdfterms.RDF_NIL;
import static trld.Rdfterms.RDF_DIRECTION;
import static trld.Rdfterms.RDF_LANGUAGE;
import static trld.Rdfterms.RDF_JSON;
import static trld.Rdfterms.RDF_LANGSTRING;
import static trld.Rdfterms.XSD_BOOLEAN;
import static trld.Rdfterms.XSD_DOUBLE;
import static trld.Rdfterms.XSD_INTEGER;
import static trld.Rdfterms.XSD_STRING;
import static trld.Rdfterms.I18N;
import static trld.jsonld.Rdf.*;


public class RdfGraph {
  public /*@Nullable*/ String name;
  public List<RdfTriple> triples;

  public RdfGraph() {
    this(null);
  }
  public RdfGraph(/*@Nullable*/ String name) {
    this.name = name;
    this.triples = new ArrayList<>();
  }

  public void add(RdfTriple triple) {
    this.triples.add(triple);
  }
}
