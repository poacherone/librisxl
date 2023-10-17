/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/tvm/mapmaker.py
 */
package trld.tvm;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;

import static trld.jsonld.Base.CONTEXT;
import static trld.jsonld.Base.GRAPH;
import static trld.jsonld.Base.ID;
import static trld.jsonld.Base.LIST;
import static trld.jsonld.Base.REVERSE;
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.VOCAB;
import static trld.jsonld.Base.asList;
import static trld.jsonld.extras.Index.makeIndex;

public class Mapmaker {
  public static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  static String RDF_Property = RDF + "Property";
  static String RDF_Statement = RDF + "Statement";
  static String RDF_subject = RDF + "subject";
  static String RDF_predicate = RDF + "predicate";
  static String RDF_object = RDF + "object";
  public static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
  static String RDFS_Class = RDFS + "Class";
  static String RDFS_subClassOf = RDFS + "subClassOf";
  static String RDFS_subPropertyOf = RDFS + "subPropertyOf";
  static String RDFS_domain = RDFS + "domain";
  static String RDFS_range = RDFS + "range";
  public static final String OWL = "http://www.w3.org/2002/07/owl#";
  static String OWL_Class = OWL + "Class";
  static String OWL_Datatype = OWL + "Datatype";
  static String OWL_ObjectProperty = OWL + "ObjectProperty";
  static String OWL_DatatypeProperty = OWL + "DatatypeProperty";
  static String OWL_Restriction = OWL + "Restriction";
  static String OWL_equivalentClass = OWL + "equivalentClass";
  static String OWL_equivalentProperty = OWL + "equivalentProperty";
  static String OWL_inverseOf = OWL + "inverseOf";
  static String OWL_propertyChainAxiom = OWL + "propertyChainAxiom";
  static String OWL_onProperty = OWL + "onProperty";
  static String OWL_hasValue = OWL + "hasValue";
  static String OWL_allValuesFrom = OWL + "allValuesFrom";
  public static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  static String SKOS_broadMatch = SKOS + "broadMatch";
  static String SKOS_closeMatch = SKOS + "closeMatch";
  static String SKOS_exactMatch = SKOS + "exactMatch";
  static String SKOS_narrowMatch = SKOS + "narrowMatch";
  static String SKOS_mappingRelation = SKOS + "mappingRelation";
  public static final Set<String> SYMMETRIC = new HashSet(new ArrayList<>(Arrays.asList(new String[] {(String) OWL_equivalentClass, OWL_equivalentProperty, SKOS_closeMatch, SKOS_exactMatch})));

  public static Map makeTargetMap(Object vocab, Object target) {
    Map<String, Object> targetDfn = new LinkedHashMap();
    if (target instanceof String) {
      targetDfn.put(VOCAB, (String) target);
    } else {
      if (target instanceof Map) {
        target = ((Map) target).get(CONTEXT);
      }
      for (Object dfn : asList(target)) {
        targetDfn.putAll(((Map) dfn));
      }
    }
    List<Map<String, Object>> graph = (List<Map<String, Object>>) (vocab instanceof List ? (List) vocab : ((List) ((Map) vocab).get(GRAPH)));
    Map<String, Map<String, Object>> vocabIndex = (Map<String, Map<String, Object>>) makeIndex(graph);
    Map<String, Object> targetMap = new HashMap<>();
    for (Map<String, Object> obj : graph) {
      /*@Nullable*/ String id = ((/*@Nullable*/ String) obj.get(ID));
      processClassRelations(obj, vocabIndex, targetDfn, targetMap);
      processPropertyRelations(obj, vocabIndex, targetDfn, targetMap);
      processReifiedForms(obj, vocabIndex, targetMap);
    }
    Map<String, String> identityMap = new HashMap<>();
    for (Map.Entry<String, Object> key_rule : targetMap.entrySet()) {
      String key = key_rule.getKey();
      Object rule = key_rule.getValue();
      List<Map.Entry<Integer, Object>> rules = (List<Map.Entry<Integer, Object>>) Builtins.sorted(asList(rule), (it) -> new KeyValue(((Integer) ((Map.Entry) it).getKey()), (((Map.Entry) it).getValue() instanceof Map ? ((Map) ((Map.Entry) it).getValue()).get("match") != null : false)), true);
      targetMap.put(key, rules.stream().map((priority_it) -> priority_it.getValue()).collect(Collectors.toList()));
      for (Map.Entry<Integer, Object> priority_it : rules) {
        Integer priority = priority_it.getKey();
        Object it = priority_it.getValue();
        if (it instanceof String) {
          identityMap.put((String) it, (String) it);
        }
        break;
      }
    }
    targetMap.putAll(identityMap);
    return targetMap;
  }

  protected static void processClassRelations(Map obj, Map vocabIndex, Map<String, Object> target, Map targetMap) {
    List<String> rels = new ArrayList<>(Arrays.asList(new String[] {(String) OWL_equivalentClass, RDFS_subClassOf}));
    List<BaseRelation> baseRels = new ArrayList<>();
    /*@Nullable*/ String id = ((/*@Nullable*/ String) obj.get(ID));
    Integer idTargetPrio = 0;
    if (id != null) {
      idTargetPrio = getTargetPriority(target, id);
      if (idTargetPrio > 0) {
        baseRels.add(new BaseRelation(null, id, idTargetPrio));
      }
    }
    List<Map.Entry</*@Nullable*/ String, Map>> candidates = collectCandidates(obj, rels);
    Set<String> seenCandidates = new HashSet();
    while ((candidates != null && candidates.size() > 0)) {
      Map.Entry<String, Map> crel_candidate = candidates.remove(0);
      String crel = crel_candidate.getKey();
      Map candidate = crel_candidate.getValue();
      if (!candidate.containsKey(ID)) {
        continue;
      }
      String candidateId = (String) candidate.get(ID);
      candidate = ((Map) vocabIndex.getOrDefault(candidateId, candidate));
      Integer targetPrio = getTargetPriority(target, candidateId);
      if (targetPrio > 0) {
        baseRels.add(new BaseRelation(crel, candidateId, targetPrio));
      } else if ((SYMMETRIC.contains(crel) && idTargetPrio > 0)) {
        assert id != null;
        addRule(targetMap, candidateId, id, idTargetPrio);
      } else if (!seenCandidates.contains(candidateId)) {
        extendCandidates(candidates, candidate, rels);
      }
      seenCandidates.add(candidateId);
    }
    if ((id != null && idTargetPrio == 0)) {
      baseRels = baseRels.stream().filter((it) -> !it.base.equals(id)).collect(Collectors.toList());
      if (baseRels.size() > 0) {
        List<String> baseClasses = new ArrayList<>();
        for (BaseRelation baserel : baseRels) {
          /*@Nullable*/ Map classDfn = ((/*@Nullable*/ Map) vocabIndex.get(baserel.base));
          if (classDfn == null) {
            classDfn = Builtins.mapOf(ID, baserel.base);
          }
          baseClasses.add(((String) classDfn.get(ID)));
          break;
        }
        addRule(targetMap, id, baseClasses);
      }
    }
  }

  protected static void processPropertyRelations(Map obj, Map vocabIndex, Map<String, Object> target, Map targetMap) {
    List<String> rels = new ArrayList<>(Arrays.asList(new String[] {(String) OWL_equivalentProperty, RDFS_subPropertyOf}));
    if (!obj.containsKey(ID)) {
      return;
    }
    String id = (String) obj.get(ID);
    Integer idTargetPrio = getTargetPriority(target, id);
    /*@Nullable*/ String property = (idTargetPrio > 0 ? id : null);
    Integer propPrio = idTargetPrio;
    if (property != null) {
      addRule(targetMap, id, property, idTargetPrio);
    }
    List<Map.Entry</*@Nullable*/ String, Map>> candidates = collectCandidates(obj, rels);
    List<Map.Entry<Integer, String>> baseprops = new ArrayList<>();
    if (idTargetPrio > 0) {
      baseprops.add(new KeyValue(idTargetPrio, id));
    }
    /*@Nullable*/ String candidateProp = null;
    while ((candidates != null && candidates.size() > 0)) {
      Map.Entry<String, Map> crel_candidate = candidates.remove(0);
      String crel = crel_candidate.getKey();
      Map candidate = crel_candidate.getValue();
      if (!candidate.containsKey(ID)) {
        continue;
      }
      String candidateId = (String) candidate.get(ID);
      candidate = ((Map) vocabIndex.getOrDefault(candidateId, candidate));
      Integer targetPrio = getTargetPriority(target, candidateId);
      if ((idTargetPrio == 0 && targetPrio > 0)) {
        baseprops.add(new KeyValue(targetPrio, candidateId));
        addRule(targetMap, id, candidateId, targetPrio);
        candidateProp = candidateId;
        propPrio = targetPrio;
      } else if ((SYMMETRIC.contains(crel) && targetPrio == 0 && idTargetPrio > 0)) {
        addRule(targetMap, candidateId, id, idTargetPrio);
        candidateProp = candidateId;
        propPrio = targetPrio;
      } else {
        extendCandidates(candidates, candidate, rels);
      }
    }
    processPropertyChain(obj, target, targetMap, candidateProp, baseprops);
  }

  protected static boolean processPropertyChain(Map obj, Map<String, Object> target, Map targetMap, /*@Nullable*/ String candidateProp, List<Map.Entry<Integer, String>> baseprops) {
    if (!obj.containsKey(OWL_propertyChainAxiom)) {
      return false;
    }
    List<Map> propChain = ((List<Map>) obj.get(OWL_propertyChainAxiom));
    /*@Nullable*/ String sourceProperty = null;
    List<Map> lst = (List<Map>) propChain.get(0).get(LIST);
    Map lead = (Map) lst.get(0);
    if (lead != null) {
      sourceProperty = (String) lead.get(ID);
    }
    String valueFrom = (String) lst.get(1).get(ID);
    /*@Nullable*/ String rtype = null;
    if ((sourceProperty == null || sourceProperty.startsWith("_:"))) {
      try {
        List<Map> ranges = (List<Map>) lead.get(RDFS_range);
        rtype = (String) ranges.get(0).get(ID);
      } catch (Exception e) {
      }
      List<Map> superprops = (List<Map>) lead.get(RDFS_subPropertyOf);
      sourceProperty = (String) superprops.get(0).get(ID);
    }
    /*@Nullable*/ Map match = (rtype != null ? Builtins.mapOf(TYPE, rtype) : null);
    if (sourceProperty != null) {
      if ((!sourceProperty.equals(candidateProp) && getTargetPriority(target, sourceProperty) == 0)) {
        for (Map.Entry<Integer, String> prio_baseprop : baseprops) {
          Integer prio = prio_baseprop.getKey();
          String baseprop = prio_baseprop.getValue();
          Map rule = ruleFrom(baseprop, null, valueFrom, match);
          addRule(targetMap, sourceProperty, rule, prio);
        }
        return true;
      }
    }
    return false;
  }

  protected static List<Map.Entry</*@Nullable*/ String, Map>> collectCandidates(Map obj, List<String> rels) {
    List<Map.Entry</*@Nullable*/ String, Map>> candidates = new ArrayList<>();
    for (String rel : rels) {
      Object refs = (Object) obj.get(rel);
      if (refs instanceof List) {
        candidates.addAll(((List<Map.Entry</*@Nullable*/ String, Map>>) ((List) refs).stream().map((ref) -> new KeyValue(rel, ref)).collect(Collectors.toList())));
      }
    }
    return candidates;
  }

  protected static void extendCandidates(List<Map.Entry</*@Nullable*/ String, Map>> candidates, Map candidate, List<String> rels) {
    for (String rel : rels) {
      /*@Nullable*/ List<Map> superrefs = ((/*@Nullable*/ List<Map>) candidate.get(rel));
      if (superrefs != null) {
        for (Map sup : superrefs) {
          if (sup.containsKey(ID)) {
            candidates.add(new KeyValue(null, sup));
          }
        }
      }
    }
  }

  protected static void processReifiedForms(Map obj, Map vocabIndex, Map<String, Object> targetMap) {
    /*@Nullable*/ Map prop = traceInverseOfSubject(obj, vocabIndex);
    if (prop != null) {
      List<Map> ranges = new ArrayList<>();
      String propId = (String) obj.get(ID);
      if (obj.containsKey(RDFS_range)) {
        ranges.addAll(asList(obj.get(RDFS_range)));
      }
      if ((prop != obj && prop.containsKey(RDFS_range))) {
        ranges.addAll(asList(prop.get(RDFS_range)));
      }
      /*@Nullable*/ String propertyFrom = null;
      /*@Nullable*/ String valueFrom = null;
      for (Map range : ranges) {
        /*@Nullable*/ Map rangeNode = (/*@Nullable*/ Map) vocabIndex.get(range.get(ID));
        if (rangeNode == null) {
          continue;
        }
        /*@Nullable*/ Map reverses = (/*@Nullable*/ Map) ((Map) rangeNode.get(REVERSE));
        List<Map> inDomainOf = (reverses != null ? ((List<Map>) reverses.getOrDefault(RDFS_domain, new ArrayList<>())) : new ArrayList<>());
        for (Map domainProp : inDomainOf) {
          if (leadsTo(domainProp, vocabIndex, RDFS_subPropertyOf, RDF_predicate)) {
            propertyFrom = (String) domainProp.get(ID);
          } else if (leadsTo(domainProp, vocabIndex, RDFS_subPropertyOf, RDF_object)) {
            valueFrom = (String) domainProp.get(ID);
          }
        }
      }
      if ((propertyFrom != null && valueFrom != null && propId instanceof String)) {
        addRule(targetMap, (String) propId, ruleFrom(null, propertyFrom, valueFrom, null));
      }
    }
  }

  protected static /*@Nullable*/ Map traceInverseOfSubject(Map obj, Map vocabIndex) {
    /*@Nullable*/ List<Map> invs = ((/*@Nullable*/ List<Map>) obj.get(OWL_inverseOf));
    if (invs != null) {
      for (Map p : invs) {
        if (leadsTo(p, vocabIndex, RDFS_subPropertyOf, RDF_subject)) {
          return p;
        }
      }
    }
    /*@Nullable*/ List<Map> supers = ((/*@Nullable*/ List<Map>) obj.get(RDFS_subPropertyOf));
    if (supers == null) {
      return null;
    }
    for (Map supref : supers) {
      Map sup = (Map) ((Map) (supref.containsKey(ID) ? vocabIndex.getOrDefault(supref.get(ID), supref) : supref));
      if (traceInverseOfSubject(sup, vocabIndex) != null) {
        return sup;
      }
    }
    return null;
  }

  protected static void addRule(Map<String, Object> targetMap, String sourceId, Object rule) {
    addRule(targetMap, sourceId, rule, 0);
  }
  protected static void addRule(Map<String, Object> targetMap, String sourceId, Object rule, Integer priority) {
    if ((sourceId == null && ((Object) rule) == null || sourceId != null && (sourceId).equals(rule))) {
      return;
    }
    Object rulePriority = (Object) (rule instanceof List ? ((List) rule).stream().map((it) -> new KeyValue(priority, it)).collect(Collectors.toList()) : new ArrayList<>(Arrays.asList(new Object[] {(Object) new KeyValue(priority, rule)})));
    List rules = (List) ((List) targetMap.get(sourceId));
    if (rules == null) {
      targetMap.put(sourceId, rulePriority);
    } else {
      if (!(rules instanceof List)) {
        rules = new ArrayList<>(Arrays.asList(new List[] {(List) rules}));
        targetMap.put(sourceId, rules);
      }
      if (rulePriority instanceof List) {
        rules.addAll((List) rulePriority);
      } else {
        ((List) rules).add(rulePriority);
      }
    }
  }

  protected static Map ruleFrom(/*@Nullable*/ String property, /*@Nullable*/ String propertyFrom, /*@Nullable*/ String valueFrom, /*@Nullable*/ Map<String, String> match) {
    return Builtins.mapOf("property", property, "propertyFrom", propertyFrom, "valueFrom", valueFrom, "match", match);
  }

  protected static Integer getTargetPriority(Map<String, Object> target, String id) {
    Integer topPrio = (Integer) target.size();
    Integer prio = topPrio * 3;
    for (Object v : target.values()) {
      if ((id == null && ((Object) v) == null || id != null && (id).equals(v))) {
        return prio;
      }
      prio -= 1;
    }
    if ((target.containsKey(VOCAB) && id.startsWith(((String) target.get(VOCAB))))) {
      return topPrio * 2;
    }
    prio = topPrio;
    for (Object v : target.values()) {
      if ((v instanceof String && id.startsWith(((String) v)))) {
        return prio;
      }
      prio -= 1;
    }
    return 0;
  }

  public static boolean leadsTo(Map s, Map vocabIndex, String rel, Object o) {
    if ((s.get(ID) == null && ((Object) o) == null || s.get(ID) != null && (s.get(ID)).equals(o))) {
      return true;
    }
    /*@Nullable*/ Map data = (/*@Nullable*/ Map) ((Map) (s.containsKey(ID) ? vocabIndex.getOrDefault(s.get(ID), s) : s));
    List<Map> xs = (List<Map>) (data != null ? ((List) data.getOrDefault(rel, new ArrayList<>())) : new ArrayList<>());
    for (Map x : xs) {
      if (((x.get(ID) == null && ((Object) o) == null || x.get(ID) != null && (x.get(ID)).equals(o)) || leadsTo(x, vocabIndex, rel, o))) {
        return true;
      }
    }
    return false;
  }
}
