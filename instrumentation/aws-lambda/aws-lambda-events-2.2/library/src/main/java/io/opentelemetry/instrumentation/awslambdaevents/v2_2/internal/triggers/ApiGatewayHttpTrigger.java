package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerMismatchException;
import io.opentelemetry.semconv.SemanticAttributes.FaasTriggerValues;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("deprecation")
public final class ApiGatewayHttpTrigger extends Trigger {

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof APIGatewayV2HTTPEvent;
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    APIGatewayV2HTTPEvent req = requireCorrectEventType(request);
    return getRoute(req);
  }

  @Override
  public void extract(SpanStatusBuilder spanStatusBuilder, AwsLambdaRequest request,
      @Nullable Object response, @Nullable Throwable error) {
    if (error != null) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
      return;
    }

    if (response != null) {
      APIGatewayV2HTTPResponse res = requireCorrectResponseType(response);
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
      APIGatewayV2HTTPEvent req = requireCorrectEventType(request);
      RequestContext requestContext = req.getRequestContext();
      RequestContext.Http http = requestContext.getHttp();
      Map<String, String> headers = lowercaseMap(req.getHeaders());

      attributes.put(FAAS_TRIGGER, FaasTriggerValues.HTTP);
      attributes.put(FAAS_TRIGGER_TYPE, "Api Gateway HTTP");
      attributes.put(HTTP_METHOD, http.getMethod());
      attributes.put(HTTP_ROUTE, getRoute(req)); // TODO nodejs doesn't do this
      attributes.put(HTTP_URL, getHttpUrl(req, headers));
      attributes.put(NET_SOCK_PEER_ADDR,
          http.getSourceIp()); // TODO change this in python and nodejs
      attributes.put(USER_AGENT_ORIGINAL,
          headers.get("user-agent")); // TODO change this in python and nodejs
      attributes.put(HTTP_SCHEME, headers.get("x-forwarded-proto"));
      attributes.put(NET_HOST_NAME, headers.get("host")); // TODO change this in nodejs

      attributes.put(HTTP_REQUEST_BODY, limitedPayload(req.getBody()));

      // TODO nodejs doesn't do this
      Map<String, String> mvHeaders = orEmpty(headers);
      for (Map.Entry<String, String> entry : mvHeaders.entrySet()) {
        // See https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/http/#http-request-and-response-headers
        attributes.put("http.request.header." + entry.getKey().replace('-', '_'), entry.getValue());
      }

      // We don't have a semantic attribute for query parameters, but would be useful nonetheless
      // TODO nodejs/python doesn't do this
      Map<String, String> queryParameters = orEmpty(req.getQueryStringParameters());
      for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
        attributes.put("http.request.query." + entry.getKey(), entry.getValue());
      }

      // TODO nodejs/python doesn't do this
      Map<String, String> pathParameters = orEmpty(req.getPathParameters());
      for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
        attributes.put("http.request.parameters." + entry.getKey(), entry.getValue());
      }
    } catch (RuntimeException e) {
      logException(e, "ApiGatewayHttpTrigger.onStart instrumentation failed");
    }
  }

  private static String getHttpUrl(
      APIGatewayV2HTTPEvent request, Map<String, String> headers) {
    StringBuilder str = new StringBuilder();

    String scheme = headers.get("x-forwarded-proto");
    if (scheme != null) {
      str.append(scheme).append("://");
    }
    String host = headers.get("host");
    if (host != null) {
      str.append(host);
    }
    String path = request.getRawPath();
    if (path != null) {
      str.append(path);
    }
    String query = request.getRawQueryString();
    if (query != null) {
      str.append('?').append(query);
    }

    return str.length() == 0 ? null : str.toString();
  }

  private static String getRoute(APIGatewayV2HTTPEvent req) {
    String[] parts = req.getRouteKey().split(" ", 2);
    if (parts.length == 2) {
      return parts[1];
    }
    return req.getRouteKey();
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      AwsLambdaRequest request, @Nullable Object response, @Nullable Throwable error) {

    APIGatewayV2HTTPResponse res = requireCorrectResponseType(response);
    try {
      attributes.put(HTTP_STATUS_CODE, res.getStatusCode());
      attributes.put(HTTP_RESPONSE_BODY, limitedPayload(res.getBody()));
    } catch (RuntimeException e) {
      logException(e, "ApiGatewayHttpTrigger.onEnd instrumentation failed");
    }
  }

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysClient(); // TODO this is client in python and server in nodejs
  }

  private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
    return map != null ? map : new HashMap<>();
  }

  private static APIGatewayV2HTTPEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof APIGatewayV2HTTPEvent)) {
      throw new TriggerMismatchException(
          "Expected APIGatewayV2HTTPEvent. Received " + request.getInput().getClass());
    }
    return (APIGatewayV2HTTPEvent) request.getInput();
  }

  private static APIGatewayV2HTTPResponse requireCorrectResponseType(Object response) {
    if (!(response instanceof APIGatewayV2HTTPResponse)) {
      throw new TriggerMismatchException(
          "Expected APIGatewayV2HTTPResponse. Received " + response.getClass());
    }
    return (APIGatewayV2HTTPResponse) response;
  }
}
