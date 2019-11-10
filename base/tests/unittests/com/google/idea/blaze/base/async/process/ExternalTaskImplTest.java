/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async.process;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.idea.blaze.base.async.process.ExternalTask.ExternalTaskImpl;
import java.io.File;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExternalTaskImpl}. */
@RunWith(JUnit4.class)
public final class ExternalTaskImplTest {

  @Test
  public void getCustomBinary_withoutCustomPath() throws Exception {
    System.clearProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY);
    assertThat(ExternalTaskImpl.getCustomBinary("sh")).isEmpty();
  }

  @Test
  public void getCustomBinary_multiArgCommand() throws Exception {
    System.setProperty(
        ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, System.getProperty("java.io.tmpdir"));
    File temp = File.createTempFile("sadjfhjk-", "-sodiuflk");
    temp.deleteOnExit();
    assertThat(ExternalTaskImpl.getCustomBinary(temp.getName() + " --withlog")).isEmpty();
  }

  @Test
  public void getCustomBinary_fullPathCommand() throws Exception {
    System.setProperty(
        ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, System.getProperty("java.io.tmpdir"));
    File temp = File.createTempFile("sadjfhjk-", "-sodiuflk");
    temp.deleteOnExit();
    assertThat(ExternalTaskImpl.getCustomBinary(temp.getAbsolutePath())).isEmpty();
  }

  @Test
  public void getCustomBinary_nonExistent() throws Exception {
    System.setProperty(
        ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, System.getProperty("java.io.tmpdir"));
    assertThat(ExternalTaskImpl.getCustomBinary("this_is_almost_certainly_not_an_existing_file"))
        .isEmpty();
  }

  @Test
  public void getCustomBinary_directory() throws Exception {
    File tmpDir = new File(System.getProperty("java.io.tmpdir"));
    assertThat(tmpDir.exists()).isTrue();
    assertThat(tmpDir.isFile()).isFalse();
    System.setProperty(
        ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, tmpDir.getParentFile().getAbsolutePath());
    assertThat(ExternalTaskImpl.getCustomBinary(tmpDir.getName())).isEmpty();
  }

  @Test
  public void getCustomBinary_success() throws Exception {
    System.setProperty(
        ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, System.getProperty("java.io.tmpdir"));
    File temp = File.createTempFile("sadjfhjk-", "-sodiuflk");
    temp.deleteOnExit();
    Optional<File> fileOr = ExternalTaskImpl.getCustomBinary(temp.getName());
    assertThat(fileOr).isPresent();
    assertThat(fileOr.get().getAbsolutePath()).isEqualTo(temp.getAbsolutePath());
  }
}
