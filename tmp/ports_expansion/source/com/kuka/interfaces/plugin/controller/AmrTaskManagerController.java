/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.kuka.interfaces.plugin.common.ErrorCode
 *  com.kuka.interfaces.plugin.common.MisUtils
 *  com.kuka.interfaces.plugin.common.ResponseResult
 *  com.kuka.interfaces.plugin.common.State
 *  com.kuka.interfaces.plugin.common.ValidatorUtil
 *  com.kuka.interfaces.plugin.entity.AgvTaskDto
 *  com.kuka.interfaces.plugin.entity.MissionCancelDto
 *  com.kuka.interfaces.plugin.entity.MoveTaskDO
 *  com.kuka.interfaces.plugin.entity.OperationFeedBackDto
 *  com.kuka.interfaces.plugin.entity.PauseResumeMissionRequest
 *  com.kuka.interfaces.plugin.entity.QueryJobDto
 *  com.kuka.interfaces.plugin.entity.RedirectMissionDto
 *  com.kuka.interfaces.plugin.entity.mission.MissionRetryRequest
 *  com.kuka.interfaces.plugin.services.ApiRequestCache
 *  com.kuka.interfaces.plugin.services.MoveTaskService
 *  com.kuka.interfaces.plugin.services.TaskManager
 *  com.kuka.interfaces.plugin.services.WcsGrpcAdapterService
 *  io.swagger.annotations.Api
 *  io.swagger.annotations.ApiOperation
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.util.StringUtils
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package com.kuka.interfaces.plugin.controller;

import com.kuka.interfaces.plugin.common.ErrorCode;
import com.kuka.interfaces.plugin.common.MisUtils;
import com.kuka.interfaces.plugin.common.ResponseResult;
import com.kuka.interfaces.plugin.common.State;
import com.kuka.interfaces.plugin.common.ValidatorUtil;
import com.kuka.interfaces.plugin.entity.AgvTaskDto;
import com.kuka.interfaces.plugin.entity.MissionCancelDto;
import com.kuka.interfaces.plugin.entity.MoveTaskDO;
import com.kuka.interfaces.plugin.entity.OperationFeedBackDto;
import com.kuka.interfaces.plugin.entity.PauseResumeMissionRequest;
import com.kuka.interfaces.plugin.entity.QueryJobDto;
import com.kuka.interfaces.plugin.entity.RedirectMissionDto;
import com.kuka.interfaces.plugin.entity.mission.MissionRetryRequest;
import com.kuka.interfaces.plugin.services.ApiRequestCache;
import com.kuka.interfaces.plugin.services.MoveTaskService;
import com.kuka.interfaces.plugin.services.TaskManager;
import com.kuka.interfaces.plugin.services.WcsGrpcAdapterService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags={"AMR\u4efb\u52a1\u6267\u884c\u76f8\u5173\u63a5\u53e3 Mission Related APIs"})
@RequestMapping(value={"/api/amr"})
@RestController
public class AmrTaskManagerController {
    private static final Logger log = LoggerFactory.getLogger(AmrTaskManagerController.class);
    @Autowired
    private WcsGrpcAdapterService wcsGrpcAdapterService;
    @Autowired
    private MoveTaskService moveTaskService;
    @Autowired
    private TaskManager taskManager;
    @Autowired
    private ApiRequestCache apiRequestCache;

    @ApiOperation(value="AMR\u4efb\u52a1\u4e0b\u53d1\u63a5\u53e3 Submit Mission")
    @PostMapping(value={"/submitMission"})
    public ResponseResult submitMission(@RequestBody AgvTaskDto<?> agvTask) {
        log.info("amrTaskSubmit:[{}]", agvTask);
        ResponseResult responseResult = this.validateTask(agvTask);
        if (Objects.nonNull(responseResult)) {
            return responseResult;
        }
        responseResult = this.taskManager.submit(agvTask);
        return responseResult;
    }

    @ApiOperation(value="AMR\u4efb\u52a1\u53d6\u6d88 Cancel mission")
    @PostMapping(value={"/missionCancel"})
    public ResponseResult missionCancel(@RequestBody MissionCancelDto missionCancelDto) {
        String validate = ValidatorUtil.validate((Object)missionCancelDto);
        if (StringUtils.hasText((String)validate)) {
            return ResponseResult.error((String)ErrorCode.INVALID_PARAMETER, (String)validate);
        }
        if (MisUtils.isBlank((String)missionCancelDto.getMissionCode()) && MisUtils.isBlank((String)missionCancelDto.getPosition()) && MisUtils.isBlank((String)missionCancelDto.getContainerCode())) {
            return ResponseResult.error((String)ErrorCode.MISS_PARAM, (String)"missionCode,position,containerCode can not be blank at same");
        }
        if (StringUtils.hasText((String)missionCancelDto.getMissionCode())) {
            MoveTaskDO moveTaskDO = this.moveTaskService.selectTaskByMission(missionCancelDto.getMissionCode());
            if (null == moveTaskDO) {
                return ResponseResult.error((String)ErrorCode.INVALID_PARAMETER, (String)("no task for missionCode[" + missionCancelDto.getMissionCode() + "]"));
            }
            if (State.DONE.name().equals(moveTaskDO.getState())) {
                return ResponseResult.error((String)ErrorCode.INVALID_STATE, (String)("missionCode[" + missionCancelDto.getMissionCode() + "] already completed"));
            }
            if (State.CANCELED.name().equals(moveTaskDO.getState()) || State.FAIL.name().equals(moveTaskDO.getState())) {
                return ResponseResult.ok();
            }
        }
        return this.wcsGrpcAdapterService.cancel(missionCancelDto);
    }

    @ApiOperation(value="AMR\u4efb\u52a1\u8282\u70b9\u64cd\u4f5c\u5b8c\u6210\u53cd\u9988 Release Mission To Continue")
    @PostMapping(value={"/operationFeedback"})
    public ResponseResult operationFeedback(@RequestBody OperationFeedBackDto OperationFeedBackDto2) {
        if (MisUtils.isBlank((String)OperationFeedBackDto2.getRequestId())) {
            return ResponseResult.error((String)ErrorCode.INVALID_PARAMETER, (String)"RequestId can not be blank");
        }
        if (MisUtils.isBlank((String)OperationFeedBackDto2.getMissionCode()) && MisUtils.isBlank((String)OperationFeedBackDto2.getContainerCode()) && MisUtils.isBlank((String)OperationFeedBackDto2.getPosition())) {
            return ResponseResult.error((String)ErrorCode.INVALID_PARAMETER, (String)"missionCode,nodeCode,containerCode can not be blank at same time");
        }
        return this.wcsGrpcAdapterService.missionPass(OperationFeedBackDto2);
    }

    @ApiOperation(value="\u67e5\u8be2\u4f5c\u4e1a Query jobs", notes="\u67e5\u8be2\u5185\u5bb9\u540c\u4f5c\u4e1a\u770b\u677f\uff0c\u65e0\u6cd5\u67e5\u8be2\u4f5c\u4e1a\u5386\u53f2\u3002\nThe response is the same as the Job Board. Job History won't be included in the response.")
    @PostMapping(value={"/jobQuery"})
    public ResponseResult jobQuery(@RequestBody QueryJobDto queryJobDto) {
        return this.wcsGrpcAdapterService.queryJobs(queryJobDto);
    }

    @ApiOperation(value="AMR\u4efb\u52a1\u91cd\u65b0\u9009\u62e9\u76ee\u6807\u70b9 Redirect Mission To New Target Node")
    @PostMapping(value={"/redirectMission"})
    public ResponseResult redirectMission(@RequestBody RedirectMissionDto redirectMissionDto) {
        return this.taskManager.changeTarget(redirectMissionDto);
    }

    @ApiOperation(value="\u6682\u505c\u4efb\u52a1 Pause Mission")
    @PostMapping(value={"/pauseMission"})
    public ResponseResult pauseMission(@RequestBody PauseResumeMissionRequest request) {
        return this.wcsGrpcAdapterService.pauseMission(request);
    }

    @ApiOperation(value="\u6062\u590d\u4efb\u52a1 Recover Mission")
    @PostMapping(value={"/recoverMission"})
    public ResponseResult resumeMission(@RequestBody PauseResumeMissionRequest request) {
        return this.wcsGrpcAdapterService.recoverMission(request);
    }

    @ApiOperation(value="\u91cd\u8bd5\u4efb\u52a1 Retry Mission")
    @PostMapping(value={"/retryMission"})
    public ResponseResult retryMission(@RequestBody MissionRetryRequest request) {
        return this.wcsGrpcAdapterService.retryMission(request);
    }

    private ResponseResult validateTask(AgvTaskDto agvTaskDto) {
        String validate = ValidatorUtil.validate((Object)agvTaskDto);
        if (StringUtils.hasText((String)validate)) {
            return ResponseResult.error((String)ErrorCode.INVALID_PARAMETER, (String)validate);
        }
        AgvTaskDto requestHis = this.apiRequestCache.getRequest(agvTaskDto.getRequestId());
        this.apiRequestCache.putRequest(agvTaskDto);
        if (Objects.nonNull(requestHis)) {
            return ResponseResult.error((String)ErrorCode.API_REQUEST_ID_IS_DUPLICATE, (String)("requestId:[" + agvTaskDto.getRequestId() + "] is already used, should be unique"));
        }
        MoveTaskDO moveTaskDO = this.moveTaskService.selectTaskByMission(agvTaskDto.getMissionCode());
        if (Objects.nonNull(moveTaskDO)) {
            if (State.doneStates().contains(moveTaskDO.getState())) {
                return ResponseResult.error((String)ErrorCode.API_MISSION_CODE_IS_DUPLICATE, (String)("missionCode [" + agvTaskDto.getMissionCode() + "] is already " + moveTaskDO.getState()));
            }
            return ResponseResult.ok();
        }
        return null;
    }
}
