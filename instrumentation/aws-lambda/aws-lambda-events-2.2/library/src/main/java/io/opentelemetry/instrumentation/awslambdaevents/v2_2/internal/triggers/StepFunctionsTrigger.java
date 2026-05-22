package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerMismatchException;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.SerializationUtil;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class StepFunctionsTrigger extends Trigger {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");

  @Override
  public boolean matches(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof Map)) {
      return false;
    }
    return looksLikeStepFunctionsPayload((Map<?, ?>) request.getInput());
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    Map<?, ?> payload = requireCorrectEventType(request);
    String stateMachineName = nestedString(payload, "stateMachineContext", "Name");
    String stateName = nestedString(payload, "stateContext", "Name");
    if (stateMachineName != null && stateName != null) {
      return stateMachineName + " " + stateName;
    }
    if (stateName != null) {
      return stateName;
    }
    if (stateMachineName != null) {
      return stateMachineName;
    }
    return "step functions trigger";
  }

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      AwsLambdaRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {
    if (error != null) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    }
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, AwsLambdaRequest request) {
    try {
      Map<?, ?> payload = requireCorrectEventType(request);
      attributes.put(FAAS_TRIGGER, "other");
      attributes.put(FAAS_TRIGGER_TYPE, "Step Functions");
      attributes.put(RPC_REQUEST_PAYLOAD, limitedPayload(SerializationUtil.toJson(payload)));
    } catch (RuntimeException e) {
      logException(e, "StepFunctionsTrigger.onStart instrumentation failed");
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      AwsLambdaRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {}

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysServer();
  }

  private static Map<?, ?> requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof Map)) {
      throw new TriggerMismatchException(
          "Expected Step Functions payload as Map. Received " + request.getInput().getClass());
    }
    Map<?, ?> payload = (Map<?, ?>) request.getInput();
    if (!looksLikeStepFunctionsPayload(payload)) {
      throw new TriggerMismatchException("Expected Step Functions payload markers.");
    }
    return payload;
  }

  private static boolean looksLikeStepFunctionsPayload(Map<?, ?> payload) {
    return payload.containsKey("executionContext")
        && payload.containsKey("stateContext")
        && payload.containsKey("stateMachineContext")
        && payload.get("executionContext") instanceof Map
        && payload.get("stateContext") instanceof Map
        && payload.get("stateMachineContext") instanceof Map;
  }

  // The payload shape is validated before the cast, so reading the nested context map is safe.
  @SuppressWarnings("unchecked")
  private static String nestedString(Map<?, ?> payload, String parentKey, String childKey) {
    Object parent = payload.get(parentKey);
    if (!(parent instanceof Map)) {
      return null;
    }
    Object value = ((Map<Object, Object>) parent).get(childKey);
    return value instanceof String ? (String) value : null;
  }
}
