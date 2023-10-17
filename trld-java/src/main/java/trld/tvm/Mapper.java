/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/tvm/mapper.py
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
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.VOCAB;
import static trld.jsonld.Base.asList;

public class Mapper {

  public static Object mapTo(Map targetMap, Object indata) {
    return mapTo(targetMap, indata, false);
  }
  public static Object mapTo(Map targetMap, Object indata, Boolean dropUnmapped) {
    Object result = (indata instanceof Map ? new HashMap<>() : new ArrayList<>());
    modify(targetMap, indata, result, dropUnmapped);
    return result;
  }

  protected static void modify(Map targetMap, Object ino, Object outo, Boolean dropUnmapped) {
    if (ino instanceof Map) {
      for (Map.Entry<String, Object> k_v : ((Map<String, Object>) ino).entrySet()) {
        String k = k_v.getKey();
        Object v = k_v.getValue();
        modifyPair(targetMap, k, v, outo, dropUnmapped);
      }
    } else if (ino instanceof List) {
      Integer i = 0;
      for (Object v : (List) ino) {
        modifyPair(targetMap, i, v, outo, dropUnmapped);
        i += 1;
      }
    }
  }

  protected static void modifyPair(Map targetMap, Object k, Object v, Object outo, Boolean dropUnmapped) {
    Map<Object, Object> mapo = map(targetMap, k, v, dropUnmapped);
    for (Map.Entry<Object, Object> mapk_mapv : mapo.entrySet()) {
      Object mapk = mapk_mapv.getKey();
      Object mapv = mapk_mapv.getValue();
      Object outv;
      if (mapv instanceof List) {
        outv = new ArrayList<>();
        modify(targetMap, (List) mapv, outv, dropUnmapped);
        mapv = (List) outv;
      } else if (mapv instanceof Map) {
        outv = new HashMap<>();
        modify(targetMap, (Map) mapv, outv, dropUnmapped);
        mapv = (Map) outv;
      }
      if (outo instanceof Map) {
        if (((Map) outo).containsKey(mapk)) {
          List values = (List) asList(((Map) outo).get(mapk));
          values.addAll(asList(mapv));
          mapv = (Object) values;
        }
        ((Map) outo).put(mapk, mapv);
      } else {
        ((List) outo).add(mapv);
      }
    }
  }

  protected static Map map(Map targetMap, Object key, Object value) {
    return map(targetMap, key, value, false);
  }
  protected static Map map(Map targetMap, Object key, Object value, Boolean dropUnmapped) {
    Object somerule = (Object) targetMap.get(key);
    if ((dropUnmapped && key instanceof String && !((String) key).substring(0, 0 + 1).equals("@") && somerule == null)) {
      return new HashMap<>();
    }
    if (value instanceof List) {
      List<Object> remapped = new ArrayList<>();
      for (Object v : (List) value) {
        Object item = ((v instanceof String && targetMap.containsKey(v)) ? targetMap.get(v) : v);
        if (item instanceof List) {
          remapped.addAll((List) item);
        } else {
          remapped.add(item);
        }
      }
      value = (List) remapped;
    }
    if (somerule == null) {
      return Builtins.mapOf(key, value);
    }
    Map out = new HashMap<>();
    Set<String> mappedKeypaths = new HashSet();
    for (Object rule : asList(somerule)) {
      if (rule instanceof String) {
        out.put((String) rule, value);
        break;
      }
      if (rule instanceof Map) {
        List<Map> objectvalues = (List<Map>) value;
        /*@Nullable*/ String property = (/*@Nullable*/ String) ((Map) rule).get("property");
        /*@Nullable*/ String propertyFrom = (/*@Nullable*/ String) ((Map) rule).get("propertyFrom");
        if (propertyFrom != null) {
          Map first = (Map) objectvalues.get(0);
          List<Map> propertyFromObject = (List<Map>) first.get(propertyFrom);
          property = (String) propertyFromObject.get(0).get(ID);
        }
        if (targetMap.containsKey(property)) {
          property = (String) asList(targetMap.get(property)).get(0);
        }
        List<Object> outvalue = new ArrayList<>();
        /*@Nullable*/ String valueFrom = (/*@Nullable*/ String) ((Map) rule).get("valueFrom");
        if (valueFrom != null) {
          for (Map v : objectvalues) {
            assert v instanceof Map;
            /*@Nullable*/ Map<String, String> match = (/*@Nullable*/ Map<String, String>) ((Map) rule).get("match");
            if ((match == null || (match.containsKey(TYPE) && ((List) ((Map) v).get(TYPE)).stream().anyMatch(t -> (t == null && ((Object) match.get(TYPE)) == null || t != null && (t).equals(match.get(TYPE))))))) {
              Object vv = (Object) ((Map) v).get(valueFrom);
              if (vv instanceof List) {
                for (Object m : (List) vv) {
                  outvalue.add(m);
                }
              } else {
                outvalue.add(vv);
              }
            }
          }
        } else {
          outvalue = (List<Object>) value;
        }
        List<Object> mappedvalue = new ArrayList<>();
        for (Object v : outvalue) {
          mappedvalue.add((v instanceof String ? targetMap.getOrDefault((String) v, (String) v) : v));
        }
        outvalue = mappedvalue;
        if ((property != null && (outvalue != null && outvalue.size() > 0))) {
          if (valueFrom != null) {
            String mappedKey = key + " " + valueFrom;
            if (mappedKeypaths.contains(mappedKey)) {
              continue;
            }
            mappedKeypaths.add(mappedKey);
          }
          out.put(property, outvalue);
        }
      }
    }
    return out;
  }
}
