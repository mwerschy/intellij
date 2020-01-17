/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.execution.CidrCommandLineState;
import com.jetbrains.cidr.execution.CidrRunner;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A version of CPPRunner which can accept {@link BlazeCommandRunConfiguration} when appropriate.
 */
public class BlazeCppRunner extends CidrRunner {

  @Override
  public String getRunnerId() {
    return "BlazeCppAppRunner";
  }

  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    return profile instanceof BlazeCommandRunConfiguration
        && RunConfigurationUtils.canUseClionRunner((BlazeCommandRunConfiguration) profile);
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) {
    CidrRunner.triggerUsage(env.getRunnerAndConfigurationSettings());

    if (Objects.equals(env.getExecutor().getId(), "Debug")) {
      XDebugSession debugSession = this.startDebugSession((CidrCommandLineState) state, env, false);
      return debugSession.getRunContentDescriptor();
    }

    return super.doExecute(state, env);
  }
}
