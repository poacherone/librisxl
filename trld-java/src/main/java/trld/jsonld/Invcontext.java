/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/jsonld/invcontext.py
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

import static trld.jsonld.Base.*;
import trld.jsonld.Context;
import trld.jsonld.Term;



public class Invcontext {
  public static Map getInverseContext(Context activeContext) {
    if (activeContext.inverseContext == null) {
      activeContext.inverseContext = (Map) createInverseContext(activeContext);
    }
    return activeContext.inverseContext;
  }
  public static Map<String, Object> createInverseContext(Context activeContext) {
    Map<String, Object> result = new HashMap<>();
    String defaultLanguage = (activeContext.defaultLanguage != null ? activeContext.defaultLanguage : NONE);
    List</*@Nullable*/ String> termKeys = new ArrayList(activeContext.terms.keySet());
    Collections.sort(termKeys, Builtins.makeComparator((s) -> new KeyValue((s != null ? ((String) s).length() : 0), s), false));
    for (String termKey : termKeys) {
      assert termKey instanceof String;
      /*@Nullable*/ Term termDfn = (/*@Nullable*/ Term) activeContext.terms.get(termKey);
      if (termDfn == null) {
        continue;
      }
      String container = NONE;
      if (termDfn.container.size() > 0) {
        container = String.join("", Builtins.sorted(termDfn.container));
      }
      String iri = (String) termDfn.iri;
      if (!result.containsKey(iri)) {
        result.put(iri, new HashMap<>());
      }
      Map<String, Object> containerMap = ((Map<String, Object>) result.get(iri));
      if (!containerMap.containsKey(container)) {
        containerMap.put(container, Builtins.mapOf(LANGUAGE, new HashMap<>(), TYPE, new HashMap<>(), ANY, Builtins.mapOf(NONE, termKey)));
      }
      Map<String, Object> typelanguageMap = ((Map<String, Object>) containerMap.get(container));
      Map<String, Object> typeMap = ((Map<String, Object>) typelanguageMap.get(TYPE));
      Map<String, Object> languageMap = ((Map<String, Object>) typelanguageMap.get(LANGUAGE));
      String langDir;
      if (termDfn.isReverseProperty) {
        if (!typeMap.containsKey(REVERSE)) {
          typeMap.put(REVERSE, (String) termKey);
        }
      } else if ((termDfn.typeMapping == null && ((Object) NONE) == null || termDfn.typeMapping != null && (termDfn.typeMapping).equals(NONE))) {
        if (!languageMap.containsKey(ANY)) {
          languageMap.put(ANY, (String) termKey);
        }
        if (!typeMap.containsKey(ANY)) {
          typeMap.put(ANY, (String) termKey);
        }
      } else if (termDfn.typeMapping != null) {
        if (!typeMap.containsKey(termDfn.typeMapping)) {
          typeMap.put(termDfn.typeMapping, (String) termKey);
        }
      } else if ((termDfn.language != null && termDfn.direction != null)) {
        langDir = NULL;
        if ((termDfn.language != NULL && termDfn.direction != NULL)) {
          langDir = termDfn.language + "_" + termDfn.direction;
        } else if (termDfn.language != NULL) {
          langDir = (String) termDfn.language;
        } else if (termDfn.direction != NULL) {
          langDir = (String) termDfn.direction;
        }
        if (!languageMap.containsKey(langDir)) {
          languageMap.put(langDir, (String) termKey);
        }
      } else if (termDfn.language != null) {
        String language = (String) termDfn.language;
        if (!languageMap.containsKey(language)) {
          languageMap.put(language, (String) termKey);
        }
      } else if (termDfn.direction != null) {
        String direction = (String) termDfn.direction;
        if ((direction == null && ((Object) NULL) == null || direction != null && (direction).equals(NULL))) {
          direction = NONE;
        } else {
          direction = "_" + direction;
        }
        if (!languageMap.containsKey(direction)) {
          languageMap.put(direction, (String) termKey);
        }
      } else if (activeContext.defaultBaseDirection != null) {
        langDir = activeContext.defaultLanguage + "_" + activeContext.defaultBaseDirection;
        if (!languageMap.containsKey(langDir)) {
          languageMap.put(langDir, (String) termKey);
        }
        if (!languageMap.containsKey(NONE)) {
          languageMap.put(NONE, (String) termKey);
        }
        if (!typeMap.containsKey(NONE)) {
          typeMap.put(NONE, (String) termKey);
        }
      } else {
        if ((activeContext.defaultLanguage != null && !languageMap.containsKey(activeContext.defaultLanguage))) {
          languageMap.put(activeContext.defaultLanguage, (String) termKey);
        }
        if (!languageMap.containsKey(NONE)) {
          languageMap.put(NONE, (String) termKey);
        }
        if (!typeMap.containsKey(NONE)) {
          typeMap.put(NONE, (String) termKey);
        }
      }
    }
    return result;
  }
}
