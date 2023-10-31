package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.triggers;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.HTTP_REQUEST_BODY;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.HTTP_RESPONSE_BODY;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_TRIGGER;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SERVER_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_IP;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ApiGatewayRestTrigger extends Trigger {

  @SuppressWarnings("unchecked")
  @Override
  public boolean matches(AwsLambdaRequest request) {
    if (request.getInput() instanceof Map) {

      Map<String, Object> input = (Map<String, Object>) request.getInput();
      Object requestContext = input.get("requestContext");
      Object resource = input.get("resource");
      return requestContext != null && resource instanceof String;
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String extract(AwsLambdaRequest request) {
    if (request.getInput() instanceof Map) {
      Map<String, Object> input = (Map<String, Object>) request.getInput();
      Object resource = input.get("resource");
      if (resource instanceof String) {
        return (String) resource;
      }
    }
    return "API Gateway";
  }

  @SuppressWarnings("unchecked")
  @Override
  public void extract(SpanStatusBuilder spanStatusBuilder, AwsLambdaRequest request,
      @Nullable Object response, @Nullable Throwable error) {
    if (response instanceof Map) {
      Map<String, Object> responseMap = (Map<String, Object>) response;
      Object statusCode = responseMap.get("statusCode");
      if (statusCode instanceof Number) {
        int statusCodeInt = ((Number) statusCode).intValue();
        // This is the logic for client spans, while this is a server span, I did it for consistency with python and js. We should consider changing the logic (or changing the span to Client?).
        if (statusCodeInt >= 400 && statusCodeInt < 600) {
          spanStatusBuilder.setStatus(StatusCode.ERROR);
        } else {
          spanStatusBuilder.setStatus(StatusCode.UNSET);
        }
        return;
      }
    }
    SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
  }

  // Some attributes are deprecated, but we still want to add them, just like python and node.js do.
  @SuppressWarnings({"unchecked", "CatchAndPrintStackTrace", "deprecation"})
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      AwsLambdaRequest request) {

    // TODO consider using some things from io.opentelemetry.instrumentation.api.instrumenter.http
    try {
      if (!(request.getInput() instanceof Map)) {
        return;
      }
      Map<String, Object> input = (Map<String, Object>) request.getInput();
      Map<String, Object> requestContext = getMapFromMap(input, "requestContext");
      Map<String, String> identity = getMapFromMap(requestContext, "identity");
      Map<String, String> headers = getMapFromMap(input, "headers");

      attributes.put(FAAS_TRIGGER, "http");
      attributes.put(FAAS_TRIGGER_TYPE, "Api Gateway Rest");
      attributes.put(HTTP_METHOD, (String) requestContext.get("httpMethod"));
      attributes.put(HTTP_ROUTE, (String) requestContext.get("resourcePath"));
      attributes.put(HTTP_URL,
          requestContext.get("domainName") + ((String) requestContext.get("path")));
      attributes.put(HTTP_SERVER_NAME, (String) requestContext.get("domainName"));
      attributes.put(NET_PEER_IP, identity.get("sourceIp"));
      attributes.put(HTTP_SCHEME, headers.get("X-Forwarded-For"));

      attributes.put(HTTP_REQUEST_BODY, limitedPayload((String) input.get("body")));

      Map<String, List<String>> mvHeaders = getMapFromMap(input, "multiValueHeaders");
      for (Map.Entry<String, List<String>> entry : mvHeaders.entrySet()) {
        List<String> values = entry.getValue();
        // See https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/http/#http-request-and-response-headers
        if (values.size() == 1) {
          attributes.put("http.request.header." + entry.getKey(), values.get(0));
        } else {
          attributes.put("http.request.header." + entry.getKey(), values.toArray(new String[0]));
        }
      }

      // We don't have a semantic attribute for query parameters, but would be useful nonetheless
      Map<String, List<String>> queryParameters = getMapFromMap(input,
          "multiValueQueryStringParameters");
      for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
        List<String> values = entry.getValue();
        if (values.size() == 1) {
          attributes.put("http.request.query." + entry.getKey(), values.get(0));
        } else {
          attributes.put("http.request.query." + entry.getKey(), values.toArray(new String[0]));
        }
      }

      Map<String, String> pathParameters = getMapFromMap(input, "pathParameters");
      for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
        attributes.put("http.request.parameters." + entry.getKey(), entry.getValue());
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  // May throw ClassCastException
  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> getMapFromMap(Map<String, Object> from, String key) {
    Map<K, V> m = (Map<K, V>) from.get(key);
    if (m != null) {
      return m;
    }
    return new HashMap<>();
  }

  @SuppressWarnings({"unchecked", "CatchAndPrintStackTrace"})
  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      AwsLambdaRequest request, @Nullable Object response, @Nullable Throwable error) {
    if (!(response instanceof Map)) {
      return;
    }
    try {
      Map<String, Object> resp = (Map<String, Object>) response;
      long statusCode = ((Number) resp.get("statusCode")).longValue();
      attributes.put(HTTP_STATUS_CODE, statusCode);
      attributes.put(HTTP_RESPONSE_BODY, limitedPayload((String) resp.get("body")));
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysServer();
  }
}
