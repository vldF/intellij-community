// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.util.Arrays;

public class ProcessSpawnHelper {
  private static final Logger LOG = Logger.getInstance(ProcessSpawnHelper.class);

  static void spawn(String... command) {
    if (LOG.isDebugEnabled()) LOG.debug(Arrays.toString(command));

    ProcessIOExecutorService.INSTANCE.execute(() -> {
      try {
        CapturingProcessHandler handler;
        if (SystemInfo.isWindows) {
          assert command.length == 1 : Arrays.toString(command);
          Process process = Runtime.getRuntime().exec(command[0]);  // no quoting/escaping is needed
          handler = new CapturingProcessHandler.Silent(process, null, command[0]);
        }
        else {
          handler = new CapturingProcessHandler.Silent(new GeneralCommandLine(command));
        }
        handler.runProcess(10000, false).checkSuccess(LOG);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    });
  }
}
