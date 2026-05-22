package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNS;
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
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SnsTrigger extends Trigger {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof SNSEvent;
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    SNSEvent event = requireCorrectEventType(request);
    return commonDestination(event).map(name -> name + " deliver").orElse("sns deliver");
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
      SNSEvent event = requireCorrectEventType(request);
      attributes.put(FAAS_TRIGGER, "pubsub");
      attributes.put(FAAS_TRIGGER_TYPE, "SNS");
      attributes.put(MESSAGING_SYSTEM, "AmazonSNS");
      attributes.put(MESSAGING_OPERATION, "deliver");
      commonDestination(event).ifPresent(name -> attributes.put(MESSAGING_DESTINATION_NAME, name));
      if (!event.getRecords().isEmpty()) {
        attributes.put(
            RPC_REQUEST_PAYLOAD,
            limitedPayload(SerializationUtil.toJson(event.getRecords().get(0))));
      }
    } catch (RuntimeException e) {
      logException(e, "SnsTrigger.onStart instrumentation failed");
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
    return SpanKindExtractor.alwaysConsumer();
  }

  private static Optional<String> commonDestination(SNSEvent event) {
    if (event.getRecords().isEmpty()) {
      return Optional.empty();
    }
    SNS sns = event.getRecords().get(0).getSNS();
    if (sns == null || sns.getTopicArn() == null || sns.getTopicArn().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(sns.getTopicArn());
  }

  private static SNSEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof SNSEvent)) {
      throw new TriggerMismatchException(
          "Expected SNSEvent. Received " + request.getInput().getClass());
    }
    return (SNSEvent) request.getInput();
  }
}
