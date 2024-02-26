package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Locale;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum MapGetter implements TextMapGetter<Map<String, String>> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Map<String, String> map) {
    return map.keySet();
  }

  @Override
  public String get(Map<String, String> map, String s) {
    return map.get(s.toLowerCase(Locale.ROOT));
  }
}
