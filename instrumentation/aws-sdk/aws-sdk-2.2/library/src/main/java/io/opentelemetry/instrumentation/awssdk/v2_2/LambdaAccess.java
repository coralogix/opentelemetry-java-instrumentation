/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import software.amazon.awssdk.core.SdkRequest;
import javax.annotation.Nullable;

final class LambdaAccess {
  private LambdaAccess() {}

  private static final boolean enabled = PluginImplUtil.isImplPresent("LambdaImpl");

  @Nullable
  @NoMuzzle
  public static SdkRequest modifyRequest(
      SdkRequest request, Context otelContext, TextMapPropagator messagingPropagator) {
    System.out.println("LambdaAccess.modifyRequest: enabled: "+ enabled);
    return enabled ? LambdaImpl.modifyRequest(request, otelContext, messagingPropagator) : null;
  }
}
