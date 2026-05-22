package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TriggerUtils {

  private TriggerUtils() {}

  private static final Logger logger = Logger.getLogger(TriggerUtils.class.getName());

  // Coralogix custom attributes
  public static final AttributeKey<String> RPC_REQUEST_PAYLOAD = AttributeKey.stringKey("rpc.request.payload");
  public static final AttributeKey<String> RPC_RESPONSE_PAYLOAD = AttributeKey.stringKey("rpc.response.payload");
  public static final AttributeKey<String> HTTP_REQUEST_BODY = AttributeKey.stringKey(
      "http.request.body");
  public static final AttributeKey<String> HTTP_RESPONSE_BODY = AttributeKey.stringKey(
      "http.response.body");
  public static final AttributeKey<String> FAAS_TRIGGER_TYPE = AttributeKey.stringKey(
      "faas.trigger.type");

  static final int DEFAULT_OTEL_PAYLOAD_SIZE_LIMIT = 50 * 1024;
  static final int OTEL_PAYLOAD_SIZE_LIMIT =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common")
          .getLong("payload-size-limit", (long) DEFAULT_OTEL_PAYLOAD_SIZE_LIMIT)
          .intValue();

  public static String limitedPayload(String s) {
    if (s == null) {
      return null;
    } else if (s.length() <= OTEL_PAYLOAD_SIZE_LIMIT) {
      return s;
    } else {
      return s.substring(0, OTEL_PAYLOAD_SIZE_LIMIT);
    }
  }

  public static void logException(Exception e, String message) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    // Print stack trace in single line, otherwise AWS will treat it as multiple logs.
    logger.warning(message + ": " + sw.toString().replace("\n", " "));
  }
}
