package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TriggerUtils {

  private TriggerUtils() {}

  // Coralogix custom OTEL attributes
  public static final AttributeKey<String> HTTP_REQUEST_BODY = AttributeKey.stringKey("http.request.body");
  public static final AttributeKey<String> HTTP_RESPONSE_BODY = AttributeKey.stringKey("http.response.body");
  public static final AttributeKey<String> FAAS_TRIGGER_TYPE = AttributeKey.stringKey("faas.trigger.type");

  static final int DEFAULT_OTEL_PAYLOAD_SIZE_LIMIT = 50 * 1024;
  static final int OTEL_PAYLOAD_SIZE_LIMIT =
      ConfigPropertiesUtil.getInt("otel.payload-size-limit", DEFAULT_OTEL_PAYLOAD_SIZE_LIMIT);

  public static String limitedPayload(String s) {
    if (s.length() <= OTEL_PAYLOAD_SIZE_LIMIT) {
      return s;
    } else {
      return s.substring(0, OTEL_PAYLOAD_SIZE_LIMIT);
    }
  }
}
