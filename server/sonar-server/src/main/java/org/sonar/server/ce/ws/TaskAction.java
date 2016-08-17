/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.server.ce.ws;

import com.google.common.base.Optional;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.util.Uuids;
import org.sonar.core.util.stream.Collectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.WsCe;

import static org.sonar.core.permission.GlobalPermissions.SCAN_EXECUTION;
import static org.sonar.core.permission.GlobalPermissions.SYSTEM_ADMIN;
import static org.sonar.server.user.AbstractUserSession.insufficientPrivilegesException;
import static org.sonar.server.ws.WsUtils.writeProtobuf;

public class TaskAction implements CeWsAction {

  public static final String ACTION = "task";
  public static final String PARAM_TASK_UUID = "id";

  private static final String PARAM_ADDITIONAL_FIELDS = "additionalFields";

  private final DbClient dbClient;
  private final TaskFormatter wsTaskFormatter;
  private final UserSession userSession;

  public TaskAction(DbClient dbClient, TaskFormatter wsTaskFormatter, UserSession userSession) {
    this.dbClient = dbClient;
    this.wsTaskFormatter = wsTaskFormatter;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION)
      .setDescription("Give Compute Engine task details such as type, status, duration and associated component.<br />" +
        "Requires 'Administer System' or 'Execute Analysis' permission.<br/>" +
        "Since 6.1, field \"logs\" is deprecated and its value will always be false")
      .setResponseExample(getClass().getResource("task-example.json"))
      .setSince("5.2")
      .setHandler(this);

    action
      .createParam(PARAM_TASK_UUID)
      .setRequired(true)
      .setDescription("Id of task")
      .setExampleValue(Uuids.UUID_EXAMPLE_01);
    action.createParam(PARAM_ADDITIONAL_FIELDS)
      .setSince("6.1")
      .setDescription("Comma-separated list of the optional fields to be returned in response.")
      .setPossibleValues(AdditionalField.possibleValues());
  }

  @Override
  public void handle(Request wsRequest, Response wsResponse) throws Exception {
    String taskUuid = wsRequest.mandatoryParam(PARAM_TASK_UUID);
    DbSession dbSession = dbClient.openSession(false);
    try {
      WsCe.TaskResponse.Builder wsTaskResponse = WsCe.TaskResponse.newBuilder();
      Optional<CeQueueDto> queueDto = dbClient.ceQueueDao().selectByUuid(dbSession, taskUuid);
      if (queueDto.isPresent()) {
        checkPermission(queueDto.get().getComponentUuid());
        wsTaskResponse.setTask(wsTaskFormatter.formatQueue(dbSession, queueDto.get()));
      } else {
        Optional<CeActivityDto> activityDto = dbClient.ceActivityDao().selectByUuid(dbSession, taskUuid);
        if (activityDto.isPresent()) {
          CeActivityDto ceActivityDto = activityDto.get();
          checkPermission(ceActivityDto.getComponentUuid());
          maskErrorStacktrace(wsRequest, ceActivityDto);
          wsTaskResponse.setTask(wsTaskFormatter.formatActivity(dbSession, ceActivityDto));
        } else {
          throw new NotFoundException();
        }
      }
      writeProtobuf(wsTaskResponse.build(), wsRequest, wsResponse);
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private void checkPermission(@Nullable String projectUuid) {
    if (!userSession.hasPermission(SYSTEM_ADMIN)
      && !userSession.hasPermission(SCAN_EXECUTION)
      && (projectUuid == null || !userSession.hasComponentUuidPermission(SCAN_EXECUTION, projectUuid))) {
      throw insufficientPrivilegesException();
    }
  }

  private static void maskErrorStacktrace(Request wsRequest, CeActivityDto ceActivityDto) {
    Set<AdditionalField> fromRequest = TaskAction.AdditionalField.getFromRequest(wsRequest);
    if (!fromRequest.contains(TaskAction.AdditionalField.STACKTRACE)) {
      ceActivityDto.setErrorStacktrace(null);
    }
  }

  private enum AdditionalField {
    STACKTRACE;

    public static Set<AdditionalField> getFromRequest(Request wsRequest) {
      List<String> strings = wsRequest.paramAsStrings(PARAM_ADDITIONAL_FIELDS);
      if (strings == null) {
        return Collections.emptySet();
      }
      return strings.stream()
        .map(s -> {
          for (AdditionalField field : AdditionalField.values()) {
            if (field.name().equalsIgnoreCase(s)) {
              return field;
            }
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    }

    public static Collection<String> possibleValues() {
      return Arrays.stream(values())
        .map(Enum::name)
        .map(s -> s.toLowerCase(Locale.ENGLISH))
        .collect(Collectors.toList(values().length));
    }
  }
}
