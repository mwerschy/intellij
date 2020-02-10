package cpp.src.com.google.idea.blaze.cpp;

import com.google.idea.blaze.cpp.PatchUtils;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.Project;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

public class PatchClass {
  // https://github.com/JetBrains/intellij-community/blob/6f5104f1a40b09c2f532c3e0ec730a463d4434b6/platform/configuration-store-impl/src/ProjectStoreBase.kt#L90
  @PatchUtils.Patch(value = "com.intellij.openapi.project.impl.ProjectImpl")
  public static String getBasePath(@NotNull Project self) {
    IProjectStore projectStore = ((IProjectStore) ((ProjectStoreOwner) self).getComponentStore());
    String basePath = projectStore.getProjectBasePath();
    if (PathUtil.getFileName(basePath).equals(".clwb")) return PathUtil.getParentPath(basePath);
    return basePath;
  }
}
