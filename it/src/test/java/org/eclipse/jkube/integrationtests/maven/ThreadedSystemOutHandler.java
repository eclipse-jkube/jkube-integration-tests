package org.eclipse.jkube.integrationtests.maven;

import org.apache.maven.shared.invoker.SystemOutHandler;

public class ThreadedSystemOutHandler extends SystemOutHandler {
  @Override
  public void consumeLine(String line) {
    super.consumeLine(String.format("[%s] %s", Thread.currentThread().getName(), line));
  }
}
