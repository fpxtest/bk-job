/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.execute.engine.listener;

import com.tencent.bk.job.common.util.date.DateUtils;
import com.tencent.bk.job.execute.common.constants.RunStatusEnum;
import com.tencent.bk.job.execute.common.constants.StepExecuteTypeEnum;
import com.tencent.bk.job.execute.common.util.TaskCostCalculator;
import com.tencent.bk.job.execute.engine.listener.event.StepEvent;
import com.tencent.bk.job.execute.engine.listener.event.TaskExecuteMQEventDispatcher;
import com.tencent.bk.job.execute.engine.message.StepProcessor;
import com.tencent.bk.job.execute.engine.prepare.FilePrepareService;
import com.tencent.bk.job.execute.model.GseTaskDTO;
import com.tencent.bk.job.execute.model.StepInstanceBaseDTO;
import com.tencent.bk.job.execute.model.TaskInstanceDTO;
import com.tencent.bk.job.execute.model.TaskInstanceRollingConfigDTO;
import com.tencent.bk.job.execute.service.GseTaskService;
import com.tencent.bk.job.execute.service.NotifyService;
import com.tencent.bk.job.execute.service.RollingConfigService;
import com.tencent.bk.job.execute.service.StepInstanceRollingTaskService;
import com.tencent.bk.job.execute.service.StepInstanceService;
import com.tencent.bk.job.execute.service.TaskInstanceService;
import com.tencent.bk.job.manage.common.consts.task.TaskStepTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.stereotype.Component;

import static com.tencent.bk.job.execute.common.constants.StepExecuteTypeEnum.EXECUTE_SCRIPT;
import static com.tencent.bk.job.execute.common.constants.StepExecuteTypeEnum.EXECUTE_SQL;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.CLEAR;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.CONFIRM_CONTINUE;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.CONFIRM_RESTART;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.CONFIRM_TERMINATE;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.CONTINUE_FILE_PUSH;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.IGNORE_ERROR;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.NEXT_STEP;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.REFRESH;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.RETRY_ALL;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.RETRY_FAIL;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.SKIP;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.START;
import static com.tencent.bk.job.execute.engine.consts.StepActionEnum.STOP;

/**
 * 执行引擎事件处理-步骤
 */
@Component
@EnableBinding({StepProcessor.class})
@Slf4j
public class StepListener {
    private final TaskInstanceService taskInstanceService;
    private final StepInstanceService stepInstanceService;
    private final TaskExecuteMQEventDispatcher taskExecuteMQEventDispatcher;
    private final FilePrepareService filePrepareService;
    private final GseTaskService gseTaskService;
    private final RollingConfigService rollingConfigService;
    private final StepInstanceRollingTaskService stepInstanceRollingTaskService;
    private final NotifyService notifyService;

    @Autowired
    public StepListener(TaskInstanceService taskInstanceService,
                        StepInstanceService stepInstanceService,
                        TaskExecuteMQEventDispatcher TaskExecuteMQEventDispatcher,
                        FilePrepareService filePrepareService,
                        GseTaskService gseTaskService,
                        RollingConfigService rollingConfigService,
                        StepInstanceRollingTaskService stepInstanceRollingTaskService,
                        NotifyService notifyService) {
        this.taskInstanceService = taskInstanceService;
        this.stepInstanceService = stepInstanceService;
        this.taskExecuteMQEventDispatcher = TaskExecuteMQEventDispatcher;
        this.filePrepareService = filePrepareService;
        this.gseTaskService = gseTaskService;
        this.rollingConfigService = rollingConfigService;
        this.stepInstanceRollingTaskService = stepInstanceRollingTaskService;
        this.notifyService = notifyService;
    }

    /**
     * 处理步骤执行相关的事件
     *
     * @param stepEvent 步骤执行相关的事件
     */
    @StreamListener(StepProcessor.INPUT)
    public void handleEvent(StepEvent stepEvent) {
        log.info("Handle step event: {}", stepEvent);
        long stepInstanceId = stepEvent.getStepInstanceId();
        try {
            int action = stepEvent.getAction();
            StepInstanceBaseDTO stepInstance = taskInstanceService.getBaseStepInstance(stepInstanceId);
            if (START.getValue() == action) {
                log.info("Start step, stepInstanceId={}", stepInstanceId);
                startStep(stepInstance);
            } else if (SKIP.getValue() == action) {
                TaskInstanceDTO taskInstance = taskInstanceService.getTaskInstance(stepInstance.getTaskInstanceId());
                if (taskInstance.getCurrentStepInstanceId() == stepInstanceId) {
                    log.info("Skip step, stepInstanceId={}", stepInstanceId);
                    skipStep(stepInstance);
                } else {
                    log.warn("Only current running step is support for skipping, stepInstanceId={}", stepInstanceId);
                }
            } else if (RETRY_FAIL.getValue() == action) {
                log.info("Retry step fail, stepInstanceId={}", stepInstanceId);
                retryStepFail(stepInstance);
            } else if (RETRY_ALL.getValue() == action) {
                log.info("Retry step all, stepInstanceId={}", stepInstanceId);
                retryStepAll(stepInstance);
            } else if (STOP.getValue() == action) {
                log.info("Force stop step, stepInstanceId={}", stepInstanceId);
                stopStep(stepInstance);
            } else if (IGNORE_ERROR.getValue() == action) {
                log.info("Ignore step error, stepInstanceId={}", stepInstanceId);
                ignoreError(stepInstance);
            } else if (NEXT_STEP.getValue() == action) {
                log.info("Next step, stepInstanceId={}", stepInstanceId);
                nextStep(stepInstance);
            } else if (CONFIRM_TERMINATE.getValue() == action) {
                log.info("Confirm step terminate, stepInstanceId={}", stepInstanceId);
                confirmStepTerminate(stepInstance);
            } else if (CONFIRM_RESTART.getValue() == action) {
                log.info("Confirm step restart, stepInstanceId={}", stepInstanceId);
                confirmStepRestart(stepInstance);
            } else if (CONFIRM_CONTINUE.getValue() == action) {
                log.info("Confirm step continue, stepInstanceId={}", stepInstanceId);
                confirmStepContinue(stepInstance);
            } else if (CONTINUE_FILE_PUSH.getValue() == action) {
                log.info("Continue file push step, stepInstanceId={}", stepInstanceId);
                continueGseFileStep(stepInstance);
            } else if (CLEAR.getValue() == action) {
                log.info("Clear step, stepInstanceId={}", stepInstanceId);
                clearStep(stepInstance);
            } else if (REFRESH.getValue() == action) {
                log.info("Refresh step, stepInstanceId: {}, action:{}", stepInstanceId, action);
                refreshStep(stepInstance);
            } else {
                log.error("Error step action:{}", action);
            }
        } catch (Exception e) {
            String errorMsg = "Handling step event error,stepInstanceId:" + stepInstanceId;
            log.warn(errorMsg, e);
        }

    }

    private void confirmStepTerminate(StepInstanceBaseDTO stepInstance) {
        long stepInstanceId = stepInstance.getId();
        TaskInstanceDTO taskInstance = taskInstanceService.getTaskInstance(stepInstance.getTaskInstanceId());
        if (RunStatusEnum.WAITING.getValue().equals(stepInstance.getStatus())) {
            Long endTime = DateUtils.currentTimeMillis();
            long taskTotalTime = TaskCostCalculator.calculate(taskInstance.getStartTime(), endTime,
                taskInstance.getTotalTime());
            taskInstanceService.updateTaskExecutionInfo(taskInstance.getId(), RunStatusEnum.CONFIRM_TERMINATED, null,
                null, endTime, taskTotalTime);
            long stepTotalTime = TaskCostCalculator.calculate(stepInstance.getStartTime(), endTime,
                stepInstance.getTotalTime());
            taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.CONFIRM_TERMINATED, null,
                endTime, stepTotalTime);
        } else {
            log.warn("Unsupported step instance status for confirm step terminate action, stepInstanceId:{}, " +
                "status:{}", stepInstanceId, stepInstance.getStatus());
        }
    }

    private void confirmStepRestart(StepInstanceBaseDTO stepInstance) {
        long stepInstanceId = stepInstance.getId();
        if (RunStatusEnum.CONFIRM_TERMINATED.getValue().equals(stepInstance.getStatus())) {
            executeConfirmStep(stepInstance);
        } else {
            log.warn("Unsupported step instance status for confirm-step-restart action, stepInstanceId:{}, status:{}"
                , stepInstanceId, stepInstance.getStatus());
        }
    }

    private void nextStep(StepInstanceBaseDTO stepInstance) {
        long taskInstanceId = stepInstance.getTaskInstanceId();
        long stepInstanceId = stepInstance.getId();
        int stepStatus = stepInstance.getStatus();

        if (RunStatusEnum.STOP_SUCCESS.getValue() == stepStatus) {
            taskInstanceService.updateTaskStatus(taskInstanceId, RunStatusEnum.RUNNING.getValue());
            long endTime = DateUtils.currentTimeMillis();
            long totalTime = TaskCostCalculator.calculate(stepInstance.getStartTime(), endTime,
                stepInstance.getTotalTime());
            // 终止成功，进入下一步，该步骤设置为“跳过”
            taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.SKIPPED, null, endTime,
                totalTime);
            taskExecuteMQEventDispatcher.refreshJob(taskInstanceId);
        } else {
            log.warn("Unsupported step instance status for next step action, stepInstanceId:{}, status:{}",
                stepInstanceId, stepInstance.getStatus());
        }
    }

    private void confirmStepContinue(StepInstanceBaseDTO stepInstance) {
        long taskInstanceId = stepInstance.getTaskInstanceId();
        long stepInstanceId = stepInstance.getId();
        int stepStatus = stepInstance.getStatus();

        if (RunStatusEnum.WAITING.getValue() == stepStatus) {
            taskInstanceService.updateTaskStatus(taskInstanceId, RunStatusEnum.RUNNING.getValue());
            long endTime = DateUtils.currentTimeMillis();
            long totalTime = TaskCostCalculator.calculate(stepInstance.getStartTime(), endTime,
                stepInstance.getTotalTime());
            // 人工确认通过，该步骤状态标识为成功；终止成功的步骤保持状态不变
            taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.SUCCESS, null, endTime,
                totalTime);
            taskExecuteMQEventDispatcher.refreshJob(taskInstanceId);
        } else {
            log.warn("Unsupported step instance status for confirm-step-continue step action, stepInstanceId:{}, " +
                "status:{}", stepInstanceId, stepInstance.getStatus());
        }
    }

    private void ignoreError(StepInstanceBaseDTO stepInstance) {
        if (!stepInstance.getStatus().equals(RunStatusEnum.FAIL.getValue())) {
            log.warn("Current step status does not support ignore error operation! stepInstanceId:{}, status:{}",
                stepInstance.getId(), stepInstance.getStatus());
            return;
        }

        taskInstanceService.updateStepStatus(stepInstance.getId(), RunStatusEnum.IGNORE_ERROR.getValue());
        taskInstanceService.resetTaskExecuteInfoForResume(stepInstance.getTaskInstanceId());
        taskExecuteMQEventDispatcher.refreshJob(stepInstance.getTaskInstanceId());
    }

    private void startStep(StepInstanceBaseDTO stepInstance) {
        int stepStatus = stepInstance.getStatus();
        long stepInstanceId = stepInstance.getId();
        long taskInstanceId = stepInstance.getTaskInstanceId();

        // 只有当步骤状态为“等待用户”、“未执行”、“滚动等待”时可以启动步骤
        if (RunStatusEnum.BLANK.getValue() == stepStatus
            || RunStatusEnum.WAITING.getValue() == stepStatus
            || RunStatusEnum.ROLLING_WAITING.getValue() == stepStatus) {

            taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.RUNNING,
                DateUtils.currentTimeMillis(), null, null);

            // 如果是滚动步骤，需要更新滚动进度
            if (stepInstance.isRollingStep()) {
                int currentRollingBatch = stepInstance.getBatch() + 1;
                stepInstance.setBatch(currentRollingBatch);
                stepInstanceService.updateStepCurrentBatch(stepInstanceId, currentRollingBatch);
            }

            int stepType = stepInstance.getExecuteType();
            if (EXECUTE_SCRIPT.getValue() == stepType || StepExecuteTypeEnum.EXECUTE_SQL.getValue() == stepType) {
                taskExecuteMQEventDispatcher.startGseStep(stepInstanceId, stepInstance.isRollingStep() ?
                    stepInstance.getBatch() : null);
            } else if (TaskStepTypeEnum.FILE.getValue() == stepType) {
                // 如果不是滚动步骤或者是第一批次滚动执行，那么需要为后续的分发阶段准备本地/第三方源文件
                if (!stepInstance.isRollingStep() || stepInstance.isFirstRollingBatch()) {
                    filePrepareService.prepareFileForGseTask(stepInstanceId);
                }
            } else if (TaskStepTypeEnum.APPROVAL.getValue() == stepType) {
                executeConfirmStep(stepInstance);
            } else {
                log.warn("Unsupported step type, skip it! stepInstanceId={}, stepType={}", stepInstanceId, stepType);
                taskInstanceService.updateTaskStatus(taskInstanceId, RunStatusEnum.SKIPPED.getValue());
                taskExecuteMQEventDispatcher.refreshJob(taskInstanceId);
            }
        } else {
            log.warn("Unsupported step instance run status for starting step, stepInstanceId={}, status={}",
                stepInstanceId, stepStatus);
        }
    }

    private void skipStep(StepInstanceBaseDTO stepInstance) {
        int stepStatus = stepInstance.getStatus();
        long stepInstanceId = stepInstance.getId();
        long taskInstanceId = stepInstance.getTaskInstanceId();

        // 只有当步骤状态为'终止中'时可以跳过步骤
        if (RunStatusEnum.STOPPING.getValue() == stepStatus) {
            long now = DateUtils.currentTimeMillis();
            taskInstanceService.updateStepStartTimeIfNull(stepInstanceId, now);
            taskInstanceService.updateStepStatus(stepInstanceId, RunStatusEnum.SKIPPED.getValue());
            taskInstanceService.updateStepEndTime(stepInstanceId, now);

            taskInstanceService.updateTaskStatus(taskInstanceId, RunStatusEnum.RUNNING.getValue());
            taskExecuteMQEventDispatcher.refreshJob(taskInstanceId);
        } else {
            log.warn("Unsupported step instance run status for skipping step, stepInstanceId={}, status={}",
                stepInstanceId, stepStatus);
        }
    }

    private void stopStep(StepInstanceBaseDTO stepInstance) {
        long stepInstanceId = stepInstance.getId();
        long taskInstanceId = stepInstance.getTaskInstanceId();

        int executeType = stepInstance.getExecuteType();
        if (TaskStepTypeEnum.SCRIPT.getValue() == executeType || TaskStepTypeEnum.FILE.getValue() == executeType
            || EXECUTE_SQL.getValue().equals(executeType)) {
            taskInstanceService.updateTaskStatus(taskInstanceId, RunStatusEnum.STOPPING.getValue());
        } else {
            log.warn("Not gse step type, can not stop! stepInstanceId={}, stepType={}", stepInstanceId, executeType);
        }
    }

    /**
     * 第三方文件源文件拉取完成后继续GSE文件分发
     *
     * @param stepInstance 步骤实例
     */
    private void continueGseFileStep(StepInstanceBaseDTO stepInstance) {
        // 如果是滚动步骤，需要更新滚动进度
        if (stepInstance.isRollingStep()) {
            int currentRollingBatch = stepInstance.getBatch() + 1;
            stepInstance.setBatch(currentRollingBatch);
            stepInstanceService.updateStepCurrentBatch(stepInstance.getId(), currentRollingBatch);
        }
        taskExecuteMQEventDispatcher.startGseStep(stepInstance.getId(),
            stepInstance.isRollingStep() ? stepInstance.getBatch() : null);
    }

    /**
     * 重新执行步骤失败的任务
     */
    private void retryStepFail(StepInstanceBaseDTO stepInstance) {
        resetStatusForRetry(stepInstance);
        filePrepareService.retryPrepareFile(stepInstance.getId());
        taskExecuteMQEventDispatcher.retryGseStepFail(stepInstance.getId());
    }

    /**
     * 从头执行步骤
     */
    private void retryStepAll(StepInstanceBaseDTO stepInstance) {
        resetStatusForRetry(stepInstance);
        filePrepareService.retryPrepareFile(stepInstance.getId());
        taskExecuteMQEventDispatcher.retryGseStepAll(stepInstance.getId());
    }

    /**
     * 清理执行完的步骤
     */
    private void clearStep(StepInstanceBaseDTO stepInstance) {
        int executeType = stepInstance.getExecuteType();
        // 当前仅有文件分发类步骤需要清理中间文件
        if (TaskStepTypeEnum.FILE.getValue() == executeType) {
            filePrepareService.clearPreparedTmpFile(stepInstance.getId());
        }
    }

    private void resetStatusForRetry(StepInstanceBaseDTO stepInstance) {
        long stepInstanceId = stepInstance.getId();
        long taskInstanceId = stepInstance.getTaskInstanceId();

        taskInstanceService.resetStepExecuteInfoForRetry(stepInstanceId);
        taskInstanceService.resetTaskExecuteInfoForResume(taskInstanceId);
    }

    /**
     * 人工确认步骤
     */
    private void executeConfirmStep(StepInstanceBaseDTO stepInstance) {
        long stepInstanceId = stepInstance.getId();
        long taskInstanceId = stepInstance.getTaskInstanceId();

        // 只有“未执行”和“确认终止”状态的，才可以重新执行人工确认步骤
        if (RunStatusEnum.BLANK.getValue().equals(stepInstance.getStatus())
            || RunStatusEnum.CONFIRM_TERMINATED.getValue().equals(stepInstance.getStatus())) {
            // 发送页面确认信息
            TaskInstanceDTO taskInstance = taskInstanceService.getTaskInstance(taskInstanceId);
            String stepOperator = stepInstance.getOperator();

            if (StringUtils.isBlank(stepOperator)) {
                log.info("The operator is empty, continue run step! stepInstanceId={}", stepInstanceId);
                stepOperator = taskInstance.getOperator();
                stepInstance.setOperator(stepOperator);
            }
            taskInstanceService.updateStepStatus(stepInstanceId, RunStatusEnum.WAITING.getValue());
            taskInstanceService.updateTaskStatus(taskInstanceId, RunStatusEnum.WAITING.getValue());
            notifyService.asyncSendMQConfirmNotification(taskInstance, stepInstance);
        } else {
            log.warn("Unsupported step instance run status for executing confirm step, stepInstanceId={}, status={}",
                stepInstanceId, stepInstance.getStatus());
        }
    }


    private void refreshStep(StepInstanceBaseDTO stepInstance) {
        long stepInstanceId = stepInstance.getId();
        int stepStatus = stepInstance.getStatus();

        GseTaskDTO gseTask = gseTaskService.getGseTask(stepInstance.getId(), stepInstance.getExecuteCount(),
            stepInstance.getBatch());
        RunStatusEnum gseTaskStatus = RunStatusEnum.valueOf(gseTask.getStatus());
        if (gseTaskStatus == null) {
            log.error("Refresh step fail, invalid gse task status, stepInstanceId: {}, status: {}",
                stepInstance, stepStatus);
            return;
        }

        long endTime = System.currentTimeMillis();
        long startTime = stepInstance.getStartTime();
        long totalTime = endTime - startTime;

        switch (gseTaskStatus) {
            case SUCCESS:
                if (stepInstance.isRollingStep()) {
                    TaskInstanceRollingConfigDTO rollingConfig =
                        rollingConfigService.getRollingConfig(stepInstance.getRollingConfigId());
                    stepInstanceRollingTaskService.updateRollingTask(stepInstanceId, stepInstance.getExecuteCount(),
                        stepInstance.getBatch(), RunStatusEnum.SUCCESS, startTime, endTime, totalTime);
                    int totalBatch = rollingConfig.getConfig().getServerBatchList().size();
                    boolean isLastBatch = totalBatch == stepInstance.getBatch();
                    if (isLastBatch) {
                        taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.SUCCESS,
                            startTime, endTime, totalTime);
                        // 步骤执行成功后清理产生的临时文件
                        clearStep(stepInstance);
                    } else {
                        taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.ROLLING_WAITING,
                            startTime, endTime, totalTime);
                        return;
                    }
                } else {
                    taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.SUCCESS,
                        startTime, endTime, totalTime);
                    // 步骤执行成功后清理产生的临时文件
                    clearStep(stepInstance);
                }
                break;
            case FAIL:
                if (stepInstance.isIgnoreError()) {
                    taskInstanceService.updateStepStatus(stepInstanceId, RunStatusEnum.IGNORE_ERROR.getValue());
                }
                if (stepInstance.isRollingStep()) {
                    TaskInstanceRollingConfigDTO rollingConfig =
                        rollingConfigService.getRollingConfig(stepInstance.getRollingConfigId());
                    stepInstanceRollingTaskService.updateRollingTask(stepInstanceId, stepInstance.getExecuteCount(),
                        stepInstance.getBatch(), RunStatusEnum.FAIL, startTime, endTime, totalTime);
                }
                taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.FAIL,
                    startTime, endTime, totalTime);
                break;
            case STOP_SUCCESS:
                if (stepStatus == RunStatusEnum.STOPPING.getValue() || stepStatus == RunStatusEnum.RUNNING.getValue()) {
                    taskInstanceService.updateStepExecutionInfo(stepInstanceId, RunStatusEnum.STOP_SUCCESS,
                        startTime, endTime, totalTime);
                    if (stepInstance.isRollingStep()) {
                        stepInstanceRollingTaskService.updateRollingTask(stepInstanceId, stepInstance.getExecuteCount(),
                            stepInstance.getBatch(), RunStatusEnum.STOP_SUCCESS, startTime, endTime, totalTime);
                    }
                } else {
                    log.error("Refresh step fail, stepInstanceId: {}, stepStatus: {}, gseTaskStatus: {}",
                        stepInstanceId, stepStatus, RunStatusEnum.STOP_SUCCESS.getValue());
                    return;
                }
                break;
            case ABNORMAL_STATE:
                setAbnormalStatusForStep(stepInstance);
                if (stepInstance.isRollingStep()) {
                    stepInstanceRollingTaskService.updateRollingTask(stepInstanceId, stepInstance.getExecuteCount(),
                        stepInstance.getBatch(), RunStatusEnum.ABNORMAL_STATE, startTime, endTime, totalTime);
                }
                break;
            default:
                log.error("Refresh step fail, stepInstanceId: {}, stepStatus: {}, gseTaskStatus: {}", stepInstanceId,
                    stepStatus, gseTaskStatus.getValue());
                return;
        }
        taskExecuteMQEventDispatcher.refreshJob(stepInstance.getTaskInstanceId());
    }

    private void setAbnormalStatusForStep(StepInstanceBaseDTO stepInstance) {
        long endTime = System.currentTimeMillis();
        if (!RunStatusEnum.getFinishedStatusValueList().contains(stepInstance.getStatus())) {
            long totalTime = TaskCostCalculator.calculate(stepInstance.getStartTime(), endTime,
                stepInstance.getTotalTime());
            taskInstanceService.updateStepExecutionInfo(
                stepInstance.getId(),
                RunStatusEnum.ABNORMAL_STATE,
                null,
                endTime,
                totalTime
            );
        } else {
            log.info(
                "StepInstance {} already enter a final state:{}",
                stepInstance.getId(),
                stepInstance.getStatus()
            );
        }
    }
}
