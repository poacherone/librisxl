/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/jsonld/docloader.py
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

import static trld.Mimetypes.JSON_MIME_TYPES;
import static trld.Mimetypes.JSONLD_MIME_TYPE;
import trld.platform.Input;
import trld.jsonld.JsonLdError;
import static trld.jsonld.Docloader.*;


public class RemoteDocument {
  public /*@Nullable*/ String documentUrl;
  public /*@Nullable*/ String contentType;
  public /*@Nullable*/ String contextUrl;
  public /*@Nullable*/ String profile;
  public Object document;
  public RemoteDocument(String documentUrl, String contentType, String contextUrl, String profile, Object document) {
    this.documentUrl = documentUrl;
    this.contentType = contentType;
    this.contextUrl = contextUrl;
    this.profile = profile;
    this.document = document;
  }
}
