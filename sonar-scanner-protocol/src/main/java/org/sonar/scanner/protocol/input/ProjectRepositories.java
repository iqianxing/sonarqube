/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.protocol.input;

import java.util.Date;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * Container for all project data going from server to batch.
 * This is not an API since server and batch always share the same version.
 */
public abstract class ProjectRepositories {

  private long timestamp;

  private Date lastAnalysisDate;

  public long timestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  @CheckForNull
  public Date lastAnalysisDate() {
    return lastAnalysisDate;
  }

  public void setLastAnalysisDate(@Nullable Date lastAnalysisDate) {
    this.lastAnalysisDate = lastAnalysisDate;
  }
}
