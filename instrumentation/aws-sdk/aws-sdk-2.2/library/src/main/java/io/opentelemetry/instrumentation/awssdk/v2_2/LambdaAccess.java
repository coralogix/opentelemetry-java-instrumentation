/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;

final class LambdaAccess {
  private LambdaAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("LambdaImpl");

  @Nullable
  @NoMuzzle
  public static SdkRequest modifyRequest(
      SdkRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    return enabled ? LambdaImpl.modifyRequest(request, otelContext, messagingPropagator) : null;
  }
}
