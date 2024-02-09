package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class CxAttributes {
  public static final AttributeKey<String> SPAN_ROLE = AttributeKey.stringKey("cx.internal.span.role");

  private CxAttributes(){}
}
