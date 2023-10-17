/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/jsonld/expansion.py
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
import trld.jsonld.Context;
import trld.jsonld.Term;
import trld.jsonld.InvalidBaseDirectionError;
import trld.jsonld.InvalidNestValueError;
import static trld.jsonld.Expansion.*;


public class InvalidReverseValueError extends JsonLdError {
  InvalidReverseValueError() { };
  InvalidReverseValueError(String msg) { super(msg); };
}
