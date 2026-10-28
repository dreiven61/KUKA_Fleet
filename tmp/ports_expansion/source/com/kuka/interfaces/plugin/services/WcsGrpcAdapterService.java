/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Lists
 *  com.google.protobuf.BoolValue
 *  com.google.protobuf.DoubleValue
 *  com.google.protobuf.Int32Value
 *  com.google.protobuf.Int64Value
 *  com.google.protobuf.StringValue
 *  com.kuka.interfaces.plugin.common.ConfigConstants
 *  com.kuka.interfaces.plugin.common.InterfaceAPIException
 *  com.kuka.interfaces.plugin.common.MisUtils
 *  com.kuka.interfaces.plugin.common.ResponseResult
 *  com.kuka.interfaces.plugin.entity.AreaDto
 *  com.kuka.interfaces.plugin.entity.AreaForNodeDto
 *  com.kuka.interfaces.plugin.entity.AreaNodeDto
 *  com.kuka.interfaces.plugin.entity.ContainerDto
 *  com.kuka.interfaces.plugin.entity.ContainerMoveInDto
 *  com.kuka.interfaces.plugin.entity.ContainerMoveOutDto
 *  com.kuka.interfaces.plugin.entity.ContainerQueryAllDto
 *  com.kuka.interfaces.plugin.entity.ContainerQueryDto
 *  com.kuka.interfaces.plugin.entity.ContainerUpdateDto
 *  com.kuka.interfaces.plugin.entity.JobRecordDto
 *  com.kuka.interfaces.plugin.entity.MissionCancelDto
 *  com.kuka.interfaces.plugin.entity.OperationFeedBackDto
 *  com.kuka.interfaces.plugin.entity.OppChargingRequest
 *  com.kuka.interfaces.plugin.entity.PauseResumeMissionRequest
 *  com.kuka.interfaces.plugin.entity.QueryJobDto
 *  com.kuka.interfaces.plugin.entity.RobotDto
 *  com.kuka.interfaces.plugin.entity.RobotDto$RobotDtoBuilder
 *  com.kuka.interfaces.plugin.entity.UpdateForbiddenAreaDto
 *  com.kuka.interfaces.plugin.entity.data.QueryFunctionNodeReq
 *  com.kuka.interfaces.plugin.entity.data.QueryFunctionNodeRes
 *  com.kuka.interfaces.plugin.entity.mission.MissionRetryRequest
 *  com.kuka.interfaces.plugin.entity.robot.ChargeRobotRequest
 *  com.kuka.interfaces.plugin.entity.robot.InsertRobotRequest
 *  com.kuka.interfaces.plugin.entity.robot.RemoveRobotRequest
 *  com.kuka.interfaces.plugin.entity.robot.RobotDropReq
 *  com.kuka.interfaces.plugin.entity.robot.RobotLiftReq
 *  com.kuka.interfaces.plugin.entity.robot.RobotMoveCarryReq
 *  com.kuka.interfaces.plugin.entity.robot.RobotMoveReq
 *  com.kuka.interfaces.plugin.entity.robot.UnlockRobotRequest
 *  com.kuka.interfaces.plugin.function.PresentFunctionHandler
 *  com.kuka.rcs.bd.grpc.robot.MobileRobotRealtimeGto
 *  com.kuka.rcs.bd.grpc.robot.RealtimeDataQueryResponse
 *  com.kuka.rcs.bd.grpc.robot.RobotIdRequest
 *  com.kuka.rcs.bd.grpc.robot.RobotServiceGrpc$RobotServiceBlockingStub
 *  com.kuka.wcs.grpc.mission.CancelModeGto
 *  com.kuka.wcs.grpc.mission.EmptyParameterRequest
 *  com.kuka.wcs.grpc.mission.JobRecordGto
 *  com.kuka.wcs.grpc.mission.MoveContainerTypeGto
 *  com.kuka.wcs.grpc.mission.NodeGto
 *  com.kuka.wcs.grpc.mission.PauseResumeTypeGto
 *  com.kuka.wcs.grpc.mission.QueryJobRequest
 *  com.kuka.wcs.grpc.mission.QueryJobRequest$Builder
 *  com.kuka.wcs.grpc.mission.QueryJobResponse
 *  com.kuka.wcs.grpc.mission.WcsAllAreaResponse
 *  com.kuka.wcs.grpc.mission.WcsAreaNodeRequest
 *  com.kuka.wcs.grpc.mission.WcsAreaNodeResponse
 *  com.kuka.wcs.grpc.mission.WcsAreaOfPointRequest
 *  com.kuka.wcs.grpc.mission.WcsAreaRequest
 *  com.kuka.wcs.grpc.mission.WcsAreaResponse
 *  com.kuka.wcs.grpc.mission.WcsCancelMissionRequest
 *  com.kuka.wcs.grpc.mission.WcsCancelMissionRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsContainerAddAndEnterRequest
 *  com.kuka.wcs.grpc.mission.WcsContainerAddAndEnterRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsContainerAddAndEnterResponse
 *  com.kuka.wcs.grpc.mission.WcsContainerAllRequest
 *  com.kuka.wcs.grpc.mission.WcsContainerAllRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsContainerDepartAndRemoveRequest
 *  com.kuka.wcs.grpc.mission.WcsContainerDepartAndRemoveRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsContainerGto
 *  com.kuka.wcs.grpc.mission.WcsContainerModelListResponse
 *  com.kuka.wcs.grpc.mission.WcsContainerOfPointRequest
 *  com.kuka.wcs.grpc.mission.WcsContainerRequest
 *  com.kuka.wcs.grpc.mission.WcsContainerResponse
 *  com.kuka.wcs.grpc.mission.WcsContainerWithEmptyFullStatusRequest
 *  com.kuka.wcs.grpc.mission.WcsGenerateMissionRequest
 *  com.kuka.wcs.grpc.mission.WcsMapCellCodeResponse
 *  com.kuka.wcs.grpc.mission.WcsMapNodeGto
 *  com.kuka.wcs.grpc.mission.WcsMissionGrpcResponse
 *  com.kuka.wcs.grpc.mission.WcsMissionServiceGrpc$WcsMissionServiceBlockingStub
 *  com.kuka.wcs.grpc.mission.WcsMoveContainerRequest
 *  com.kuka.wcs.grpc.mission.WcsMoveContainerRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsPassMissionRequest
 *  com.kuka.wcs.grpc.mission.WcsPassMissionRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsPauseResumeMissionRequest
 *  com.kuka.wcs.grpc.mission.WcsPauseResumeMissionRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsPauseRobotRequest
 *  com.kuka.wcs.grpc.mission.WcsPipelineDeviceRelatedNodeGto
 *  com.kuka.wcs.grpc.mission.WcsPipelineDeviceRelatedNodeResponse
 *  com.kuka.wcs.grpc.mission.WcsQueryContainerCustomStatusRequest
 *  com.kuka.wcs.grpc.mission.WcsRelatedMapCellOfPipelineRequest
 *  com.kuka.wcs.grpc.mission.WcsRelatedMapCellOfSlotRequest
 *  com.kuka.wcs.grpc.mission.WcsResumeRobotRequest
 *  com.kuka.wcs.grpc.mission.WcsRetryMissionRequest
 *  com.kuka.wcs.grpc.mission.WcsRetryMissionRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsUnlockRobotRequest
 *  com.kuka.wcs.grpc.mission.WcsUpdateContainerCustomStatusRequest
 *  com.kuka.wcs.grpc.mission.WcsUpdateContainerEmptyFullStatusRequest
 *  com.kuka.wcs.grpc.mission.WcsUpdateContainerEmptyFullStatusRequest$Builder
 *  com.kuka.wcs.grpc.mission.WcsUpdateContainerPositionRequest
 *  com.kuka.wcs.grpc.mission.WcsUpdateContainerPositionRequest$Builder
 *  com.kuka.wcs.grpc.platform.bandArea.WcsBandAreaResponse
 *  com.kuka.wcs.grpc.platform.bandArea.WcsBandAreaServiceGrpc$WcsBandAreaServiceBlockingStub
 *  com.kuka.wcs.grpc.platform.bandArea.WcsBandAreaStatus
 *  com.kuka.wcs.grpc.platform.bandArea.WcsQueryOneBandAreaRequest
 *  com.kuka.wcs.grpc.platform.bandArea.WcsUpdateBandAreaStatusRequest
 *  com.kuka.wcs.grpc.platform.bandArea.WcsUpdateBandAreaStatusRequest$Builder
 *  com.kuka.wcs.grpc.platform.common.NoParameterRequest
 *  com.kuka.wcs.grpc.platform.common.WcsCommonResponse
 *  com.kuka.wcs.grpc.platform.data.WcsDataServiceGrpc$WcsDataServiceBlockingStub
 *  com.kuka.wcs.grpc.platform.data.WcsQueryCommandRequest
 *  com.kuka.wcs.grpc.platform.data.WcsQueryCommandResponse
 *  com.kuka.wcs.grpc.platform.data.WcsQueryFunctionNodeRequest
 *  com.kuka.wcs.grpc.platform.data.WcsQueryFunctionNodeRequest$Builder
 *  com.kuka.wcs.grpc.platform.data.WcsQueryFunctionNodeResponse
 *  com.kuka.wcs.grpc.platform.data.WcsQueryRobByNodeUuidOrForeignCodeRequest
 *  com.kuka.wcs.grpc.platform.data.WcsQueryRobotOfNodeResponse
 *  com.kuka.wcs.grpc.platform.data.WcsRobotGto
 *  com.kuka.wcs.grpc.platform.robot.ChargeRobRequest
 *  com.kuka.wcs.grpc.platform.robot.ChargeRobRequest$Builder
 *  com.kuka.wcs.grpc.platform.robot.ChargeRobResponse
 *  com.kuka.wcs.grpc.platform.robot.InsertRobRequest
 *  com.kuka.wcs.grpc.platform.robot.InsertRobResponse
 *  com.kuka.wcs.grpc.platform.robot.OpportunityChargingRequest
 *  com.kuka.wcs.grpc.platform.robot.OpportunityChargingResponse
 *  com.kuka.wcs.grpc.platform.robot.RemoveRobRequest
 *  com.kuka.wcs.grpc.platform.robot.RemoveRobRequest$Builder
 *  com.kuka.wcs.grpc.platform.robot.RemoveRobResponse
 *  com.kuka.wcs.grpc.platform.robot.RobCarryRequest
 *  com.kuka.wcs.grpc.platform.robot.RobCarryRequest$Builder
 *  com.kuka.wcs.grpc.platform.robot.RobCarryResponse
 *  com.kuka.wcs.grpc.platform.robot.RobDropRequest
 *  com.kuka.wcs.grpc.platform.robot.RobDropRequest$Builder
 *  com.kuka.wcs.grpc.platform.robot.RobLiftRequest
 *  com.kuka.wcs.grpc.platform.robot.RobLiftRequest$Builder
 *  com.kuka.wcs.grpc.platform.robot.RobMoveRequest
 *  com.kuka.wcs.grpc.platform.robot.RobMoveRequest$Builder
 *  com.kuka.wcs.grpc.platform.robot.RobPayloadPoseRequest
 *  com.kuka.wcs.grpc.platform.robot.RobPayloadPoseResponse
 *  com.kuka.wcs.grpc.platform.robot.WcsRobotServiceGrpc$WcsRobotServiceBlockingStub
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Autowired
 *  org.springframework.context.event.EventListener
 *  org.springframework.stereotype.Service
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package com.kuka.interfaces.plugin.services;

import com.google.common.collect.Lists;
import com.google.protobuf.BoolValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.kuka.interfaces.plugin.common.ConfigConstants;
import com.kuka.interfaces.plugin.common.InterfaceAPIException;
import com.kuka.interfaces.plugin.common.MisUtils;
import com.kuka.interfaces.plugin.common.ResponseResult;
import com.kuka.interfaces.plugin.entity.AreaDto;
import com.kuka.interfaces.plugin.entity.AreaForNodeDto;
import com.kuka.interfaces.plugin.entity.AreaNodeDto;
import com.kuka.interfaces.plugin.entity.ContainerDto;
import com.kuka.interfaces.plugin.entity.ContainerMoveInDto;
import com.kuka.interfaces.plugin.entity.ContainerMoveOutDto;
import com.kuka.interfaces.plugin.entity.ContainerQueryAllDto;
import com.kuka.interfaces.plugin.entity.ContainerQueryDto;
import com.kuka.interfaces.plugin.entity.ContainerUpdateDto;
import com.kuka.interfaces.plugin.entity.JobRecordDto;
import com.kuka.interfaces.plugin.entity.MissionCancelDto;
import com.kuka.interfaces.plugin.entity.OperationFeedBackDto;
import com.kuka.interfaces.plugin.entity.OppChargingRequest;
import com.kuka.interfaces.plugin.entity.PauseResumeMissionRequest;
import com.kuka.interfaces.plugin.entity.QueryJobDto;
import com.kuka.interfaces.plugin.entity.RobotDto;
import com.kuka.interfaces.plugin.entity.UpdateForbiddenAreaDto;
import com.kuka.interfaces.plugin.entity.data.QueryFunctionNodeReq;
import com.kuka.interfaces.plugin.entity.data.QueryFunctionNodeRes;
import com.kuka.interfaces.plugin.entity.mission.MissionRetryRequest;
import com.kuka.interfaces.plugin.entity.robot.ChargeRobotRequest;
import com.kuka.interfaces.plugin.entity.robot.InsertRobotRequest;
import com.kuka.interfaces.plugin.entity.robot.RemoveRobotRequest;
import com.kuka.interfaces.plugin.entity.robot.RobotDropReq;
import com.kuka.interfaces.plugin.entity.robot.RobotLiftReq;
import com.kuka.interfaces.plugin.entity.robot.RobotMoveCarryReq;
import com.kuka.interfaces.plugin.entity.robot.RobotMoveReq;
import com.kuka.interfaces.plugin.entity.robot.UnlockRobotRequest;
import com.kuka.interfaces.plugin.function.PresentFunctionHandler;
import com.kuka.rcs.bd.grpc.robot.MobileRobotRealtimeGto;
import com.kuka.rcs.bd.grpc.robot.RealtimeDataQueryResponse;
import com.kuka.rcs.bd.grpc.robot.RobotIdRequest;
import com.kuka.rcs.bd.grpc.robot.RobotServiceGrpc;
import com.kuka.wcs.grpc.mission.CancelModeGto;
import com.kuka.wcs.grpc.mission.EmptyParameterRequest;
import com.kuka.wcs.grpc.mission.JobRecordGto;
import com.kuka.wcs.grpc.mission.MoveContainerTypeGto;
import com.kuka.wcs.grpc.mission.NodeGto;
import com.kuka.wcs.grpc.mission.PauseResumeTypeGto;
import com.kuka.wcs.grpc.mission.QueryJobRequest;
import com.kuka.wcs.grpc.mission.QueryJobResponse;
import com.kuka.wcs.grpc.mission.WcsAllAreaResponse;
import com.kuka.wcs.grpc.mission.WcsAreaNodeRequest;
import com.kuka.wcs.grpc.mission.WcsAreaNodeResponse;
import com.kuka.wcs.grpc.mission.WcsAreaOfPointRequest;
import com.kuka.wcs.grpc.mission.WcsAreaRequest;
import com.kuka.wcs.grpc.mission.WcsAreaResponse;
import com.kuka.wcs.grpc.mission.WcsCancelMissionRequest;
import com.kuka.wcs.grpc.mission.WcsContainerAddAndEnterRequest;
import com.kuka.wcs.grpc.mission.WcsContainerAddAndEnterResponse;
import com.kuka.wcs.grpc.mission.WcsContainerAllRequest;
import com.kuka.wcs.grpc.mission.WcsContainerDepartAndRemoveRequest;
import com.kuka.wcs.grpc.mission.WcsContainerGto;
import com.kuka.wcs.grpc.mission.WcsContainerModelListResponse;
import com.kuka.wcs.grpc.mission.WcsContainerOfPointRequest;
import com.kuka.wcs.grpc.mission.WcsContainerRequest;
import com.kuka.wcs.grpc.mission.WcsContainerResponse;
import com.kuka.wcs.grpc.mission.WcsContainerWithEmptyFullStatusRequest;
import com.kuka.wcs.grpc.mission.WcsGenerateMissionRequest;
import com.kuka.wcs.grpc.mission.WcsMapCellCodeResponse;
import com.kuka.wcs.grpc.mission.WcsMapNodeGto;
import com.kuka.wcs.grpc.mission.WcsMissionGrpcResponse;
import com.kuka.wcs.grpc.mission.WcsMissionServiceGrpc;
import com.kuka.wcs.grpc.mission.WcsMoveContainerRequest;
import com.kuka.wcs.grpc.mission.WcsPassMissionRequest;
import com.kuka.wcs.grpc.mission.WcsPauseResumeMissionRequest;
import com.kuka.wcs.grpc.mission.WcsPauseRobotRequest;
import com.kuka.wcs.grpc.mission.WcsPipelineDeviceRelatedNodeGto;
import com.kuka.wcs.grpc.mission.WcsPipelineDeviceRelatedNodeResponse;
import com.kuka.wcs.grpc.mission.WcsQueryContainerCustomStatusRequest;
import com.kuka.wcs.grpc.mission.WcsRelatedMapCellOfPipelineRequest;
import com.kuka.wcs.grpc.mission.WcsRelatedMapCellOfSlotRequest;
import com.kuka.wcs.grpc.mission.WcsResumeRobotRequest;
import com.kuka.wcs.grpc.mission.WcsRetryMissionRequest;
import com.kuka.wcs.grpc.mission.WcsUnlockRobotRequest;
import com.kuka.wcs.grpc.mission.WcsUpdateContainerCustomStatusRequest;
import com.kuka.wcs.grpc.mission.WcsUpdateContainerEmptyFullStatusRequest;
import com.kuka.wcs.grpc.mission.WcsUpdateContainerPositionRequest;
import com.kuka.wcs.grpc.platform.bandArea.WcsBandAreaResponse;
import com.kuka.wcs.grpc.platform.bandArea.WcsBandAreaServiceGrpc;
import com.kuka.wcs.grpc.platform.bandArea.WcsBandAreaStatus;
import com.kuka.wcs.grpc.platform.bandArea.WcsQueryOneBandAreaRequest;
import com.kuka.wcs.grpc.platform.bandArea.WcsUpdateBandAreaStatusRequest;
import com.kuka.wcs.grpc.platform.common.NoParameterRequest;
import com.kuka.wcs.grpc.platform.common.WcsCommonResponse;
import com.kuka.wcs.grpc.platform.data.WcsDataServiceGrpc;
import com.kuka.wcs.grpc.platform.data.WcsQueryCommandRequest;
import com.kuka.wcs.grpc.platform.data.WcsQueryCommandResponse;
import com.kuka.wcs.grpc.platform.data.WcsQueryFunctionNodeRequest;
import com.kuka.wcs.grpc.platform.data.WcsQueryFunctionNodeResponse;
import com.kuka.wcs.grpc.platform.data.WcsQueryRobByNodeUuidOrForeignCodeRequest;
import com.kuka.wcs.grpc.platform.data.WcsQueryRobotOfNodeResponse;
import com.kuka.wcs.grpc.platform.data.WcsRobotGto;
import com.kuka.wcs.grpc.platform.robot.ChargeRobRequest;
import com.kuka.wcs.grpc.platform.robot.ChargeRobResponse;
import com.kuka.wcs.grpc.platform.robot.InsertRobRequest;
import com.kuka.wcs.grpc.platform.robot.InsertRobResponse;
import com.kuka.wcs.grpc.platform.robot.OpportunityChargingRequest;
import com.kuka.wcs.grpc.platform.robot.OpportunityChargingResponse;
import com.kuka.wcs.grpc.platform.robot.RemoveRobRequest;
import com.kuka.wcs.grpc.platform.robot.RemoveRobResponse;
import com.kuka.wcs.grpc.platform.robot.RobCarryRequest;
import com.kuka.wcs.grpc.platform.robot.RobCarryResponse;
import com.kuka.wcs.grpc.platform.robot.RobDropRequest;
import com.kuka.wcs.grpc.platform.robot.RobLiftRequest;
import com.kuka.wcs.grpc.platform.robot.RobMoveRequest;
import com.kuka.wcs.grpc.platform.robot.RobPayloadPoseRequest;
import com.kuka.wcs.grpc.platform.robot.RobPayloadPoseResponse;
import com.kuka.wcs.grpc.platform.robot.WcsRobotServiceGrpc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class WcsGrpcAdapterService {
    private static final Logger log = LoggerFactory.getLogger(WcsGrpcAdapterService.class);
    @Autowired(required=false)
    private WcsMissionServiceGrpc.WcsMissionServiceBlockingStub wcsMissionServiceBlockingStub;
    @Autowired(required=false)
    private WcsBandAreaServiceGrpc.WcsBandAreaServiceBlockingStub wcsBandAreaServiceBlockingStub;
    @Autowired(required=false)
    private WcsDataServiceGrpc.WcsDataServiceBlockingStub wcsDataServiceBlockingStub;
    @Autowired(required=false)
    private WcsRobotServiceGrpc.WcsRobotServiceBlockingStub wcsRobotServiceBlockingStub;
    @Autowired(required=false)
    private RobotServiceGrpc.RobotServiceBlockingStub robotServiceBlockingStub;
    private Map<String, WcsBandAreaStatus> bandAreaMap = null;

    public ResponseResult submit(WcsGenerateMissionRequest request) {
        WcsMissionGrpcResponse generate = this.wcsMissionServiceBlockingStub.generate(request);
        return this.transferWCSGrpc(generate);
    }

    public ResponseResult<List<ContainerDto>> queryContainer(ContainerQueryDto containerQueryDto) {
        WcsContainerResponse wcsContainerResponse = (WcsContainerResponse)this.notBlankApplyContainer(containerQueryDto.getContainerCode()).functionHandle(containerCode -> this.getContainerByCode((String)containerCode));
        if (Objects.nonNull(wcsContainerResponse)) {
            return this.transferContainerResponse(wcsContainerResponse, containerQueryDto);
        }
        wcsContainerResponse = (WcsContainerResponse)this.notBlankApplyContainer(containerQueryDto.getNodeCode()).functionHandle(nodeCode -> this.getContainerByNode((String)nodeCode));
        if (Objects.nonNull(wcsContainerResponse)) {
            return this.transferContainerResponse(wcsContainerResponse, containerQueryDto);
        }
        wcsContainerResponse = (WcsContainerResponse)this.notBlankApplyContainer(containerQueryDto.getAreaCode()).functionHandle(areaCode -> this.getContainerByArea((String)areaCode));
        if (Objects.nonNull(wcsContainerResponse)) {
            return this.transferContainerResponse(wcsContainerResponse, containerQueryDto);
        }
        wcsContainerResponse = (WcsContainerResponse)this.notBlankApplyContainer(containerQueryDto.getCustomStatus()).functionHandle(customStatus -> this.getContainerByCustomStatus((String)customStatus));
        if (Objects.nonNull(wcsContainerResponse)) {
            return this.transferContainerResponse(wcsContainerResponse, containerQueryDto);
        }
        wcsContainerResponse = this.getAllContainer();
        return this.transferContainerResponse(wcsContainerResponse, containerQueryDto);
    }

    public ResponseResult<List<ContainerDto>> queryContainerAll(ContainerQueryAllDto containerQueryDto) {
        WcsContainerAllRequest.Builder builder = WcsContainerAllRequest.newBuilder();
        if (StringUtils.hasText((String)containerQueryDto.getContainerCode())) {
            builder.setContainerCode(containerQueryDto.getContainerCode());
        }
        if (StringUtils.hasText((String)containerQueryDto.getNodeCode())) {
            builder.setNodeCode(containerQueryDto.getNodeCode());
        }
        if (StringUtils.hasText((String)containerQueryDto.getAreaCode())) {
            builder.setAreaCode(containerQueryDto.getAreaCode());
        }
        if (StringUtils.hasText((String)containerQueryDto.getMapCode())) {
            builder.setMapCode(containerQueryDto.getMapCode());
        }
        if (StringUtils.hasText((String)containerQueryDto.getDistrictCode())) {
            builder.setFloorNumber(containerQueryDto.getDistrictCode());
        }
        if (StringUtils.hasText((String)containerQueryDto.getContainerModelCode())) {
            builder.setContainerModelCode(containerQueryDto.getContainerModelCode());
        }
        if (Objects.nonNull(containerQueryDto.getEmptyFullStatus())) {
            builder.setEmptyFullStatus(Int32Value.newBuilder().setValue(containerQueryDto.getEmptyFullStatus().intValue()).build());
        }
        if (Objects.nonNull(containerQueryDto.getInMapStatus())) {
            builder.setInMapStatus(Int32Value.newBuilder().setValue(containerQueryDto.getInMapStatus().intValue()).build());
        }
        WcsContainerResponse wcsContainerResponse = this.wcsMissionServiceBlockingStub.queryContainerAll(builder.build());
        return this.transferContainerResponse(wcsContainerResponse);
    }

    private ResponseResult<List<ContainerDto>> transferContainerResponse(WcsContainerResponse wcsContainerResponse, ContainerQueryDto containerQueryDto) {
        List containerList;
        if (wcsContainerResponse.getSuccess() && !CollectionUtils.isEmpty((Collection)(containerList = wcsContainerResponse.getContainerList()))) {
            List containerDtoList = containerList.stream().filter(containerGto -> {
                if (StringUtils.hasText((String)containerQueryDto.getContainerModelCode()) && !Objects.equals(containerGto.getContainerModelCode(), containerQueryDto.getContainerModelCode())) {
                    return false;
                }
                if (containerQueryDto.getEmptyFullStatus() < 2 && containerGto.getEmptyFullStatus() != containerQueryDto.getEmptyFullStatus().intValue()) {
                    return false;
                }
                if (StringUtils.hasText((String)containerQueryDto.getCustomStatus()) && !Objects.equals(containerGto.getCustomStatus(), containerQueryDto.getCustomStatus())) {
                    return false;
                }
                return containerGto.getInMapStatus() == 1;
            }).map(containerGto -> ContainerDto.builder().containerCode(containerGto.getContainerCode()).containerModelCode(containerGto.getContainerModelCode()).nodeCode(containerGto.getNodeCode()).orientation(String.valueOf(containerGto.getOrientation())).isCarry(Integer.valueOf(containerGto.getIsCarry())).emptyFullStatus(Integer.valueOf(containerGto.getEmptyFullStatus())).inMapStatus(Integer.valueOf(containerGto.getInMapStatus())).containerCheckCode(containerGto.getContainerCheckCode()).mapCode(containerGto.getMapCode()).districtCode(containerGto.getFloorNumber()).customStatus(containerGto.getCustomStatus()).build()).collect(Collectors.toList());
            log.info("getAllContainer,has result:[{}]", containerDtoList);
            return ResponseResult.ok().put(containerDtoList);
        }
        return ResponseResult.error((String)wcsContainerResponse.getMessage());
    }

    private ResponseResult<List<ContainerDto>> transferContainerResponse(WcsContainerResponse wcsContainerResponse) {
        if (wcsContainerResponse.getSuccess()) {
            List containerList = wcsContainerResponse.getContainerList();
            if (!CollectionUtils.isEmpty((Collection)containerList)) {
                ArrayList<ContainerDto> containerDtoList = new ArrayList<ContainerDto>();
                for (WcsContainerGto containerGto : containerList) {
                    containerDtoList.add(ContainerDto.builder().containerCode(containerGto.getContainerCode()).containerModelCode(containerGto.getContainerModelCode()).nodeCode(containerGto.getNodeCode()).orientation(String.valueOf(containerGto.getOrientation())).isCarry(Integer.valueOf(containerGto.getIsCarry())).emptyFullStatus(Integer.valueOf(containerGto.getEmptyFullStatus())).inMapStatus(Integer.valueOf(containerGto.getInMapStatus())).containerCheckCode(containerGto.getContainerCheckCode()).mapCode(containerGto.getMapCode()).districtCode(containerGto.getFloorNumber()).customStatus(containerGto.getCustomStatus()).build());
                }
                log.info("getContainerAll,has result:[{}]", containerDtoList);
                return ResponseResult.ok().put(containerDtoList);
            }
            return ResponseResult.ok();
        }
        return ResponseResult.error((String)wcsContainerResponse.getMessage());
    }

    public WcsContainerResponse getAllContainer() {
        WcsContainerResponse wcsContainerResponse = this.wcsMissionServiceBlockingStub.queryAllContainer(EmptyParameterRequest.newBuilder().build());
        log.info("getAllContainer,has result:[{}]", (Object)wcsContainerResponse);
        return wcsContainerResponse;
    }

    public WcsContainerResponse getContainerByNode(String foreignNodeCode) {
        WcsContainerResponse wcsContainerResponse = this.wcsMissionServiceBlockingStub.queryContainerOfPoint(WcsContainerOfPointRequest.newBuilder().setNodeCode(foreignNodeCode).build());
        log.info("getContainerByNode,foreignNodeCode:[{}],has result:[{}]", (Object)foreignNodeCode, (Object)wcsContainerResponse);
        return wcsContainerResponse;
    }

    public String containerIn(ContainerMoveInDto containerMoveDto) {
        WcsContainerAddAndEnterResponse wcsContainerAddAndEnterResponse;
        log.info("container move in:[{}]", (Object)containerMoveDto);
        if (!containerMoveDto.getIsNew().booleanValue()) {
            if (StringUtils.isEmpty((Object)containerMoveDto.getContainerCode())) {
                throw new InterfaceAPIException("containerCode can not be blank");
            }
            WcsMoveContainerRequest.Builder moveContainerBuilder = WcsMoveContainerRequest.newBuilder().setNodeCode(containerMoveDto.getPosition()).setContainerCode(containerMoveDto.getContainerCode()).setRequestId(containerMoveDto.getRequestId()).setType(MoveContainerTypeGto.IN);
            MisUtils.isNotBlank((String)containerMoveDto.getEnterOrientation()).presentHandle(orientation -> moveContainerBuilder.setOrientation(orientation));
            WcsMissionGrpcResponse wcsMissionGrpcResponse = this.wcsMissionServiceBlockingStub.moveContainer(moveContainerBuilder.build());
            if (wcsMissionGrpcResponse.getSuccess()) {
                return containerMoveDto.getContainerCode();
            }
            log.error("Container move in error:{}", (Object)wcsMissionGrpcResponse.getMessage());
            throw new InterfaceAPIException(wcsMissionGrpcResponse.getResultCode(), wcsMissionGrpcResponse.getMessage());
        }
        WcsContainerAddAndEnterRequest.Builder addAndEnterRequest = WcsContainerAddAndEnterRequest.newBuilder().setEnterNodeCode(containerMoveDto.getPosition());
        MisUtils.isNotBlank((String)containerMoveDto.getEnterOrientation()).presentHandle(orientation -> addAndEnterRequest.setContainerEnterOrientation(orientation));
        if (StringUtils.hasText((String)containerMoveDto.getContainerCode())) {
            addAndEnterRequest.setContainerCode(containerMoveDto.getContainerCode());
        }
        if (StringUtils.hasText((String)containerMoveDto.getContainerModelCode())) {
            addAndEnterRequest.setContainerType(containerMoveDto.getContainerModelCode());
        }
        if (StringUtils.hasText((String)containerMoveDto.getContainerValidationCode())) {
            addAndEnterRequest.setContainerCheckCode(containerMoveDto.getContainerValidationCode());
        }
        if (Objects.nonNull(containerMoveDto.getWithDefaultValidationCode())) {
            addAndEnterRequest.setWithDefaultCheckCode(BoolValue.newBuilder().setValue(containerMoveDto.getWithDefaultValidationCode().booleanValue()).build());
        }
        if ((wcsContainerAddAndEnterResponse = this.wcsMissionServiceBlockingStub.addContainerAndEnter(addAndEnterRequest.build())).getSuccess()) {
            return wcsContainerAddAndEnterResponse.getContainerCode();
        }
        log.error("Container add and enter error:{}", (Object)wcsContainerAddAndEnterResponse.getMessage());
        throw new InterfaceAPIException(wcsContainerAddAndEnterResponse.getResultCode(), wcsContainerAddAndEnterResponse.getMessage());
    }

    @EventListener(value={ContainerMoveOutDto.class})
    public ResponseResult containerOut(ContainerMoveOutDto containerMoveOutDto) {
        WcsMissionGrpcResponse wcsMissionGrpcResponse;
        String containerCode = containerMoveOutDto.getContainerCode();
        String foreignNodeCode = containerMoveOutDto.getPosition();
        if (containerMoveOutDto.getIsDelete().booleanValue()) {
            WcsMissionGrpcResponse wcsMissionGrpcResponse2;
            log.info("departContainerAndRemove,foreignNodeCode:[{}],containerCode:[{}]", (Object)foreignNodeCode, (Object)containerCode);
            WcsContainerDepartAndRemoveRequest.Builder outDelRequest = WcsContainerDepartAndRemoveRequest.newBuilder();
            if (StringUtils.hasText((String)foreignNodeCode)) {
                outDelRequest.setDepartNodeCode(foreignNodeCode);
            }
            if (StringUtils.hasText((String)containerCode)) {
                outDelRequest.setContainerCode(containerCode);
            }
            if ((wcsMissionGrpcResponse2 = this.wcsMissionServiceBlockingStub.departContainerAndRemove(outDelRequest.build())).getSuccess()) {
                return ResponseResult.ok();
            }
            log.error("departContainerAndRemove error:[{}]", (Object)wcsMissionGrpcResponse2.getMessage());
            return ResponseResult.error((String)wcsMissionGrpcResponse2.getResultCode(), (String)wcsMissionGrpcResponse2.getMessage());
        }
        log.info("moveContainer,foreignNodeCode:[{}],containerCode:[{}]", (Object)foreignNodeCode, (Object)containerCode);
        WcsMoveContainerRequest.Builder outRequest = WcsMoveContainerRequest.newBuilder().setRequestId(MisUtils.getRequestId()).setType(MoveContainerTypeGto.OUT);
        if (StringUtils.hasText((String)foreignNodeCode)) {
            outRequest.setNodeCode(foreignNodeCode);
        }
        if (StringUtils.hasText((String)containerCode)) {
            outRequest.setContainerCode(containerCode);
        }
        if ((wcsMissionGrpcResponse = this.wcsMissionServiceBlockingStub.moveContainer(outRequest.build())).getSuccess()) {
            return ResponseResult.ok();
        }
        log.error("moveContainer error:[{}]", (Object)wcsMissionGrpcResponse.getMessage());
        return ResponseResult.error((String)wcsMissionGrpcResponse.getResultCode(), (String)wcsMissionGrpcResponse.getMessage());
    }

    public ResponseResult cancel(MissionCancelDto missionCancelDto) {
        String cancelMode = missionCancelDto.getCancelMode();
        log.info("invoke task cancel:[{}]", (Object)missionCancelDto);
        WcsCancelMissionRequest.Builder builder = WcsCancelMissionRequest.newBuilder().setCancelMode(CancelModeGto.valueOf((String)missionCancelDto.getCancelMode())).setRequestId(missionCancelDto.getRequestId()).setReason(MisUtils.checkNull2Empty((String)missionCancelDto.getReason()));
        WcsMissionGrpcResponse cancel = (WcsMissionGrpcResponse)this.notBlankApply(missionCancelDto.getMissionCode()).functionHandle(missionCode -> this.wcsMissionServiceBlockingStub.cancel(builder.setTaskCode(missionCode).build()));
        if (Objects.nonNull(cancel)) {
            return this.transferWCSGrpc(cancel);
        }
        cancel = (WcsMissionGrpcResponse)this.notBlankApply(missionCancelDto.getContainerCode()).functionHandle(containerCode -> this.wcsMissionServiceBlockingStub.cancel(builder.setContainerCode(containerCode).build()));
        if (Objects.nonNull(cancel)) {
            return this.transferWCSGrpc(cancel);
        }
        cancel = (WcsMissionGrpcResponse)this.notBlankApply(missionCancelDto.getPosition()).functionHandle(position -> this.wcsMissionServiceBlockingStub.cancel(builder.setNodeCode(position).build()));
        return this.transferWCSGrpc(cancel);
    }

    public ResponseResult missionPass(OperationFeedBackDto operationFeedBackDto) {
        WcsPassMissionRequest.Builder builder = WcsPassMissionRequest.newBuilder().setRequestId(operationFeedBackDto.getRequestId());
        WcsMissionGrpcResponse pass = (WcsMissionGrpcResponse)this.notBlankApply(operationFeedBackDto.getMissionCode()).functionHandle(missionCode -> this.wcsMissionServiceBlockingStub.pass(builder.setTaskCode(missionCode).build()));
        if (Objects.nonNull(pass)) {
            return this.transferWCSGrpc(pass);
        }
        pass = (WcsMissionGrpcResponse)this.notBlankApply(operationFeedBackDto.getContainerCode()).functionHandle(containerCode -> this.wcsMissionServiceBlockingStub.pass(builder.setContainerCode(containerCode).build()));
        if (Objects.nonNull(pass)) {
            return this.transferWCSGrpc(pass);
        }
        pass = (WcsMissionGrpcResponse)this.notBlankApply(operationFeedBackDto.getPosition()).functionHandle(position -> this.wcsMissionServiceBlockingStub.pass(builder.setNodeCode(position).build()));
        return this.transferWCSGrpc(pass);
    }

    public ResponseResult pauseResumeMission(String taskCode, String pauseOrResume, NodeGto nodeGto) {
        log.info("request pause or resume mission,taskCode:[{}],pauseOrResume:[{}]", (Object)taskCode, (Object)pauseOrResume);
        WcsPauseResumeMissionRequest.Builder builder = WcsPauseResumeMissionRequest.newBuilder().setTaskCode(taskCode).setType(PauseResumeTypeGto.valueOf((String)pauseOrResume));
        if (Objects.nonNull(nodeGto)) {
            builder.setRedirectNode(nodeGto);
        }
        WcsMissionGrpcResponse wcsMissionGrpcResponse = this.wcsMissionServiceBlockingStub.pauseResumeMission(builder.build());
        return this.transferWCSGrpc(wcsMissionGrpcResponse);
    }

    public ResponseResult containerUpdate(ContainerUpdateDto containerUpdateDto) {
        WcsUpdateContainerPositionRequest.Builder builder;
        WcsMissionGrpcResponse wcsMissionGrpcResponse = null;
        if (StringUtils.hasText((String)containerUpdateDto.getTargetPosition())) {
            builder = WcsUpdateContainerPositionRequest.newBuilder().setContainerCode(containerUpdateDto.getContainerCode()).setRequestId(containerUpdateDto.getRequestId()).setCurrentNodeCode(containerUpdateDto.getTargetPosition());
            if (containerUpdateDto.getOriginPosition() != null) {
                builder.setOriginalNodeCode(containerUpdateDto.getOriginPosition());
            }
            if (!(wcsMissionGrpcResponse = this.wcsMissionServiceBlockingStub.updateContainerPosition(builder.build())).getSuccess()) {
                return this.transferWCSGrpc(wcsMissionGrpcResponse);
            }
        }
        if (StringUtils.hasText((String)containerUpdateDto.getEmptyStatus())) {
            WcsUpdateContainerEmptyFullStatusRequest.Builder emptyFullStatusBuilder = WcsUpdateContainerEmptyFullStatusRequest.newBuilder().setContainerCode(containerUpdateDto.getContainerCode()).setEmptyFullStatus(Integer.valueOf(Objects.equals(containerUpdateDto.getEmptyStatus(), "EMPTY") ? ConfigConstants.EMPTY : ConfigConstants.FULL).intValue());
            wcsMissionGrpcResponse = this.wcsMissionServiceBlockingStub.updateContainerEmptyFullStatus(emptyFullStatusBuilder.build());
        }
        if (StringUtils.hasText((String)containerUpdateDto.getCustomStatus())) {
            builder = WcsUpdateContainerCustomStatusRequest.newBuilder().setContainerCode(containerUpdateDto.getContainerCode()).setCustomStatus(containerUpdateDto.getCustomStatus());
            wcsMissionGrpcResponse = this.wcsMissionServiceBlockingStub.updateContainerCustomStatus(builder.build());
        }
        if (Objects.isNull(wcsMissionGrpcResponse)) {
            return ResponseResult.ok();
        }
        return this.transferWCSGrpc(wcsMissionGrpcResponse);
    }

    public ResponseResult<List<String>> queryAreasForContainerMode(String containerModeCode, boolean noContainerFirst) {
        WcsAreaRequest wcsAreaRequest = WcsAreaRequest.newBuilder().setContainerModelCode(containerModeCode).setAreaWithIdleContainerFirst(!noContainerFirst).build();
        WcsAreaResponse wcsAreaResponse = this.wcsMissionServiceBlockingStub.queryDefaultAreaCodeByContainer(wcsAreaRequest);
        if (wcsAreaResponse.getSuccess()) {
            String areaCode = wcsAreaResponse.getAreaCode();
            return ResponseResult.ok().put((Object)Lists.newArrayList((Object[])new String[]{areaCode}));
        }
        return ResponseResult.error((String)wcsAreaResponse.getMessage());
    }

    private PresentFunctionHandler<String, WcsMissionGrpcResponse> notBlankApply(String param) {
        return function -> {
            if (!MisUtils.isBlank((String)param)) {
                return (WcsMissionGrpcResponse)function.apply(param);
            }
            return null;
        };
    }

    public WcsContainerResponse getContainerByCode(String containerCode) {
        WcsContainerRequest containerRequest = WcsContainerRequest.newBuilder().setContainerCode(containerCode).build();
        WcsContainerResponse wcsContainerResponse = this.wcsMissionServiceBlockingStub.queryContainer(containerRequest);
        log.info("getContainerByCode,containerCode:[{}],has result:[{}]", (Object)containerCode, (Object)wcsContainerResponse);
        return wcsContainerResponse;
    }

    public WcsContainerResponse getContainerByArea(String area) {
        WcsContainerWithEmptyFullStatusRequest emptyFullStatusRequest = WcsContainerWithEmptyFullStatusRequest.newBuilder().setAreaCode(area).setEmptyFullStatus(2).build();
        WcsContainerResponse wcsContainerResponse = this.wcsMissionServiceBlockingStub.queryContainerWithEmptyFullStatus(emptyFullStatusRequest);
        log.info("queryContainerWithEmptyFullStatus,areaCode:[{}],has result:[{}]", (Object)area, (Object)wcsContainerResponse);
        return wcsContainerResponse;
    }

    public WcsContainerResponse getContainerByCustomStatus(String customStatus) {
        WcsQueryContainerCustomStatusRequest emptyFullStatusRequest = WcsQueryContainerCustomStatusRequest.newBuilder().setCustomStatus(customStatus).build();
        WcsContainerResponse wcsContainerResponse = this.wcsMissionServiceBlockingStub.queryContainerByCustomStatus(emptyFullStatusRequest);
        log.info("queryContainerByCustomStatus,customStatus:[{}],has result:[{}]", (Object)customStatus, (Object)wcsContainerResponse);
        return wcsContainerResponse;
    }

    public ResponseResult<List<AreaDto>> queryArea() {
        WcsAllAreaResponse wcsAllAreaResponse = this.wcsMissionServiceBlockingStub.queryAllArea(EmptyParameterRequest.newBuilder().build());
        log.info("queryAllArea,has result:[{}]", (Object)wcsAllAreaResponse);
        if (wcsAllAreaResponse.getSuccess()) {
            List areaList = wcsAllAreaResponse.getAreaList();
            return ResponseResult.ok().put(areaList.stream().map(area -> AreaDto.builder().areaCode(area.getForeignCode()).areaName(area.getName()).areaType(Integer.valueOf(area.getType())).build()).collect(Collectors.toList()));
        }
        return ResponseResult.error((String)wcsAllAreaResponse.getMessage());
    }

    public ResponseResult<List<AreaNodeDto>> areaNodesQuery(List<String> areaCodes) {
        ArrayList areaNodesList = Lists.newArrayList();
        for (String areaCode : areaCodes) {
            WcsAreaNodeResponse wcsAreaNodeResponse = this.wcsMissionServiceBlockingStub.queryAreaNode(WcsAreaNodeRequest.newBuilder().setAreaCode(areaCode).build());
            if (!wcsAreaNodeResponse.getSuccess()) {
                return ResponseResult.error((String)wcsAreaNodeResponse.getMessage());
            }
            areaNodesList.add(AreaNodeDto.builder().areaCode(areaCode).nodeList(wcsAreaNodeResponse.getMapNodeList().stream().map(WcsMapNodeGto::getForeignCode).collect(Collectors.toList())).build());
        }
        return ResponseResult.ok().put((Object)areaNodesList);
    }

    public ResponseResult<AreaForNodeDto> queryWCSAreaByMapNode(String nodeCode) {
        WcsAreaResponse wcsAreaResponse = this.wcsMissionServiceBlockingStub.queryAreaOfPoint(WcsAreaOfPointRequest.newBuilder().setNodeCode(nodeCode).build());
        if (wcsAreaResponse.getSuccess()) {
            return ResponseResult.ok().put((Object)AreaForNodeDto.builder().areaCode(wcsAreaResponse.getAreaCode()).containerModelCode(wcsAreaResponse.getContainerModelCode()).build());
        }
        return ResponseResult.error((String)wcsAreaResponse.getMessage());
    }

    private ResponseResult transferWCSGrpc(WcsMissionGrpcResponse response) {
        if (response.getSuccess()) {
            return ResponseResult.ok();
        }
        log.info("invoke  wcs grpc error:[{}]", (Object)response.getMessage());
        if (!StringUtils.hasText((String)response.getResultCode())) {
            return ResponseResult.error((String)response.getMessage());
        }
        return ResponseResult.error((String)response.getResultCode(), (String)response.getMessage());
    }

    private PresentFunctionHandler<String, WcsContainerResponse> notBlankApplyContainer(String param) {
        return function -> {
            if (!MisUtils.isBlank((String)param)) {
                return (WcsContainerResponse)function.apply(param);
            }
            return null;
        };
    }

    public WcsContainerModelListResponse getAllContainerModel() {
        return this.wcsMissionServiceBlockingStub.queryAllContainerModel(EmptyParameterRequest.newBuilder().build());
    }

    public WcsBandAreaResponse queryAllBandArea() {
        log.info("queryBandArea request");
        WcsBandAreaResponse wcsBandAreaResponse = this.wcsBandAreaServiceBlockingStub.queryAllBandArea(NoParameterRequest.newBuilder().build());
        log.info("queryBandArea result:[{}]", (Object)wcsBandAreaResponse);
        return wcsBandAreaResponse;
    }

    public WcsBandAreaResponse queryOneBandArea(WcsQueryOneBandAreaRequest request) {
        log.info("queryOneBandArea request:[{}]", (Object)request);
        WcsBandAreaResponse wcsBandAreaResponse = this.wcsBandAreaServiceBlockingStub.queryOneBandArea(request);
        log.info("queryBandArea result:[{}]", (Object)wcsBandAreaResponse);
        return wcsBandAreaResponse;
    }

    public WcsCommonResponse updateBandArea(UpdateForbiddenAreaDto areaDto) {
        log.info("updateBandArea request:[id: {}, code: {}, status: {}]", new Object[]{areaDto.getForbiddenAreaId(), areaDto.getForbiddenAreaCode(), areaDto.getStatus()});
        WcsBandAreaStatus byValue = this.getByValue(areaDto.getStatus());
        if (Objects.isNull(byValue)) {
            throw new RuntimeException("status[" + areaDto.getStatus() + "] not accept");
        }
        WcsUpdateBandAreaStatusRequest.Builder request = WcsUpdateBandAreaStatusRequest.newBuilder();
        Long areaId = areaDto.getForbiddenAreaId();
        String areaCode = areaDto.getForbiddenAreaCode();
        if (areaId == null) {
            request.setAreaId(0L);
        } else {
            request.setAreaId(areaId.longValue());
        }
        if (areaCode == null) {
            request.setAreaCode("");
        } else {
            request.setAreaCode(areaCode);
        }
        WcsCommonResponse wcsCommonResponse = this.wcsBandAreaServiceBlockingStub.updateBandAreaStatus(request.setStatus(this.getByValue(areaDto.getStatus())).build());
        log.info("updateBandArea result:[{}]", (Object)wcsCommonResponse);
        return wcsCommonResponse;
    }

    public ResponseResult queryFunctionNode(QueryFunctionNodeReq queryFunctionNodeReq) {
        log.info("queryFunctionNode, request:{}", (Object)queryFunctionNodeReq);
        WcsQueryFunctionNodeRequest.Builder builder = WcsQueryFunctionNodeRequest.newBuilder().setFunctionType(queryFunctionNodeReq.getFunctionType().intValue());
        if (!StringUtils.isEmpty((Object)queryFunctionNodeReq.getMapCode())) {
            builder.setMapCode(queryFunctionNodeReq.getMapCode());
        }
        if (!StringUtils.isEmpty((Object)queryFunctionNodeReq.getFloorNumber())) {
            builder.setFloorNumber(queryFunctionNodeReq.getFloorNumber());
        }
        if (!StringUtils.isEmpty((Object)queryFunctionNodeReq.getContainerModel())) {
            builder.setContainerModel(queryFunctionNodeReq.getContainerModel());
        }
        if (!StringUtils.isEmpty((Object)queryFunctionNodeReq.getRobotTypeCode())) {
            builder.setRobotTypeCode(queryFunctionNodeReq.getRobotTypeCode());
        }
        WcsQueryFunctionNodeResponse response = this.wcsDataServiceBlockingStub.queryFunctionNode(builder.build());
        ResponseResult result = ResponseResult.ok();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setMessage(response.getMessage());
        result.setCode(response.getResultCode());
        result.setData(response.getNodesList().stream().map(n -> QueryFunctionNodeRes.builder().nodeCode(n.getNodeCode()).externalCode(n.getExternalCode()).functionType(Integer.valueOf(n.getFunctionType())).containerModelCode((List)n.getContainerModelCodeList()).robotTypeCode((List)n.getRobotTypeCodeList()).build()).collect(Collectors.toList()));
        return result;
    }

    public ResponseResult robotMoveCarry(RobotMoveCarryReq req) {
        log.info("robotMoveCarry, request:{}", (Object)req);
        RobCarryRequest.Builder builder = RobCarryRequest.newBuilder().setRobotId(req.getRobotId()).setContainerCode(req.getContainerCode()).setTargetNodeCode(req.getTargetNodeCode());
        if (!StringUtils.isEmpty((Object)req.getMissionCode())) {
            builder.setMissionCode(req.getMissionCode());
        }
        RobCarryResponse response = this.wcsRobotServiceBlockingStub.carry(builder.build());
        ResponseResult result = ResponseResult.ok();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setMessage(response.getMessage());
        result.setCode(response.getResultCode());
        result.setData((Object)response.getData());
        log.info("robotMoveCarry result:[{}]", (Object)result);
        return result;
    }

    private WcsBandAreaStatus getByValue(String key) {
        if (Objects.isNull(this.bandAreaMap)) {
            this.bandAreaMap = new HashMap<String, WcsBandAreaStatus>();
            this.bandAreaMap.put("0", WcsBandAreaStatus.BAND_STATUS_DISABLE);
            this.bandAreaMap.put("1", WcsBandAreaStatus.BAND_STATUS_ENABLE);
        }
        return this.bandAreaMap.get(key);
    }

    public String queryNodeCodeOfSlot(String slotCode) {
        try {
            if (!StringUtils.hasText((String)slotCode)) {
                return null;
            }
            WcsMapCellCodeResponse response = this.wcsMissionServiceBlockingStub.queryRelatedMapCellOfSlot(WcsRelatedMapCellOfSlotRequest.newBuilder().setSlotCode(slotCode).build());
            return StringUtils.hasText((String)response.getMapCellForeignCode()) ? response.getMapCellForeignCode() : response.getMapCellCode();
        }
        catch (Exception e) {
            log.error("queryNodeCodeOfSlot error, slotCode:{}", (Object)slotCode, (Object)e);
            return null;
        }
    }

    public List<RobotDto> queryRobByNodeCode(String nodeCode) {
        try {
            log.info(this.wcsDataServiceBlockingStub.toString());
            WcsQueryRobotOfNodeResponse response = this.wcsDataServiceBlockingStub.queryRobByNodeUuidOrForeignCode(WcsQueryRobByNodeUuidOrForeignCodeRequest.newBuilder().setNodeCode(nodeCode).build());
            log.info("response:{}", (Object)response);
            List robotGtos = response.getRobotList();
            List<RobotDto> res = robotGtos.stream().map(this::buildRobotInfo).collect(Collectors.toList());
            return res;
        }
        catch (Exception e) {
            log.error("queryRobByNodeCode error, nodeCode:{}, exception:", (Object)nodeCode, (Object)e);
            return null;
        }
    }

    public ResponseResult<List<JobRecordDto>> queryJobs(QueryJobDto queryJobDto) {
        QueryJobRequest.Builder builder = QueryJobRequest.newBuilder();
        if (queryJobDto.getWorkflowId() != null) {
            builder.setWorkflowId(Int64Value.of((long)queryJobDto.getWorkflowId()));
        }
        if (StringUtils.hasLength((String)queryJobDto.getContainerCode())) {
            builder.setContainerCode(StringValue.of((String)queryJobDto.getContainerCode()));
        }
        if (StringUtils.hasLength((String)queryJobDto.getJobCode())) {
            builder.setJobCode(StringValue.of((String)queryJobDto.getJobCode()));
        }
        if (queryJobDto.getStatus() != null) {
            builder.setStatus(Int32Value.of((int)queryJobDto.getStatus()));
        }
        if (StringUtils.hasLength((String)queryJobDto.getRobotId())) {
            builder.setRobotId(StringValue.of((String)queryJobDto.getRobotId()));
        }
        if (StringUtils.hasLength((String)queryJobDto.getTargetCellCode())) {
            builder.setTargetCellCode(StringValue.of((String)queryJobDto.getTargetCellCode()));
        }
        if (StringUtils.hasLength((String)queryJobDto.getWorkflowName())) {
            builder.setWorkflowName(StringValue.of((String)queryJobDto.getWorkflowName()));
        }
        if (StringUtils.hasLength((String)queryJobDto.getWorkflowCode())) {
            builder.setWorkflowCode(StringValue.of((String)queryJobDto.getWorkflowCode()));
        }
        if (!CollectionUtils.isEmpty((Collection)queryJobDto.getMaps())) {
            builder.addAllMaps((Iterable)queryJobDto.getMaps());
        }
        if (StringUtils.hasLength((String)queryJobDto.getCreateUsername())) {
            builder.setCreateUsername(StringValue.of((String)queryJobDto.getCreateUsername()));
        }
        if (queryJobDto.getSourceValue() != null) {
            builder.setSourceValue(Int32Value.of((int)queryJobDto.getSourceValue()));
        }
        if (queryJobDto.getLimit() != null) {
            builder.setLimit(Int32Value.of((int)queryJobDto.getLimit()));
        }
        ResponseResult result = new ResponseResult();
        QueryJobResponse queryJobResponse = this.wcsMissionServiceBlockingStub.queryJobs(builder.build());
        if (queryJobResponse.getSuccess()) {
            ArrayList<JobRecordDto> jobs = new ArrayList<JobRecordDto>();
            if (queryJobResponse.getJobsCount() > 0) {
                List jobRecordGtos = queryJobResponse.getJobsList();
                for (JobRecordGto job : jobRecordGtos) {
                    jobs.add(JobRecordDto.builder().jobCode(job.getJobCode().getValue()).workflowId(job.hasWorkflowId() ? Long.valueOf(job.getWorkflowId().getValue()) : null).containerCode(job.hasContainerCode() ? job.getContainerCode().getValue() : null).robotId(job.hasRobotId() ? job.getRobotId().getValue() : null).status(job.hasStatus() ? Integer.valueOf(job.getStatus().getValue()) : null).workflowName(job.hasWorkflowName() ? job.getWorkflowName().getValue() : null).workflowCode(job.hasWorkflowCode() ? job.getWorkflowCode().getValue() : null).workflowPriority(job.hasWorkflowPriority() ? Integer.valueOf(job.getWorkflowPriority().getValue()) : null).mapCode(job.hasMapCode() ? job.getMapCode().getValue() : null).targetCellCode(job.hasTargetCellCode() ? job.getTargetCellCode().getValue() : null).beginCellCode(job.hasBeginCellCode() ? job.getBeginCellCode().getValue() : null).targetCellCodeForeign(job.hasTargetCellCodeForeign() ? job.getTargetCellCodeForeign().getValue() : null).beginCellCodeForeign(job.hasBeginCellCodeForeign() ? job.getBeginCellCodeForeign().getValue() : null).finalNodeCode(job.hasFinalNodeCode() ? job.getFinalNodeCode().getValue() : null).warnFlag(job.hasWarnFlag() ? Integer.valueOf(job.getWarnFlag().getValue()) : null).warnCode(job.hasWarnCode() ? job.getWarnCode().getValue() : null).completeTime(job.hasCompleteTime() ? job.getCompleteTime().getValue() : null).spendTime(job.hasSpendTime() ? Integer.valueOf(job.getSpendTime().getValue()) : null).createUsername(job.hasCreateUsername() ? job.getCreateUsername().getValue() : null).createTime(job.hasCreateTime() ? job.getCreateTime().getValue() : null).source(job.hasSource() ? job.getSource().getValue() : null).materialsInfo(job.hasMaterialsInfo() ? job.getMaterialsInfo().getValue() : null).build());
                }
            }
            result.setSuccess(Boolean.valueOf(true));
            result.setData(jobs);
        } else {
            result.setSuccess(Boolean.valueOf(false));
            result.setCode(queryJobResponse.getResultCode());
            result.setMessage(queryJobResponse.getMessage());
        }
        return result;
    }

    private RobotDto buildRobotInfo(WcsRobotGto mobileRobotGto) {
        String robotId = mobileRobotGto.getRobotId();
        RobotDto.RobotDtoBuilder robotDtoBuilder = RobotDto.builder().robotId(mobileRobotGto.getRobotId()).robotType(mobileRobotGto.getRobotTypeCode()).mapCode(mobileRobotGto.getMapCode()).floorNumber(mobileRobotGto.getFloorNumber()).buildingCode(mobileRobotGto.getBuildingCode()).containerCode(mobileRobotGto.getContainerCode()).nodeCode(mobileRobotGto.getLastNodeUuid()).occupyStatus(Integer.valueOf(mobileRobotGto.getOccupyStatus())).status(Integer.valueOf(mobileRobotGto.getStatus())).nodeForeignCode(mobileRobotGto.getNodeForeignCode());
        RealtimeDataQueryResponse realtimeDataQueryResponse = this.robotServiceBlockingStub.getRealtimeDataByRobotId(RobotIdRequest.newBuilder().setRobotId(robotId).build());
        log.info("query robot:[{}] realtime status:[{}]", (Object)robotId, (Object)realtimeDataQueryResponse);
        MobileRobotRealtimeGto realtimeData = realtimeDataQueryResponse.getRealtimeData();
        if (Objects.nonNull(realtimeData)) {
            boolean isCharging;
            DoubleValue yCoordinate;
            DoubleValue xCoordinate;
            DoubleValue robotOrientation = realtimeData.getRobotOrientation();
            if (Objects.nonNull(robotOrientation)) {
                robotDtoBuilder.robotOrientation(String.valueOf(robotOrientation.getValue()));
            }
            if (Objects.nonNull(xCoordinate = realtimeData.getXCoordinate())) {
                robotDtoBuilder.x(String.valueOf(xCoordinate.getValue() * 1000.0));
            }
            if (Objects.nonNull(yCoordinate = realtimeData.getYCoordinate())) {
                robotDtoBuilder.y(String.valueOf(yCoordinate.getValue() * 1000.0));
            }
            if (isCharging = realtimeData.getBatteryIsCharging().getValue()) {
                robotDtoBuilder.status(Integer.valueOf(5));
            }
            if (Objects.nonNull(realtimeData.getLiftState())) {
                if (realtimeData.getLiftState().getValue()) {
                    robotDtoBuilder.liftStatus(Integer.valueOf(1));
                } else {
                    robotDtoBuilder.liftStatus(Integer.valueOf(0));
                }
            }
        }
        return robotDtoBuilder.build();
    }

    public ResponseResult insertRobot(InsertRobotRequest request) {
        log.info("insertRobot:{}", (Object)request);
        InsertRobResponse insertRobResponse = this.wcsRobotServiceBlockingStub.insertRobot(InsertRobRequest.newBuilder().setRobId(request.getRobotId()).setCellCode(request.getCellCode()).setSynchroContainer(request.getSynchroContainer() == 1).build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(insertRobResponse.getSuccess()));
        result.setMessage(insertRobResponse.getMessage());
        result.setCode(insertRobResponse.getResultCode());
        log.info("insertRobot response:{}", (Object)insertRobResponse);
        return result;
    }

    public ResponseResult removeRobot(RemoveRobotRequest request) {
        log.info("removeRobot:{}", (Object)request);
        RemoveRobRequest.Builder builder = RemoveRobRequest.newBuilder().setRobId(request.getRobotId());
        if (request.getWithContainer() != null) {
            builder.setWithContainer(BoolValue.of((boolean)request.getWithContainer().equals(1)));
        }
        RemoveRobResponse response = this.wcsRobotServiceBlockingStub.removeRobot(builder.build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setMessage(response.getMessage());
        result.setCode(response.getResultCode());
        log.info("removeRobot response:{}", (Object)response);
        return result;
    }

    public ResponseResult chargeRobot(ChargeRobotRequest request) {
        log.info("chargeRobot:{}", (Object)request);
        ChargeRobRequest.Builder builder = ChargeRobRequest.newBuilder().setRobId(request.getRobotId()).setNecessary(request.getNecessary() == 1).setTargetLevel(request.getTargetLevel().intValue());
        if (request.getLowestLevel() != null) {
            builder.setLowestLevel(request.getLowestLevel().intValue());
        }
        if (!StringUtils.isEmpty((Object)request.getMissionCode())) {
            builder.setMissionCode(request.getMissionCode());
        }
        ChargeRobResponse response = this.wcsRobotServiceBlockingStub.chargeRobot(builder.build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setMessage(response.getMessage());
        result.setCode(response.getResultCode());
        log.info("chargeRobot response:{}", (Object)response);
        return result;
    }

    public ResponseResult pauseMission(PauseResumeMissionRequest request) {
        String missionCode = request.getMissionCode();
        String robotId = request.getRobotId();
        log.info("PauseMission, mission code:{}, robotId:{}", (Object)missionCode, (Object)robotId);
        WcsMissionGrpcResponse response = null;
        response = StringUtils.hasLength((String)robotId) ? this.wcsMissionServiceBlockingStub.pauseRobot(WcsPauseRobotRequest.newBuilder().setRobotId(robotId).build()) : this.wcsMissionServiceBlockingStub.pauseResumeMission(WcsPauseResumeMissionRequest.newBuilder().setTaskCode(missionCode).setType(PauseResumeTypeGto.PAUSE).build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("PauseMission response:{}", (Object)response);
        return result;
    }

    public ResponseResult recoverMission(PauseResumeMissionRequest request) {
        String missionCode = request.getMissionCode();
        String robotId = request.getRobotId();
        log.info("RecoverMission, mission code:{}, robotId:{}", (Object)missionCode, (Object)robotId);
        WcsMissionGrpcResponse response = null;
        response = StringUtils.hasLength((String)robotId) ? this.wcsMissionServiceBlockingStub.resumeRobot(WcsResumeRobotRequest.newBuilder().setRobotId(robotId).build()) : this.wcsMissionServiceBlockingStub.pauseResumeMission(WcsPauseResumeMissionRequest.newBuilder().setTaskCode(missionCode).setType(PauseResumeTypeGto.RESUME).build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("RecoverMission response:{}", (Object)response);
        return result;
    }

    public ResponseResult retryMission(MissionRetryRequest request) {
        log.info("RetryMission, request:{}", (Object)request);
        WcsRetryMissionRequest.Builder builder = WcsRetryMissionRequest.newBuilder();
        if (!StringUtils.isEmpty((Object)request.getTaskCode())) {
            builder.setTaskCode(request.getTaskCode());
        }
        if (!StringUtils.isEmpty((Object)request.getContainerCode())) {
            builder.setContainerCode(request.getContainerCode());
        }
        if (!StringUtils.isEmpty((Object)request.getRobotId())) {
            builder.setRobotId(request.getRobotId());
        }
        WcsMissionGrpcResponse response = this.wcsMissionServiceBlockingStub.retry(builder.build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("RetryMission response:{}", (Object)response);
        return result;
    }

    public ResponseResult unlockRobot(UnlockRobotRequest request) {
        log.info("UnlockRobot, request:{}", (Object)request);
        WcsMissionGrpcResponse response = this.wcsMissionServiceBlockingStub.unlockRobot(WcsUnlockRobotRequest.newBuilder().setRobotId(request.getRobotId()).build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("UnlockRobot response:{}", (Object)response);
        return result;
    }

    public ResponseResult robotLift(RobotLiftReq request) {
        log.info("RobotLift, request:{}", (Object)request);
        RobLiftRequest.Builder builder = RobLiftRequest.newBuilder().setRobotId(request.getRobotId());
        if (!StringUtils.isEmpty((Object)request.getContainerCode())) {
            builder.setContainerCode(request.getContainerCode());
        }
        if (request.getMoveLift() != null) {
            builder.setMoveLift(BoolValue.of((request.getMoveLift() == 1 ? 1 : 0) != 0));
        }
        if (!StringUtils.isEmpty((Object)request.getMissionCode())) {
            builder.setMissionCode(request.getMissionCode());
        }
        WcsCommonResponse response = this.wcsRobotServiceBlockingStub.robotLift(builder.build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("RobotLift response:{}", (Object)response);
        return result;
    }

    public ResponseResult robotDrop(RobotDropReq request) {
        log.info("RobotDrop, request:{}", (Object)request);
        RobDropRequest.Builder builder = RobDropRequest.newBuilder().setRobotId(request.getRobotId());
        if (!StringUtils.isEmpty((Object)request.getNodeCode())) {
            builder.setNodeCode(request.getNodeCode());
        }
        if (!StringUtils.isEmpty((Object)request.getMissionCode())) {
            builder.setMissionCode(request.getMissionCode());
        }
        WcsCommonResponse response = this.wcsRobotServiceBlockingStub.robotDrop(builder.build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("RobotDrop response:{}", (Object)response);
        return result;
    }

    public ResponseResult robotMove(RobotMoveReq request) {
        log.info("RobotMove, request:{}", (Object)request);
        RobMoveRequest.Builder builder = RobMoveRequest.newBuilder().setRobotId(request.getRobotId());
        if (!StringUtils.isEmpty((Object)request.getNodeCode())) {
            builder.setNodeCode(request.getNodeCode());
        }
        if (!StringUtils.isEmpty((Object)request.getMissionCode())) {
            builder.setMissionCode(request.getMissionCode());
        }
        WcsCommonResponse response = this.wcsRobotServiceBlockingStub.robotMove(builder.build());
        ResponseResult result = new ResponseResult();
        result.setSuccess(Boolean.valueOf(response.getSuccess()));
        result.setCode(response.getResultCode());
        result.setMessage(response.getMessage());
        result.setData((Object)response.getData());
        log.info("RobotMove response:{}", (Object)response);
        return result;
    }

    public List<WcsPipelineDeviceRelatedNodeGto> getDeviceRelateNode(String deviceCode) {
        WcsRelatedMapCellOfPipelineRequest pipelineRequest = WcsRelatedMapCellOfPipelineRequest.newBuilder().setPipelineDeviceCode(deviceCode).build();
        WcsPipelineDeviceRelatedNodeResponse relatedNodeResponse = this.wcsMissionServiceBlockingStub.queryRelatedMapCellOfPipeline(pipelineRequest);
        log.info("getDeviceRelateNode,deviceCode:[{}],has result:[{}]", (Object)deviceCode, (Object)relatedNodeResponse);
        if (relatedNodeResponse.getSuccess()) {
            return relatedNodeResponse.getRelatedNodeList();
        }
        return Lists.newArrayList();
    }

    public WcsQueryCommandResponse queryCommand(String commandCode) {
        try {
            return this.wcsDataServiceBlockingStub.queryCommand(WcsQueryCommandRequest.newBuilder().setCommandCode(commandCode).build());
        }
        catch (Exception e) {
            log.error("queryCommand error, msg:{}", (Object)e.getMessage());
            return null;
        }
    }

    public RobPayloadPoseResponse queryRobotPayloadPose(List<String> robotIds) {
        try {
            return this.wcsRobotServiceBlockingStub.getRobotPayloadPose(RobPayloadPoseRequest.newBuilder().addAllRobotId(robotIds).build());
        }
        catch (Exception e) {
            log.error("queryRobotPayloadPose error, e:", (Throwable)e);
            return null;
        }
    }

    public OpportunityChargingResponse sendOppCharging(OppChargingRequest request) {
        boolean enable = request.getEnable();
        String nodes = request.getNodes().replace(" ", "");
        List<String> nodeUuids = Arrays.asList(nodes.split(","));
        OpportunityChargingResponse response = this.wcsRobotServiceBlockingStub.sendOpportunityCharging(OpportunityChargingRequest.newBuilder().addAllNodeUuids(nodeUuids).setEnable(enable).build());
        return response;
    }
}
