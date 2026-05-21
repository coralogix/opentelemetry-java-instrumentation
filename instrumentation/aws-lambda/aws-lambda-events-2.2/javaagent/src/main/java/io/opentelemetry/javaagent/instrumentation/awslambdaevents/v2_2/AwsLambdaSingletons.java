/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Triggers;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrapperConfiguration;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaEventsInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.ApiGatewayHttpTrigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.ApiGatewayRestTrigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.DynamoDBTrigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.S3Trigger;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.triggers.SqsTrigger;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.time.Duration;

public final class AwsLambdaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-lambda-events-2.2";
  private static final Triggers TRIGGERS =
      new Triggers(
          new Trigger[] {
            new ApiGatewayRestTrigger(),
            new ApiGatewayHttpTrigger(),
            new S3Trigger(),
            new SqsTrigger(),
            new DynamoDBTrigger(),
          },
          GlobalOpenTelemetry.get());
  private static final AwsLambdaFunctionInstrumenter FUNCTION_INSTRUMENTER =
      AwsLambdaEventsInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(),
          INSTRUMENTATION_NAME,
          AgentCommonConfig.get().getKnownHttpRequestMethods());
  private static final Duration FLUSH_TIMEOUT =
      Duration.ofMillis(
          AgentInstrumentationConfig.get()
              .getLong(
                  "otel.instrumentation.aws-lambda.flush-timeout",
                  WrapperConfiguration.OTEL_LAMBDA_FLUSH_TIMEOUT_DEFAULT.toMillis()));

  public static Triggers getTriggers() {
    return TRIGGERS;
  }

  public static AwsLambdaFunctionInstrumenter functionInstrumenter() {
    return FUNCTION_INSTRUMENTER;
  }

  public static Duration flushTimeout() {
    return FLUSH_TIMEOUT;
  }

  private AwsLambdaSingletons() {}
}
