/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.trigger.ApiGatewayRestTrigger;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class TriggerAttributesExtractor
    implements AttributesExtractor<AwsLambdaRequest, Object> {

  @Override
  public void onStart(
      AttributesBuilder attributes,
      io.opentelemetry.context.Context parentContext,
      AwsLambdaRequest request) {
      System.out.println("Event type: [" + request.getInput().getClass() + "] event value: [" + request.getInput() + "]");
      if (ApiGatewayRestTrigger.matches(request)) {
        System.out.println("API Gateway REST trigger");
        ApiGatewayRestTrigger.addStartAttributes(attributes, request);
      }
//    Context awsContext = request.getAwsContext();
//    attributes.put(FAAS_INVOCATION_ID, awsContext.getAwsRequestId());
//    String arn = getFunctionArn(awsContext);
//    if (arn != null) {
//      attributes.put(CLOUD_RESOURCE_ID, arn);
//      attributes.put(CLOUD_ACCOUNT_ID, getAccountId(arn));
//    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      io.opentelemetry.context.Context context,
      AwsLambdaRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {
    if (ApiGatewayRestTrigger.matches(request)) {
      System.out.println("API Gateway REST trigger");
      ApiGatewayRestTrigger.addEndAttributes(attributes, request, response);
    }
  }
}
