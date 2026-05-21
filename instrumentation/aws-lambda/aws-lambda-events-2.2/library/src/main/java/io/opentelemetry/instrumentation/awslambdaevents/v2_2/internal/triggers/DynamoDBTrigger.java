/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.FAAS_TRIGGER_TYPE;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.RPC_REQUEST_PAYLOAD;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.limitedPayload;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.TriggerUtils.logException;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
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
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("IdentifierName")
public final class DynamoDBTrigger extends Trigger {

  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");

  @Override
  public boolean matches(AwsLambdaRequest request) {
    return request.getInput() instanceof DynamodbEvent;
  }

  /**
   * Note taken from SqsTrigger:
   * https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/messaging/messaging-spans.md#span-name
   * TODO python uses a different name
   *
   * <p>This means that the Python SDK handles it in a different way and we need to do the same here
   * for consistency.
   */
  @Override
  public String extract(AwsLambdaRequest request) {
    DynamodbEvent req = requireCorrectEventType(request);
    if (req.getRecords().size() == 1) {
      DynamodbStreamRecord record = req.getRecords().get(0);
      return record.getEventName();
    } else {
      return "dynamodb multi trigger";
    }
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
      DynamodbEvent event = requireCorrectEventType(request);

      attributes.put(FAAS_TRIGGER, "datasource");
      attributes.put(FAAS_TRIGGER_TYPE, "DynamoDB");
      List<DynamodbStreamRecord> records = event.getRecords();
      // With DynamoDB trigger we always expect the list to contain exactly one record
      if (records.size() == 1) {
        DynamodbStreamRecord record = records.get(0);
        attributes.put(
            RPC_REQUEST_PAYLOAD, limitedPayload(SerializationUtil.toJson(record)));
      }
    } catch (RuntimeException e) {
      logException(e, "DynamoDBTrigger.onStart instrumentation failed");
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

  private static DynamodbEvent requireCorrectEventType(AwsLambdaRequest request) {
    if (!(request.getInput() instanceof DynamodbEvent)) {
      throw new TriggerMismatchException(
          "Expected DynamodbEvent. Received " + request.getInput().getClass());
    }
    return (DynamodbEvent) request.getInput();
  }
}
