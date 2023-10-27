package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.trigger;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ApiGatewayRestTrigger {

  private ApiGatewayRestTrigger(){}

  public static boolean matches(AwsLambdaRequest request) {
    if (request.getInput() instanceof Map) {
      Map<String, Object> input = (Map<String, Object>) request.getInput();
      Object requestContext = input.get("requestContext");
      Object resource = input.get("resource");
      return requestContext != null && resource instanceof String;
    }
    return false;
  }

  public static Optional<String> maybeSpanName(AwsLambdaRequest request) {
    if (request.getInput() instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> input = (Map<String, Object>) request.getInput();
      Object resource = input.get("resource");
      if (resource instanceof String) {
        return Optional.of((String) resource);
      }
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  public static void addStartAttributes(AttributesBuilder attributes, AwsLambdaRequest request) {
    if (!(request.getInput() instanceof Map)) {
      return;
    }
    Map<String, Object> input =  (Map<String, Object>) request.getInput();
    Map<String, Object> requestContext = (Map<String, Object>) input.get("requestContext");
//    String resource = (String) input.get("resource");
    Map<String, String> identity = (Map<String, String>) requestContext.get("identity");
    Map<String, String> headers = (Map<String, String>) input.getOrDefault("headers", new HashMap<>());

    attributes.put("faas.trigger", "http");
    attributes.put("http.method", (String) requestContext.get("httpMethod"));
    attributes.put("http.route", (String) requestContext.get("resourcePath"));
    attributes.put("http.url", requestContext.get("domainName") + ((String) requestContext.get("path")));
    attributes.put("http.server_name", (String) requestContext.get("domainName"));
    attributes.put("net.peer.ip", identity.get("sourceIp"));
    attributes.put("http.scheme", headers.get("X-Forwarded-For"));

//    if (multiValueQueryStringParameters) {
//      Object.assign(
//          attributes,
//          Object.fromEntries(
//              Object.entries(multiValueQueryStringParameters).map(
//                  ([k, v]) => [`http.request.query.${k}`, v?.length === 1 ? v[0] : v] // We don't have a semantic attribute for query parameters, but would be useful nonetheless
//        )
//      )
//    );
//    }
//
//    if (multiValueHeaders) {
//      Object.assign(
//          attributes,
//          Object.fromEntries(
//              Object.entries(multiValueHeaders).map(([headerName, headerValue]) => [
//      // See https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/http/#http-request-and-response-headers
//          `http.request.header.${headerName}`,
//      headerValue?.length === 1 ? headerValue[0] : headerValue,
//        ])
//      )
//    );
//    }
//    if (pathParameters) {
//      Object.assign(
//          attributes,
//          Object.fromEntries(
//              Object.entries(pathParameters).map(([paramKey, paramValue]) => [
//          `http.request.parameters.${paramKey}`,
//      paramValue,
//        ])
//      )
//    );
//    }
  }

  @SuppressWarnings("unchecked")
  public static void addEndAttributes(AttributesBuilder attributes, AwsLambdaRequest request, Object response) {
    if (!(response instanceof Map)) {
      return;
    }
    Map<String, Object> resp =  (Map<String, Object>) response;

    String statusCode = (String) resp.get("statusCode");
    attributes.put("http.status_code", statusCode);

    boolean error = statusCode.startsWith("4") || statusCode.startsWith("5");


  }
}
