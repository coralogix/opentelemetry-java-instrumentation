/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.faas.internal.FaasExceptionEventExtractors.setFaasInvocationExceptionEventExtractor;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionAttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import java.util.Set;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.CxAttributes.SPAN_ROLE;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaEventsInstrumenterFactory {

  public static AwsLambdaFunctionInstrumenter createInstrumenter(
      OpenTelemetry openTelemetry, String instrumentationName, Set<String> knownMethods) {
    InstrumenterBuilder<AwsLambdaRequest, Object> builder =
        Instrumenter.<AwsLambdaRequest, Object>builder(
                openTelemetry, instrumentationName, AwsLambdaEventsInstrumenterFactory::spanName)
            .addAttributesExtractor(new AwsLambdaFunctionAttributesExtractor())
            .addAttributesExtractor(AttributesExtractor.constant(SPAN_ROLE, "invocation"))
            .addAttributesExtractor(new ApiGatewayProxyAttributesExtractor(knownMethods));
    setFaasInvocationExceptionEventExtractor(builder);

    return new AwsLambdaFunctionInstrumenter(
        openTelemetry, builder.buildInstrumenter(SpanKindExtractor.alwaysServer()));
  }

  private static String spanName(AwsLambdaRequest input) {
    return input.getAwsContext().getFunctionName();
  }

  private AwsLambdaEventsInstrumenterFactory() {}
}
