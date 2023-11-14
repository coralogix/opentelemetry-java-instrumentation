package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerMismatchException;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;
import java.util.List;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;
import static io.opentelemetry.semconv.SemanticAttributes.FAAS_TRIGGER;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class S3Trigger extends Trigger {

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof S3Event;
  }

  @Override
  public String extract(AwsLambdaRequest request) {
    S3Event req = requireCorrectEventType(request);
    // With S3 trigger we always expect the list to contain exactly one record
    if (req.getRecords().size() == 1) {
      S3EventNotificationRecord record = req.getRecords().get(0);
      return record.getEventName();
    } else {
      return "s3 multi trigger";
    }
  }

  @Override
  public void extract(SpanStatusBuilder spanStatusBuilder, AwsLambdaRequest request,
      @Nullable Object response, @Nullable Throwable error) {
    if (error != null) {
      spanStatusBuilder.setStatus(StatusCode.ERROR);
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext,
      AwsLambdaRequest request) {

    try {
      ObjectMapper objectMapper = new ObjectMapper();

      S3Event event = requireCorrectEventType(request);

      attributes.put(FAAS_TRIGGER, SemanticAttributes.FaasTriggerValues.DATASOURCE);
      attributes.put(FAAS_TRIGGER_TYPE, "S3");
      List<S3EventNotificationRecord> records = event.getRecords();
      // With S3 trigger we always expect the list to contain exactly one record
      if (records.size() == 1) {
        S3EventNotificationRecord record = records.get(0);
        // TODO python uses different attribute name. node.js doesn't provide payload at all, but it provides extra attributes.
        attributes.put(RPC_REQUEST_PAYLOAD,
            limitedPayload(objectMapper.writeValueAsString(record)));
      }
    } catch (RuntimeException | JsonProcessingException e) {
      logException(e, "S3Trigger.onStart instrumentation failed");
    }
  }

  @Override
  public void onEnd(AttributesBuilder attributes, Context context,
      AwsLambdaRequest request, @Nullable Object response, @Nullable Throwable error) {
  }

  @Override
  public SpanKindExtractor<AwsLambdaRequest> spanKindExtractor() {
    return SpanKindExtractor.alwaysServer(); // TODO this is producer in python and server in nodejs
  }

  private static S3Event requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof S3Event)) {
      throw new TriggerMismatchException(
          "Expected S3Event. Received " + request.getInput().getClass());
    }
    return (S3Event) request.getInput();
  }
}
