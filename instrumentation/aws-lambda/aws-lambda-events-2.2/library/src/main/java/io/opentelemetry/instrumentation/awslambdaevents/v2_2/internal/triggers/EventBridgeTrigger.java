package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;

import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
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
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class EventBridgeTrigger extends Trigger {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof ScheduledEvent;
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    ScheduledEvent event = requireCorrectEventType(request);
    if (event.getDetailType() != null && !event.getDetailType().isEmpty()) {
      return event.getDetailType();
    }
    if (event.getSource() != null && !event.getSource().isEmpty()) {
      return event.getSource();
    }
    return "eventbridge trigger";
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
      ScheduledEvent event = requireCorrectEventType(request);
      boolean scheduled =
          "aws.events".equals(event.getSource()) && "Scheduled Event".equals(event.getDetailType());
      attributes.put(FAAS_TRIGGER, scheduled ? "timer" : "pubsub");
      attributes.put(FAAS_TRIGGER_TYPE, "EventBridge");
      attributes.put(RPC_REQUEST_PAYLOAD, limitedPayload(SerializationUtil.toJson(event)));
    } catch (RuntimeException e) {
      logException(e, "EventBridgeTrigger.onStart instrumentation failed");
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

  private static ScheduledEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof ScheduledEvent)) {
      throw new TriggerMismatchException(
          "Expected ScheduledEvent. Received " + request.getInput().getClass());
    }
    return (ScheduledEvent) request.getInput();
  }
}
