package dumb.jaider.tools;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.langchain4j.agent.tool.Tool;
import dumb.jaider.model.JaiderModel;

/** Tool to list files in JaiderModel's context. */
public final class ListContextFilesTool {
  private final JaiderModel jaiderModel;

  public ListContextFilesTool(JaiderModel jaiderModel) {
    this.jaiderModel = jaiderModel;
  }

  @Tool("Returns a list of files currently in the JaiderModel's context.")
  @CheckReturnValue
  public ImmutableList<String> listContextFiles() {
    return jaiderModel.getContextFilePaths();
  }
}
