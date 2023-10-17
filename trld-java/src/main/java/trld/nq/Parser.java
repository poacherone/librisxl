/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/nq/parser.py
 */
package trld.nq;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;

import trld.Input;
import static trld.Common.dumpJson;
import trld.jsonld.RdfDataset;
import trld.jsonld.RdfGraph;
import trld.jsonld.RdfTriple;
import trld.jsonld.RdfLiteral;
import static trld.jsonld.Rdf.toJsonld;
import static trld.trig.Parser.ESC_CHARS;
import trld.trig.ReadTerm;

public class Parser {
  public static final Integer READ_STMT = 0;
  public static final Integer READ_IRI = 1;
  public static final Integer READ_BNODE_ID = 2;
  public static final Integer READ_STRING = 3;
  public static final Integer READ_LITERAL_END = 4;
  public static final Integer READ_DATATYPE_START = 5;
  public static final Integer READ_DATATYPE_NEXT = 6;
  public static final Integer READ_DATATYPE_IRI = 7;
  public static final Integer READ_LANGUAGE = 8;
  public static final Integer READ_LITERAL_FINISH = 9;
  public static final Integer READ_COMMENT = 10;
  public static final ReadTerm READ_ESCAPES = new ReadTerm(null);

  public static void load(RdfDataset dataset, Input inp) {
    Object state = (Object) READ_STMT;
    Object prevState = -1;
    List<String> chars = new ArrayList<>();
    /*@Nullable*/ String literal = null;
    /*@Nullable*/ String datatype = null;
    /*@Nullable*/ String language = null;
    List<Object> terms = new ArrayList<>();
    for (String c : ((Iterable<String>) inp.characters())) {
      if (READ_ESCAPES.handleEscape(c)) {
        if (state != READ_ESCAPES) {
          READ_ESCAPES.escapeChars = (READ_STRING > 0 ? ESC_CHARS : new HashMap<>());
          prevState = state;
        }
        state = (Object) READ_ESCAPES;
      }
      if ((state == null && ((Object) READ_LITERAL_FINISH) == null || state != null && (state).equals(READ_LITERAL_FINISH))) {
        assert literal != null;
        terms.add(new RdfLiteral(literal, datatype, language));
        literal = datatype = language = null;
        state = (Object) READ_STMT;
      }
      if ((state == null && ((Object) READ_ESCAPES) == null || state != null && (state).equals(READ_ESCAPES))) {
        if (READ_ESCAPES.collected.size() == 1) {
          c = (String) READ_ESCAPES.pop();
          state = prevState;
        } else {
          continue;
        }
      } else if ((state == null && ((Object) READ_STMT) == null || state != null && (state).equals(READ_STMT))) {
        if (c.matches("^\\s+$")) {
          continue;
        } else if ((c == null && ((Object) "<") == null || c != null && (c).equals("<"))) {
          state = (Object) READ_IRI;
          continue;
        } else if ((c == null && ((Object) "_") == null || c != null && (c).equals("_"))) {
          state = (Object) READ_BNODE_ID;
        } else if ((c == null && ((Object) "\"") == null || c != null && (c).equals("\""))) {
          state = (Object) READ_STRING;
          continue;
        } else if ((c == null && ((Object) ".") == null || c != null && (c).equals("."))) {
          handleStatement(dataset, terms);
          terms = new ArrayList<>();
          continue;
        } else if ((c == null && ((Object) "#") == null || c != null && (c).equals("#"))) {
          state = (Object) READ_COMMENT;
          continue;
        }
      } else if (((state == null && ((Object) READ_IRI) == null || state != null && (state).equals(READ_IRI)) || (state == null && ((Object) READ_DATATYPE_IRI) == null || state != null && (state).equals(READ_DATATYPE_IRI)))) {
        if ((c == null && ((Object) ">") == null || c != null && (c).equals(">"))) {
          String s = String.join("", chars);
          if ((state == null && ((Object) READ_IRI) == null || state != null && (state).equals(READ_IRI))) {
            terms.add(s);
            state = (Object) READ_STMT;
          } else {
            datatype = s;
            state = (Object) READ_LITERAL_FINISH;
          }
          chars = new ArrayList<>();
          continue;
        }
      } else if ((state == null && ((Object) READ_BNODE_ID) == null || state != null && (state).equals(READ_BNODE_ID))) {
        if (c.matches("^\\s+$")) {
          terms.add(String.join("", chars));
          chars = new ArrayList<>();
          state = (Object) READ_STMT;
          continue;
        }
      } else if ((state == null && ((Object) READ_STRING) == null || state != null && (state).equals(READ_STRING))) {
        if ((c == null && ((Object) "\"") == null || c != null && (c).equals("\""))) {
          literal = String.join("", chars);
          chars = new ArrayList<>();
          state = (Object) READ_LITERAL_END;
          continue;
        }
      } else if ((state == null && ((Object) READ_LITERAL_END) == null || state != null && (state).equals(READ_LITERAL_END))) {
        if ((c == null && ((Object) "@") == null || c != null && (c).equals("@"))) {
          state = (Object) READ_LANGUAGE;
          continue;
        } else if ((c == null && ((Object) "^") == null || c != null && (c).equals("^"))) {
          state = (Object) READ_DATATYPE_START;
          continue;
        } else {
          state = (Object) READ_LITERAL_FINISH;
          continue;
        }
      } else if ((state == null && ((Object) READ_LANGUAGE) == null || state != null && (state).equals(READ_LANGUAGE))) {
        if (c.matches("^\\s+$")) {
          language = String.join("", chars);
          chars = new ArrayList<>();
          state = (Object) READ_LITERAL_FINISH;
          continue;
        }
      } else if ((state == null && ((Object) READ_DATATYPE_START) == null || state != null && (state).equals(READ_DATATYPE_START))) {
        if ((c == null && ((Object) "^") == null || c != null && (c).equals("^"))) {
          state = (Object) READ_DATATYPE_NEXT;
          continue;
        } else {
          throw new RuntimeException("Bad READ_DATATYPE_START char: " + c);
        }
      } else if ((state == null && ((Object) READ_DATATYPE_NEXT) == null || state != null && (state).equals(READ_DATATYPE_NEXT))) {
        if ((c == null && ((Object) "<") == null || c != null && (c).equals("<"))) {
          state = (Object) READ_DATATYPE_IRI;
          continue;
        } else {
          throw new RuntimeException("Bad READ_DATATYPE_NEXT char: " + c);
        }
      } else if ((state == null && ((Object) READ_COMMENT) == null || state != null && (state).equals(READ_COMMENT))) {
        if ((c == null && ((Object) "\n") == null || c != null && (c).equals("\n"))) {
          state = (Object) READ_STMT;
        }
        continue;
      }
      chars.add(c);
    }
    if ((chars.size() != 0 || terms.size() != 0)) {
      throw new RuntimeException("Trailing data: chars=" + String.join("", chars) + ", terms=" + terms);
    }
  }

  public static void handleStatement(RdfDataset dataset, List terms) {
    if ((terms.size() < 3 || terms.size() > 4)) {
      throw new RuntimeException("Invalid NQuads statement " + terms.toString());
    }
    String s = (String) terms.get(0);
    String p = (String) terms.get(1);
    Object o = (Object) terms.get(2);
    /*@Nullable*/ String g = (/*@Nullable*/ String) (terms.size() == 4 ? ((String) terms.get(3)) : null);
    RdfGraph graph;
    if (g == null) {
      graph = (RdfGraph) dataset.defaultGraph;
      if (graph == null) {
        graph = dataset.defaultGraph = new RdfGraph();
      }
    } else {
      if (!dataset.namedGraphs.containsKey(g)) {
        dataset.namedGraphs.put(g, new RdfGraph(g));
      }
      graph = (RdfGraph) dataset.namedGraphs.get(g);
    }
    graph.add(new RdfTriple(s, p, o));
  }

  public static Object parse(Input inp) {
    RdfDataset dataset = new RdfDataset();
    load(dataset, inp);
    return toJsonld(dataset);
  }
}
