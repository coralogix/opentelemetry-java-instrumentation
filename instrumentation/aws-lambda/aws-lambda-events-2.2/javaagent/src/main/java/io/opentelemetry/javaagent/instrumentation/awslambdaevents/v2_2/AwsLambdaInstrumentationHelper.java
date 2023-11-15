/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Triggers;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.ApiGatewayHttpTrigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.ApiGatewayRestTrigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.AwsLambdaEventsInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.S3Trigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.SqsTrigger;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class AwsLambdaInstrumentationHelper {

  private static final Triggers TRIGGERS = new Triggers(
      new Trigger[] {
          new ApiGatewayRestTrigger(),
          new ApiGatewayHttpTrigger(),
          new S3Trigger(),
          new SqsTrigger(),
      },
      GlobalOpenTelemetry.get()
  );

  public static Triggers getTriggers() {
    return TRIGGERS;
  }

  private static final io.opentelemetry.instrumentation.awslambdacore.v1_0.internal
      .AwsLambdaFunctionInstrumenter
      FUNCTION_INSTRUMENTER =
      AwsLambdaEventsInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(), CommonConfig.get().getKnownHttpRequestMethods());

  public static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal
      .AwsLambdaFunctionInstrumenter
  functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  private AwsLambdaInstrumentationHelper() {}
}
