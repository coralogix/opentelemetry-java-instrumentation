package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;

import com.amazonaws.services.lambda.runtime.events.CognitoEvent;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CognitoTrigger extends Trigger {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");

  @Override
  public boolean matches(AwsLambdaRequest request) {
    Object input = request.getInput();
    return input instanceof CognitoEvent
        || input.getClass()
            .getName()
            .startsWith("com.amazonaws.services.lambda.runtime.events.CognitoUserPool");
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    Object event = requireCorrectEventType(request);
    String triggerSource = invokeStringGetter(event, "getTriggerSource");
    if (triggerSource != null && !triggerSource.isEmpty()) {
      return triggerSource;
    }
    String eventType = invokeStringGetter(event, "getEventType");
    if (eventType != null && !eventType.isEmpty()) {
      return eventType;
    }
    return "cognito trigger";
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
      Object event = requireCorrectEventType(request);
      attributes.put(FAAS_TRIGGER, "other");
      attributes.put(FAAS_TRIGGER_TYPE, "Cognito");
      attributes.put(RPC_REQUEST_PAYLOAD, limitedPayload(SerializationUtil.toJson(event)));
    } catch (RuntimeException e) {
      logException(e, "CognitoTrigger.onStart instrumentation failed");
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

  private static Object requireCorrectEventType(AwsLambdaRequest request) {
    if (!new CognitoTrigger().matches(request)) {
      throw new TriggerMismatchException(
          "Expected Cognito event. Received " + request.getInput().getClass());
    }
    return request.getInput();
  }

  @Nullable
  private static String invokeStringGetter(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      Object result = method.invoke(target);
      return result instanceof String ? (String) result : null;
    } catch (NoSuchMethodException e) {
      return null;
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Could not invoke " + methodName + " on Cognito event.", e);
    }
  }
}
