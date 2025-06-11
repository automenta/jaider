package dumb.jaider.tools;

import com.google.common.collect.ImmutableList;
import dumb.jaider.model.JaiderModel; // Corrected import
import dev.langchain4j.agent.tool.Tool; // Corrected import
import com.google.errorprone.annotations.CheckReturnValue;

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
