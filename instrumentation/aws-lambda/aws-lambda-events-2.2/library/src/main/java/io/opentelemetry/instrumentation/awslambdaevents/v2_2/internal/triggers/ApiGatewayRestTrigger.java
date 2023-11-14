package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils.emptyIfNull;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils.lowercaseMap;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.HTTP_REQUEST_BODY;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.HTTP_RESPONSE_BODY;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;
import static io.opentelemetry.semconv.SemanticAttributes.FAAS_TRIGGER;
import static io.opentelemetry.semconv.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.SemanticAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.SemanticAttributes.HTTP_URL;
import static io.opentelemetry.semconv.SemanticAttributes.NET_HOST_NAME;
import static io.opentelemetry.semconv.SemanticAttributes.NET_SOCK_PEER_ADDR;
import static io.opentelemetry.semconv.SemanticAttributes.USER_AGENT_ORIGINAL;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.ProxyRequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.RequestIdentity;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerMismatchException;
import io.opentelemetry.semconv.SemanticAttributes.FaasTriggerValues;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("deprecation")
public final class ApiGatewayRestTrigger extends Trigger {

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof APIGatewayProxyRequestEvent;
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    APIGatewayProxyRequestEvent req = requireCorrectEventType(request);
    return req.getResource();
  }

  @Override
  public void extract(SpanStatusBuilder spanStatusBuilder, AwsLambdaRequest request,
      @Nullable Object response, @Nullable Throwable error) {
    if (error != null) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
      return;
    }

    if (response != null) {
      APIGatewayProxyResponseEvent res = requireCorrectResponseType(response);
      int statusCodeInt = res.getStatusCode();
      if (statusCodeInt >= 400 && statusCodeInt < 600) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      } else {
        spanStatusBuilder.setStatus(StatusCode.UNSET);
      }
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      AwsLambdaRequest request) {

    // TODO consider using some things from io.opentelemetry.instrumentation.api.instrumenter.http
    try {
      APIGatewayProxyRequestEvent req = requireCorrectEventType(request);
      ProxyRequestContext requestContext = req.getRequestContext();
      RequestIdentity identity = requestContext.getIdentity();
      Map<String, String> headers = lowercaseMap(req.getHeaders());

      attributes.put(FAAS_TRIGGER, FaasTriggerValues.HTTP);
      attributes.put(FAAS_TRIGGER_TYPE, "Api Gateway Rest");
      attributes.put(HTTP_METHOD, requestContext.getHttpMethod());
      attributes.put(HTTP_ROUTE, requestContext.getResourcePath());
      attributes.put(HTTP_URL, getHttpUrl(req, headers));
      attributes.put(NET_SOCK_PEER_ADDR,
          identity.getSourceIp()); // TODO change this in python and nodejs
      attributes.put(USER_AGENT_ORIGINAL,
          headers.get("user-agent")); // TODO change this in python and nodejs
      attributes.put(HTTP_SCHEME, headers.get("x-forwarded-proto"));
      attributes.put(NET_HOST_NAME, headers.get("host")); // TODO change this in nodejs

      attributes.put(HTTP_REQUEST_BODY, limitedPayload(req.getBody()));

      Map<String, List<String>> mvHeaders = lowercaseMap(req.getMultiValueHeaders());
      for (Map.Entry<String, List<String>> entry : mvHeaders.entrySet()) {
        List<String> values = entry.getValue();
        // TODO nodejs doesn't replace `-` with `_` (nor does it convert to lowercase)
        // See https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/http/#http-request-and-response-headers
        String attributeName = "http.request.header." + entry.getKey().replace('-', '_');
        if (values.size() == 1) {
          attributes.put(attributeName, values.get(0));
        } else {
          attributes.put(attributeName, values.toArray(new String[0]));
        }
      }

      // We don't have a semantic attribute for query parameters, but would be useful nonetheless
      Map<String, List<String>> queryParameters = orEmpty(req.getMultiValueQueryStringParameters());
      for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
        List<String> values = entry.getValue();
        if (values.size() == 1) {
          attributes.put("http.request.query." + entry.getKey(), values.get(0));
        } else {
          attributes.put("http.request.query." + entry.getKey(), values.toArray(new String[0]));
        }
      }

      Map<String, String> pathParameters = orEmpty(req.getPathParameters());
      for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
        attributes.put("http.request.parameters." + entry.getKey(), entry.getValue());
      }
    } catch (RuntimeException e) {
      logException(e, "ApiGatewayRestTrigger.onStart instrumentation failed");
    }
  }

  // Copied from ApiGatewayProxyAttributesExtractor
  private static String getHttpUrl(
      APIGatewayProxyRequestEvent request, Map<String, String> headers) {
    StringBuilder str = new StringBuilder();

    String scheme = headers.get("x-forwarded-proto");
    if (scheme != null) {
      str.append(scheme).append("://");
    }
    String host = headers.get("host");
    if (host != null) {
      str.append(host);
    }
    String path = request.getPath();
    if (path != null) {
      str.append(path);
    }

    try {
      boolean first = true;
      for (Map.Entry<String, String> entry :
          emptyIfNull(request.getQueryStringParameters()).entrySet()) {
        String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name());
        String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name());
        str.append(first ? '?' : '&').append(key).append('=').append(value);
        first = false;
      }
    } catch (UnsupportedEncodingException ignored) {
      // Ignore
    }
    return str.length() == 0 ? null : str.toString();
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      AwsLambdaRequest request, @Nullable Object response, @Nullable Throwable error) {

    APIGatewayProxyResponseEvent res = requireCorrectResponseType(response);
    try {
      attributes.put(HTTP_STATUS_CODE, res.getStatusCode());
      attributes.put(HTTP_RESPONSE_BODY, limitedPayload(res.getBody()));
    } catch (RuntimeException e) {
      logException(e, "ApiGatewayRestTrigger.onEnd instrumentation failed");
    }
  }

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysClient(); // TODO this is client in python and server in nodejs
  }

  private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
    return map != null ? map : new HashMap<>();
  }

  private static APIGatewayProxyRequestEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof APIGatewayProxyRequestEvent)) {
      throw new TriggerMismatchException(
          "Expected APIGatewayProxyRequestEvent. Received " + request.getInput().getClass());
    }
    return (APIGatewayProxyRequestEvent) request.getInput();
  }

  private static APIGatewayProxyResponseEvent requireCorrectResponseType(Object response) {
    if (!(response instanceof APIGatewayProxyResponseEvent)) {
      throw new TriggerMismatchException(
          "Expected APIGatewayProxyResponseEvent. Received " + response.getClass());
    }
    return (APIGatewayProxyResponseEvent) response;
  }
}
