/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/jsonld/flattening.py
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

import static trld.Common.warning;
import static trld.jsonld.Base.*;

public class Flattening {

  public static Object flatten(Object element) {
    return flatten(element, false);
  }
  public static Object flatten(Object element, Boolean ordered) {
    return flatten(element, ordered, null);
  }
  public static Object flatten(Object element, Boolean ordered, /*@Nullable*/ BNodes bnodes) {
    if (bnodes == null) {
      bnodes = new BNodes();
    }
    Map<String, Map<String, Object>> nodeMap = Builtins.mapOf(DEFAULT, new HashMap<>());
    makeNodeMap(bnodes, element, nodeMap);
    Map defaultGraph = (Map) nodeMap.get(DEFAULT);
    List<String> graphNames = new ArrayList(nodeMap.keySet());
    if (ordered) {
      Collections.sort(graphNames);
    }
    for (String graphName : graphNames) {
      if ((graphName == null && ((Object) DEFAULT) == null || graphName != null && (graphName).equals(DEFAULT))) {
        continue;
      }
      Map<String, Object> graph = ((Map<String, Object>) nodeMap.get(graphName));
      if (!defaultGraph.containsKey(graphName)) {
        defaultGraph.put(graphName, Builtins.mapOf(ID, graphName));
      }
      Map<String, Object> entry = ((Map<String, Object>) defaultGraph.get(graphName));
      List<Map<String, Object>> entryGraph = new ArrayList<>();
      entry.put(GRAPH, entryGraph);
      List<String> nodeIds = new ArrayList(graph.keySet());
      if (ordered) {
        Collections.sort(nodeIds);
      }
      for (String nodeId : nodeIds) {
        Map<String, Object> node = ((Map<String, Object>) graph.get(nodeId));
        if ((node.size() > 1 || !node.containsKey(ID))) {
          entryGraph.add(node);
        }
      }
    }
    List<Map<String, Object>> flattened = new ArrayList<>();
    List<String> topNodeIds = new ArrayList(defaultGraph.keySet());
    if (ordered) {
      Collections.sort(topNodeIds);
    }
    for (String nodeId : topNodeIds) {
      Map<String, Object> topNode = ((Map<String, Object>) defaultGraph.get(nodeId));
      if ((topNode.size() > 1 || !topNode.containsKey(ID))) {
        flattened.add(topNode);
      }
    }
    return flattened;
  }

  public static void makeNodeMap(BNodes bnodes, Object element, Map<String, Map<String, Object>> nodeMap) {
    makeNodeMap(bnodes, element, nodeMap, DEFAULT);
  }
  public static void makeNodeMap(BNodes bnodes, Object element, Map<String, Map<String, Object>> nodeMap, String activeGraph) {
    makeNodeMap(bnodes, element, nodeMap, activeGraph, null);
  }
  public static void makeNodeMap(BNodes bnodes, Object element, Map<String, Map<String, Object>> nodeMap, String activeGraph, /*@Nullable*/ Object activeSubject) {
    makeNodeMap(bnodes, element, nodeMap, activeGraph, activeSubject, null);
  }
  public static void makeNodeMap(BNodes bnodes, Object element, Map<String, Map<String, Object>> nodeMap, String activeGraph, /*@Nullable*/ Object activeSubject, /*@Nullable*/ String activeProperty) {
    makeNodeMap(bnodes, element, nodeMap, activeGraph, activeSubject, activeProperty, null);
  }
  public static void makeNodeMap(BNodes bnodes, Object element, Map<String, Map<String, Object>> nodeMap, String activeGraph, /*@Nullable*/ Object activeSubject, /*@Nullable*/ String activeProperty, /*@Nullable*/ Map<String, Object> listMap) {
    if (element instanceof List) {
      for (Object item : (List) element) {
        makeNodeMap(bnodes, item, nodeMap, activeGraph, activeSubject, activeProperty, listMap);
      }
      return;
    }
    assert element instanceof Map;
    if (!nodeMap.containsKey(activeGraph)) nodeMap.put(activeGraph, new HashMap<>());
    Map<String, Object> graph = (Map<String, Object>) nodeMap.get(activeGraph);
    /*@Nullable*/ Map<String, Object> subjectNode = null;
    if (activeSubject instanceof String) {
      subjectNode = ((Map<String, Object>) graph.get((String) activeSubject));
    }
    if (((Map) element).containsKey(TYPE)) {
      Object etype = (Object) ((Map) element).get(TYPE);
      List mappedTypes = new ArrayList<>();
      for (String item : ((List<String>) asList(etype))) {
        if (isBlank(item)) {
          item = (String) bnodes.makeBnodeId(item);
        }
        mappedTypes.add(item);
      }
      ((Map) element).put(TYPE, (etype instanceof List ? mappedTypes : mappedTypes.get(0)));
    }
    if (((Map) element).containsKey(VALUE)) {
      assert subjectNode != null;
      assert activeProperty instanceof String;
      if (listMap == null) {
        if (!subjectNode.containsKey(activeProperty)) {
          subjectNode.put((String) activeProperty, new ArrayList<>(Arrays.asList(new Map[] {(Map) element})));
        } else {
          List<Object> elements = ((List<Object>) subjectNode.get(activeProperty));
          if (!(elements.stream().anyMatch(el -> nodeEquals((Map) element, el)))) {
            elements.add((Map) element);
          }
        }
      } else {
        ((List<Object>) listMap.get(LIST)).add((Map) element);
      }
    } else if (((Map) element).containsKey(LIST)) {
      assert subjectNode != null;
      assert activeProperty instanceof String;
      Map<String, Object> result = Builtins.mapOf(LIST, new ArrayList<>());
      makeNodeMap(bnodes, ((Map) element).get(LIST), nodeMap, activeGraph, activeSubject, (String) activeProperty, result);
      if (listMap == null) {
        ((List<Object>) subjectNode.get(activeProperty)).add(result);
      } else {
        ((List<Object>) listMap.get(LIST)).add(result);
      }
    } else {
      String eid;
      if (((Map) element).containsKey(ID)) {
        eid = ((String) ((Map) element).remove(ID));
        if ((eid == null || isBlank(eid))) {
          eid = (String) bnodes.makeBnodeId(eid);
        }
      } else {
        eid = (String) bnodes.makeBnodeId(null);
      }
      if (!graph.containsKey(eid)) {
        graph.put(eid, Builtins.mapOf(ID, eid));
      }
      Map<String, Object> node = ((Map<String, Object>) graph.get(eid));
      if (activeSubject instanceof Map) {
        assert activeProperty instanceof String;
        if (!node.containsKey(activeProperty)) {
          node.put((String) activeProperty, new ArrayList<>(Arrays.asList(new Map[] {(Map) activeSubject})));
        } else {
          List<Map<String, Object>> subjects = ((List<Map<String, Object>>) node.get(activeProperty));
          if (!(subjects.stream().anyMatch(subj -> nodeEquals((Map) activeSubject, subj)))) {
            subjects.add((Map) activeSubject);
          }
        }
      } else if (activeProperty != null) {
        assert subjectNode != null;
        Map<String, Object> reference = Builtins.mapOf(ID, eid);
        if (listMap == null) {
          if (!subjectNode.containsKey(activeProperty)) {
            subjectNode.put(activeProperty, new ArrayList<>(Arrays.asList(new Object[] {reference})));
          }
          List<Map<String, Object>> objects = ((List<Map<String, Object>>) subjectNode.get(activeProperty));
          if (!objects.contains(reference)) {
            objects.add(reference);
          }
        } else {
          ((List<Object>) listMap.get(LIST)).add(reference);
        }
      }
      if (((Map) element).containsKey(TYPE)) {
        if (!node.containsKey(TYPE)) node.put(TYPE, new ArrayList<>());
        List ntypes = (List) ((List) node.get(TYPE));
        for (Object ntype : asList(((Map) element).get(TYPE))) {
          if (!ntypes.contains(ntype)) {
            ntypes.add(ntype);
          }
        }
        ((Map) element).remove(TYPE);
      }
      if (((Map) element).containsKey(INDEX)) {
        if ((node.containsKey(INDEX) && !node.get(INDEX).equals(((Map) element).get(INDEX)))) {
          throw new ConflictingIndexesError(node.get(INDEX).toString());
        }
        node.put(INDEX, ((Map) element).remove(INDEX));
      }
      if (((Map) element).containsKey(REVERSE)) {
        Map<String, Object> referencedNode = Builtins.mapOf(ID, eid);
        Map<String, Object> reverseMap = (Map<String, Object>) ((Map<String, Object>) ((Map) element).get(REVERSE));
        for (Map.Entry<String, Object> property_values : reverseMap.entrySet()) {
          String property = property_values.getKey();
          Object values = property_values.getValue();
          for (Object value : ((List) values)) {
            makeNodeMap(bnodes, value, nodeMap, activeGraph, referencedNode, property);
          }
        }
        ((Map) element).remove(REVERSE);
      }
      if (((Map) element).containsKey(GRAPH)) {
        makeNodeMap(bnodes, ((Map) element).get(GRAPH), nodeMap, eid);
        ((Map) element).remove(GRAPH);
      }
      if (((Map) element).containsKey(INCLUDED)) {
        makeNodeMap(bnodes, ((Map) element).get(INCLUDED), nodeMap, activeGraph);
        ((Map) element).remove(INCLUDED);
      }
      List<String> properties = (List<String>) new ArrayList(((Map) element).keySet());
      Collections.sort(properties);
      for (String property : properties) {
        Object evalue = (Object) ((Map) element).get(property);
        if (isBlank(property)) {
          property = (String) bnodes.makeBnodeId(property);
        }
        if (!node.containsKey(property)) {
          node.put(property, new ArrayList<>());
        }
        makeNodeMap(bnodes, evalue, nodeMap, activeGraph, eid, property);
      }
    }
  }

  public static void mergeNodeMaps(Map<String, Map<String, Map<String, Object>>> nodeMaps) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Map<String, Map<String, Object>>> graphName_nodeMap : nodeMaps.entrySet()) {
      String graphName = graphName_nodeMap.getKey();
      Map<String, Map<String, Object>> nodeMap = graphName_nodeMap.getValue();
      for (Map.Entry<String, Map<String, Object>> nodeId_node : nodeMap.entrySet()) {
        String nodeId = nodeId_node.getKey();
        Map<String, Object> node = nodeId_node.getValue();
        if (!result.containsKey(nodeId)) result.put(nodeId, Builtins.mapOf(ID, nodeId));
        Map<String, Object> mergedNode = ((Map<String, Object>) result.get(nodeId));
        for (Map.Entry<String, Object> property_values : node.entrySet()) {
          String property = property_values.getKey();
          Object values = property_values.getValue();
          if ((!property.equals(TYPE) && KEYWORDS.contains(property))) {
            mergedNode.put(property, values);
          } else {
            if (!mergedNode.containsKey(property)) mergedNode.put(property, new ArrayList<>());
            List existing = (List) ((List) mergedNode.get(property));
            existing.addAll(((List) values));
          }
        }
      }
    }
    return;
  }
}
