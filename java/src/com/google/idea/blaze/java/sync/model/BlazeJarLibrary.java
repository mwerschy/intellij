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
package com.google.idea.blaze.java.sync.model;

import com.google.common.base.Objects;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.libraries.AttachedSourceJarManager;
import com.google.idea.blaze.java.libraries.JarCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import java.io.File;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** An immutable reference to a .jar required by a rule. */
@Immutable
public final class BlazeJarLibrary extends BlazeLibrary {

  private static final Logger logger = Logger.getInstance(BlazeJarLibrary.class);

  public final LibraryArtifact libraryArtifact;
  @Nullable public final TargetKey targetKey;

  public BlazeJarLibrary(LibraryArtifact libraryArtifact, @Nullable TargetKey targetKey) {
    super(LibraryKey.fromArtifactLocation(libraryArtifact.jarForIntellijLibrary()));
    this.libraryArtifact = libraryArtifact;
    this.targetKey = targetKey;
  }

  public static BlazeJarLibrary fromProto(ProjectData.BlazeLibrary proto) {
    return new BlazeJarLibrary(
        LibraryArtifact.fromProto(proto.getBlazeJarLibrary().getLibraryArtifact()),
        proto.getBlazeJarLibrary().hasTargetKey()
            ? TargetKey.fromProto(proto.getBlazeJarLibrary().getTargetKey())
            : null);
  }

  @Override
  public ProjectData.BlazeLibrary toProto() {
    ProjectData.BlazeJarLibrary.Builder builder =
        ProjectData.BlazeJarLibrary.newBuilder().setLibraryArtifact(libraryArtifact.toProto());
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTargetKey, targetKey);
    return super.toProto().toBuilder().setBlazeJarLibrary(builder).build();
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      Library.ModifiableModel libraryModel) {
    JarCache jarCache = JarCache.getInstance(project);
    File jar = jarCache.getCachedJar(artifactLocationDecoder, this);
    if (jar != null) {
      libraryModel.addRoot(pathToUrl(jar), OrderRootType.CLASSES);
    } else {
      logger.error("No local jar file found for " + libraryArtifact.jarForIntellijLibrary());
    }

    AttachedSourceJarManager sourceJarManager = AttachedSourceJarManager.getInstance(project);
    if (!sourceJarManager.hasSourceJarAttached(key)) {
      return;
    }
    for (ArtifactLocation srcJar : libraryArtifact.getSourceJars()) {
      File sourceJar = jarCache.getCachedSourceJar(artifactLocationDecoder, srcJar);
      if (sourceJar != null) {
        libraryModel.addRoot(pathToUrl(sourceJar), OrderRootType.SOURCES);
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), libraryArtifact);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeJarLibrary)) {
      return false;
    }

    BlazeJarLibrary that = (BlazeJarLibrary) other;

    return super.equals(other) && Objects.equal(libraryArtifact, that.libraryArtifact);
  }
}
