// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

/**
 * How to add a new editor:
 * 1. Add a constant field with its name
 * 2. Implement a new file manager descriptor (to customize a way to open a file manager) if necessary
 * 3. Add it to the fabric method [getByName]
 */
class FileManagerDescriptorProvider {
  private static final String NAUTILUS_EXECUTABLE_NAME = "nautilus";
  private static final String PANTHEON_EXECUTABLE_NAME = "pantheon-files";
  private static final String DOLPHIN_EXECUTABLE_NAME = "dolphin";
  private static final String DEEPIN_EXECUTABLE_NAME = "dde-file-manager";

  public static FileManagerDescriptor getByName(String executableName) {
    if (executableName.endsWith(DOLPHIN_EXECUTABLE_NAME)) {
      return new DolphinFileManager(executableName);
    }
    else if (executableName.endsWith(DEEPIN_EXECUTABLE_NAME)) {
      return new DeepinFileManager(executableName);
    }
    else if (executableName.endsWith(NAUTILUS_EXECUTABLE_NAME)) {
      return new DefaultFileManager(executableName);
    }
    else if (executableName.endsWith(PANTHEON_EXECUTABLE_NAME)) {
      return new DefaultFileManager(executableName);
    }

    return null;
  }
}

abstract class FileManagerDescriptor {
  abstract protected String[] getCommand(String selectedFileOrDirPath);

  protected String executableName;

  FileManagerDescriptor(String executableName) {
    this.executableName = executableName;
  }

  public void open(String selectedFileOrDirPath, String dir) {
    if (selectedFileOrDirPath == null) {
      ProcessSpawnHelper.spawn(executableName, dir);
      return;
    }

    ProcessSpawnHelper.spawn(getCommand(selectedFileOrDirPath));
  }
}

class DolphinFileManager extends FileManagerDescriptor {
  DolphinFileManager(String executableName) {
    super(executableName);
  }

  @Override
  protected String[] getCommand(String selectedFileOrDirPath) {
    return new String[]{executableName, "--select", selectedFileOrDirPath};
  }
}

class DeepinFileManager extends FileManagerDescriptor {
  DeepinFileManager(String executableName) {
    super(executableName);
  }

  @Override
  protected String[] getCommand(String selectedFileOrDirPath) {
    return new String[]{executableName, "--show-item", selectedFileOrDirPath};
  }
}

class DefaultFileManager extends FileManagerDescriptor {
  DefaultFileManager(String executableName) {
    super(executableName);
  }

  @Override
  protected String[] getCommand(String selectedFileOrDirPath) {
    return new String[]{executableName, selectedFileOrDirPath};
  }
}
