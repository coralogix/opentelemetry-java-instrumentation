/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenterFactory;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Trigger;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.Triggers;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.triggers.ApiGatewayRestTrigger;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A base class similar to {@link RequestHandler} but will automatically trace invocations of {@link
 * #doHandleRequest(Object, Context)}. For API Gateway requests (ie of APIGatewayProxyRequestEvent
 * type parameter) also HTTP propagation can be enabled.
 */
public abstract class TracingRequestHandler<I, O> implements RequestHandler<I, O> {

  protected static final Duration DEFAULT_FLUSH_TIMEOUT = Duration.ofSeconds(1);

  private final AwsLambdaFunctionInstrumenter instrumenter;
  private final Triggers triggers;
  private final OpenTelemetrySdk openTelemetrySdk;
  private final long flushTimeoutNanos;

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of 1s when flushing at the end of an invocation.
   */
  protected TracingRequestHandler(OpenTelemetrySdk openTelemetrySdk) {
    this(openTelemetrySdk, DEFAULT_FLUSH_TIMEOUT);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation.
   */
  protected TracingRequestHandler(OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout) {
    this(
        openTelemetrySdk,
        flushTimeout,
        AwsLambdaFunctionInstrumenterFactory.createInstrumenter(openTelemetrySdk),
        new Triggers(
            new Trigger[] {
                new ApiGatewayRestTrigger()
            },
            openTelemetrySdk
        )
    );
  }

  /**
   * Creates a new {@link TracingRequestHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@link AwsLambdaFunctionInstrumenter}.
   */
  protected TracingRequestHandler(
      OpenTelemetrySdk openTelemetrySdk,
      Duration flushTimeout,
      AwsLambdaFunctionInstrumenter instrumenter,
      Triggers triggers) {
    this.openTelemetrySdk = openTelemetrySdk;
    this.flushTimeoutNanos = flushTimeout.toNanos();
    this.instrumenter = instrumenter;
    this.triggers = triggers;
  }

  @Override
  public final O handleRequest(I input, Context context) {
    System.out.println("TracingRequestHandler");
    AwsLambdaRequest request = AwsLambdaRequest.create(context, input, extractHttpHeaders(input));
    io.opentelemetry.context.Context parentContext = instrumenter.extract(request);

    if (!instrumenter.shouldStart(parentContext, request)) {
      return doHandleRequest(input, context);
    }

    try {
      return doHandleRequestInstrumentedWithTrigger(input, context, request, parentContext);
    } finally {
      LambdaUtils.forceFlush(openTelemetrySdk, flushTimeoutNanos, TimeUnit.NANOSECONDS);
    }
  }

  private O doHandleRequestInstrumentedWithTrigger(I input, Context context, AwsLambdaRequest request, io.opentelemetry.context.Context parentContext) {
    System.out.println("Event type: [" + request.getInput().getClass() + "] event value: [" + request.getInput() + "]");

    Instrumenter<AwsLambdaRequest, Object> instrumenter = triggers.getInstrumenterForRequest(request);

    if (instrumenter == null) {
      return doHandleRequestInstrumented(input, context, request, parentContext);
    }

    io.opentelemetry.context.Context otelContext = instrumenter.start(parentContext, request);
    Throwable error = null;
    O output = null;
    try (Scope ignored = otelContext.makeCurrent()) {
      output = doHandleRequestInstrumented(input, context, request, otelContext);
      return output;
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(otelContext, request, output, error);
    }
  }

  private O doHandleRequestInstrumented(I input, Context context, AwsLambdaRequest request, io.opentelemetry.context.Context parentContext) {
    io.opentelemetry.context.Context otelContext = instrumenter.start(parentContext, request);
    Throwable error = null;
    O output = null;
    try (Scope ignored = otelContext.makeCurrent()) {
      output = doHandleRequest(input, context);
      return output;
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(otelContext, request, output, error);
    }
  }

  protected abstract O doHandleRequest(I input, Context context);

  /**
   * Returns the HTTP headers for the request extracted from the request handler's input. By
   * default, returns empty headers.
   */
  protected Map<String, String> extractHttpHeaders(I input) {
    return Collections.emptyMap();
  }
}
