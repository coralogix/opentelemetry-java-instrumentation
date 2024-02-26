/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

// this class is only used from LambdaAccess from method with @NoMuzzle annotation
final class LambdaImpl {

  private static final Logger logger = LoggerFactory.getLogger(LambdaImpl.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    // Force loading of LambdaClient; this ensures that an exception is thrown at this point when the
    // Lambda library is not present, which will cause LambdaAccess to have enabled=false in library mode.
    @SuppressWarnings("unused")
    String ensureLoadedDummy = LambdaClient.class.getName();
  }

  private LambdaImpl() {}

  @Nullable
  static SdkRequest modifyRequest(
      SdkRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    if (messagingPropagator == null) {
      return null;
    }

    if (request instanceof InvokeRequest) {
      return injectIntoInvokeRequest((InvokeRequest) request, otelContext, messagingPropagator);
    } else {
      return null;
    }
  }

  private static SdkRequest injectIntoInvokeRequest(
      InvokeRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    String modifiedClientContext = injectIntoClientContext(request.clientContext(), otelContext,
        messagingPropagator);
    return request.toBuilder().clientContext(modifiedClientContext).build();
  }

  private static String injectIntoClientContext(
      String originalClientContext,
      Context otelContext,
      TextMapPropagator messagingPropagator) {

    try {
      Map<String, Object> clientContext = deserialiseClientContext(originalClientContext);
      if (clientContext == null) {
        clientContext = new HashMap<>();
      }

      Object customObject = clientContext.computeIfAbsent("custom",
          k -> new HashMap<String, String>());
      if (!(customObject instanceof Map)) {
        return originalClientContext;
      }

      @SuppressWarnings("unchecked")
      Map<String, String> custom = (Map<String, String>) customObject;
      messagingPropagator.inject(
          otelContext,
          custom,
          MapSetter.INSTANCE
      );
      String modifiedClientContext = serialiseClientContext(clientContext);
      // Make sure we don't exceed the size limit imposed by AWS https://docs.aws.amazon.com/lambda/latest/dg/API_Invoke.html
      if (modifiedClientContext.length() <= 3583) {
        return modifiedClientContext;
      } else {
        return originalClientContext;
      }
    } catch (Throwable e) {
      logger.warn("Failed to inject trace context into client context", e);
      // Whenever something goes wrong we fall back to using the original clientContext
      return originalClientContext;
    }
  }

  @Nullable
  private static Map<String, Object> deserialiseClientContext(String base64ClientContext)
      throws Exception {
    try {
      if (base64ClientContext == null) {
        return null;
      } else {
        byte[] json = Base64.getDecoder().decode(base64ClientContext);
        TypeReference<HashMap<String, Object>> typeRef
            = new TypeReference<HashMap<String, Object>>() {};
        return OBJECT_MAPPER.readValue(json, typeRef);
      }
    } catch (Throwable e) {
      throw new Exception("Failed to deserialize client context \"" + base64ClientContext + "\"",
          e);
    }
  }

  private static String serialiseClientContext(Map<String, Object> context)
      throws Exception {
    try {
      byte[] json = OBJECT_MAPPER.writeValueAsBytes(context);
      return Base64.getEncoder().encodeToString(json);
    } catch (Throwable e) {
      throw new Exception("Failed to serialise client context " + context, e);
    }
  }

  private enum MapSetter implements TextMapSetter<Map<String, String>> {
    INSTANCE;

    @Override
    public void set(Map<String, String> carrier, String key, String value) {
      carrier.put(key, value);
    }
  }
}
