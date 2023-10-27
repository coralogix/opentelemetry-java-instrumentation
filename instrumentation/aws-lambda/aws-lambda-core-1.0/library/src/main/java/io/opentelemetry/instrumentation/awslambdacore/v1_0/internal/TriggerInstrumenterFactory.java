/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.trigger.ApiGatewayRestTrigger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class TriggerInstrumenterFactory {

  public static TriggerInstrumenter createInstrumenter(OpenTelemetry openTelemetry) {
    return new TriggerInstrumenter(
        Instrumenter.builder(
                openTelemetry,
                "io.opentelemetry.aws-lambda-core-1.0",
                TriggerInstrumenterFactory::spanName)
            .addAttributesExtractor(new TriggerAttributesExtractor())
            .setSpanStatusExtractor()
            .buildInstrumenter(SpanKindExtractor.alwaysServer()));
  }

  private static String spanName(AwsLambdaRequest input) {
    return ApiGatewayRestTrigger.maybeSpanName(input)
        .orElseGet(() -> "trigger" + input.getAwsContext().getFunctionName());
  }

  private TriggerInstrumenterFactory() {}
}
