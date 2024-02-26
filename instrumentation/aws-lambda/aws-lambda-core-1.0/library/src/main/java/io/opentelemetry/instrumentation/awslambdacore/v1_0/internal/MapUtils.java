/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MapUtils {
  public static <V> Map<String, V> lowercaseMap(Map<String, V> source) {
    return emptyIfNull(source).entrySet().stream()
        .filter(e -> e.getKey() != null)
        .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
  }

  public static <V> Map<String, V> emptyIfNull(Map<String, V> map) {
    return map == null ? Collections.emptyMap() : map;
  }

  private MapUtils() {}
}
