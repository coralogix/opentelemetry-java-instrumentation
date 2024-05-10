package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;
import static io.opentelemetry.semconv.SemanticAttributes.FAAS_TRIGGER;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KinesisTrigger extends Trigger {

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof KinesisEvent;
  }

  // https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/messaging/messaging-spans.md#span-name
  // TODO python uses a different name
  @Override
  public String extract(AwsLambdaRequest request) {
    KinesisEvent event = requireCorrectEventType(request);
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
    ObjectMapper objectMapper = new ObjectMapper();

    KinesisEvent event = requireCorrectEventType(request);

    for (KinesisEventRecord record : event.getRecords()) {
      try {
        byte[] jsonBytes = decodeBase64IfNecessary(record.getKinesis().getData());
        JsonNode jsonNode = objectMapper.readTree(jsonBytes);
        JsonNode contextJson = jsonNode.findValue("_context");

        if (contextJson != null) {
          TextMapPropagator propagator = GlobalOpenTelemetry.get().getPropagators()
              .getTextMapPropagator();
          Context root = Context.root();
          Context context = propagator.extract(root, contextJson, JsonNodeGetter.INSTANCE);
          if (context != root) {
            SpanContext messageSpanCtx = Span.fromContext(context).getSpanContext();
            if (messageSpanCtx.isValid()) {
              spanLinks.addLink(messageSpanCtx);
            }
          }
        }
      } catch (Exception e) {
        // We optimistically assume that the payload is a JSON. If it's not, we just ignore that record.
      }
    }
  }

  private static byte[] decodeBase64IfNecessary(ByteBuffer data) {
    try {
      return decodeBase64(data);
    } catch (IllegalArgumentException e) {
      byte[] byteArray = new byte[data.remaining()];
      data.get(byteArray);
      return byteArray;
    }
  }

  private static byte[] decodeBase64(ByteBuffer data) {
    return Base64.getDecoder().decode(data).array();
  }

  enum JsonNodeGetter implements TextMapGetter<JsonNode> {
    INSTANCE;

    @Override
    public Iterable<String> keys(JsonNode node) {
      return node::fieldNames;
    }

    @Override
    public String get(JsonNode node, String s) {
      if (node == null) {
        return null;
      }
      JsonNode value = node.get(s);
      if (value == null) {
        return null;
      }
      return value.asText();
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      AwsLambdaRequest request) {

    try {
      KinesisEvent event = requireCorrectEventType(request);

      attributes.put(FAAS_TRIGGER, SemanticAttributes.FaasTriggerValues.PUBSUB);
      attributes.put(FAAS_TRIGGER_TYPE, "Kinesis");
      attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "Kinesis");
      attributes.put(SemanticAttributes.MESSAGING_OPERATION, "deliver");

      Optional<String> destination = commonDestination(event);
      if (destination.isPresent()) {
        attributes.put(SemanticAttributes.MESSAGING_DESTINATION_NAME, destination.get());
      }

      // Not adding the payload as an attribute for now. Python doesn't do that either.
//      List<KinesisEventRecord> records = event.getRecords();
//      if (records.size() >= 1) {
//        KinesisEventRecord record = records.get(0);
//        // TODO python uses different attribute name
//        // TODO add attributes for partition, sequence number etc
//        byte[] jsonBytes = decodeBase64IfNecessary(record);
//        attributes.put(RPC_REQUEST_PAYLOAD, limitedPayload(new String(jsonBytes, StandardCharsets.UTF_8)));
//      }
    } catch (RuntimeException e) {
      logException(e, "KinesisTrigger.onStart instrumentation failed");
    }
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      AwsLambdaRequest request, @Nullable Object response, @Nullable Throwable error) {
  }

  // OTEL refers to the queue/topic as "destination"
  // Based on io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.AwsLambdaSqsInstrumenterFactory#spanName
  public Optional<String> commonDestination(KinesisEvent event) {
    if (event.getRecords().isEmpty()) {
      return Optional.empty();
    }

    String arn = event.getRecords().get(0).getEventSourceARN();
    if (arn == null) {
      return Optional.empty();
    }
    for (int i = 1; i < event.getRecords().size(); i++) {
      KinesisEventRecord record = event.getRecords().get(i);
      String messageArn = record.getEventSourceARN();
      if (messageArn == null || !record.getEventSourceARN().equals(arn)) {
        return Optional.empty();
      }
    }

    String[] arnSegments = arn.split(":");
    if (arnSegments.length >= 6) {
      // arnSegments[5] looks like this: "stream/my-stream-name"
      String[] stream = arnSegments[5].split("/");
      if (stream.length >= 2) {
        return Optional.of(stream[1]);
      } else {
        return Optional.of(arnSegments[5]);
      }
    } else {
      return Optional.of(arn);
    }
  }

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysConsumer(); // TODO this is a producer in python
  }

  private static KinesisEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof KinesisEvent)) {
      throw new TriggerMismatchException(
          "Expected KinesisEvent. Received " + request.getInput().getClass());
    }
    return (KinesisEvent) request.getInput();
  }
}
