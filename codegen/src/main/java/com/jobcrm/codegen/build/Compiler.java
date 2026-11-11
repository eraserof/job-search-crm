package com.jobcrm.codegen.build;

/** Compiles the whole repo (main and test sources) and reports the outcome. */
public interface Compiler {

  CompileResult compile();

  /**
   * @param ok true when the build exited 0
   * @param output combined stdout and stderr of the build
   */
  record CompileResult(boolean ok, String output) {}
}
