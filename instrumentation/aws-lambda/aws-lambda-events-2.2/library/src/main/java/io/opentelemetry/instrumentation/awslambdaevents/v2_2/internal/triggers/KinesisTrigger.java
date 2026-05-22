package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;

import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord;
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
public final class KinesisTrigger extends Trigger {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof KinesisEvent;
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    KinesisEvent event = requireCorrectEventType(request);
    return commonDestination(event).map(name -> name + " deliver").orElse("kinesis deliver");
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
      KinesisEvent event = requireCorrectEventType(request);
      attributes.put(FAAS_TRIGGER, "pubsub");
      attributes.put(FAAS_TRIGGER_TYPE, "Kinesis");
      attributes.put(MESSAGING_SYSTEM, "AmazonKinesis");
      attributes.put(MESSAGING_OPERATION, "deliver");
      commonDestination(event).ifPresent(name -> attributes.put(MESSAGING_DESTINATION_NAME, name));
      if (!event.getRecords().isEmpty()) {
        attributes.put(
            RPC_REQUEST_PAYLOAD,
            limitedPayload(SerializationUtil.toJson(event.getRecords().get(0))));
      }
    } catch (RuntimeException e) {
      logException(e, "KinesisTrigger.onStart instrumentation failed");
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

  private static Optional<String> commonDestination(KinesisEvent event) {
    if (event.getRecords().isEmpty()) {
      return Optional.empty();
    }
    KinesisEventRecord record = event.getRecords().get(0);
    String arn = record.getEventSourceARN();
    if (arn == null || arn.isEmpty()) {
      return Optional.empty();
    }
    int slash = arn.lastIndexOf('/');
    return Optional.of(slash >= 0 ? arn.substring(slash + 1) : arn);
  }

  private static KinesisEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof KinesisEvent)) {
      throw new TriggerMismatchException(
          "Expected KinesisEvent. Received " + request.getInput().getClass());
    }
    return (KinesisEvent) request.getInput();
  }
}
