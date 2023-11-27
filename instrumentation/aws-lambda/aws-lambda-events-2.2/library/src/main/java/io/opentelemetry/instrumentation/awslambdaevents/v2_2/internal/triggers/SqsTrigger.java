package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerMismatchException;
import io.opentelemetry.semconv.SemanticAttributes;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;
import static io.opentelemetry.semconv.SemanticAttributes.FAAS_TRIGGER;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsTrigger extends Trigger {

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof SQSEvent;
  }

  // https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/messaging/messaging-spans.md#span-name
  // TODO python uses a different name
  @Override
  public String extract(AwsLambdaRequest request) {
    SQSEvent event = requireCorrectEventType(request);
    Optional<String> destination = commonDestination(event);
    if (destination.isPresent()) {
      return destination.get() + " deliver";
    } else {
      return "(anonymous) deliver";
    }
  }

  @Override
  public void extract(SpanStatusBuilder spanStatusBuilder, AwsLambdaRequest request,
      @Nullable Object response, @Nullable Throwable error) {
    if (error != null) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    }
  }

  // TODO Having multiple links to the same span looks weird in Coralogix UI. Not sure if we should solve it here or in the UI. Leaving it like this for now for consistency with python.
  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext,
      AwsLambdaRequest request) {

    SQSEvent event = requireCorrectEventType(request);
    for (SQSMessage message : event.getRecords()) {
      TextMapPropagator propagator = GlobalOpenTelemetry.get().getPropagators()
          .getTextMapPropagator();
      Context root = Context.root();
      Context contextFromAttributes = propagator.extract(root, message.getMessageAttributes(),
          MessageAttributeGetter.INSTANCE);
      if (contextFromAttributes != root) {
        SpanContext messageSpanCtx = Span.fromContext(contextFromAttributes).getSpanContext();
        if (messageSpanCtx.isValid()) {
          spanLinks.addLink(messageSpanCtx);
        }
      }
    }
  }

  enum MessageAttributeGetter implements TextMapGetter<Map<String, SQSEvent.MessageAttribute>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, SQSEvent.MessageAttribute> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, SQSEvent.MessageAttribute> map, String s) {
      SQSEvent.MessageAttribute attribute = map.get(s.toLowerCase(Locale.ROOT));
      if (attribute == null) {
        return null;
      }
      return attribute.getStringValue();
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      AwsLambdaRequest request) {

    try {
      SQSEvent event = requireCorrectEventType(request);

      attributes.put(FAAS_TRIGGER, SemanticAttributes.FaasTriggerValues.PUBSUB);
      attributes.put(FAAS_TRIGGER_TYPE, "SQS");
      attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
      attributes.put(SemanticAttributes.MESSAGING_OPERATION, "deliver");

      Optional<String> destination = commonDestination(event);
      if (destination.isPresent()) {
        attributes.put(SemanticAttributes.MESSAGING_DESTINATION_NAME, destination.get());
      }

      List<SQSMessage> messages = event.getRecords();
      if (messages.size() >= 1) {
        SQSMessage message = messages.get(0);
        // TODO python uses different attribute name
        attributes.put(RPC_REQUEST_PAYLOAD, limitedPayload(message.getBody()));
      }
    } catch (RuntimeException e) {
      logException(e, "SQSTrigger.onStart instrumentation failed");
    }
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      AwsLambdaRequest request, @Nullable Object response, @Nullable Throwable error) {
  }

  // OTEL refers to the queue/topic as "destination"
  // Based on io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.AwsLambdaSqsInstrumenterFactory#spanName
  public Optional<String> commonDestination(SQSEvent event) {
    if (event.getRecords().isEmpty()) {
      return Optional.empty();
    }

    String arn = event.getRecords().get(0).getEventSourceArn();
    for (int i = 1; i < event.getRecords().size(); i++) {
      SQSMessage message = event.getRecords().get(i);
      if (!message.getEventSourceArn().equals(arn)) {
        return Optional.empty();
      }
    }

    String[] arnSegments = arn.split(":");
    if (arnSegments.length >= 6) {
      String queueName = arnSegments[5];
      return Optional.of(queueName);
    } else {
      return Optional.of(arn);
    }
  }

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysConsumer(); // TODO this is a producer in python
  }

  private static SQSEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof SQSEvent)) {
      throw new TriggerMismatchException(
          "Expected SQSEvent. Received " + request.getInput().getClass());
    }
    return (SQSEvent) request.getInput();
  }
}
