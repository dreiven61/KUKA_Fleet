/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.kuka.util.Pair
 *  com.vividsolutions.jts.geom.Geometry
 *  com.vividsolutions.jts.geom.GeometryFactory
 *  com.vividsolutions.jts.geom.Polygon
 *  org.apache.commons.collections4.CollectionUtils
 *  org.apache.commons.collections4.IterableUtils
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission;

import com.kuka.rcs.as.pathplanner.config.BeanHolder;
import com.kuka.rcs.as.pathplanner.fleet.base.NoPlanFoundException;
import com.kuka.rcs.as.pathplanner.fleet.base.OrientationSet;
import com.kuka.rcs.as.pathplanner.fleet.base.actions.CustomActionRegistry;
import com.kuka.rcs.as.pathplanner.fleet.base.bezier.BezierCurveTopologyGraphUtil;
import com.kuka.rcs.as.pathplanner.fleet.base.bezier.TopologyGraphBezierCurveSampleUtils;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyEdge;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyGraph;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyHelper;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyNode;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeBezierCurveProperty;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeMotion;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.NodeRotationType;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.NodeType;
import com.kuka.rcs.as.pathplanner.fleet.base.provider.util.FleetProviderUtil;
import com.kuka.rcs.as.pathplanner.fleet.base.provider.util.PropertyUtil;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.CollisionInformation;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.CollisionInformationFactory;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.CollisionUtil;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.PlanningShape;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.RotateType;
import com.kuka.rcs.as.pathplanner.fleet.plan.cost.wait.graph.MotionGraph;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.DispatchedGraphMotion;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.GraphPlannerConfig;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.PlannerException;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.AgentConfig;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.BaseMissionNode;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.BlendingKey;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.BlendingNode;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.IntermediateMissionNode;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.MissionEdge;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.MissionGraphAccelerationTimeCalculator;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.MissionGraphConfig;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.MissionGraphWaitCostCalculator;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.MissionNodeType;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.RecoveryMissionNode;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.StartMissionNode;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.TopologyMissionNode;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.WeightCalculationMethod;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.colli.MissionColliChecker;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.colli.MissionColliCheckerForPlan;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.CostFunctionFactory;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.IMissionCostFunction;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.MissionCostFunction;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.MissionCostFunctionDynamicWeightSupport;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.MissionCostFunctionForAstar;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.MissionCostFunctionManualWeightSupport;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.MissionCostFunctionObstacleCostSupport;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.cost.MissionCostFunctionWaitCostSupport;
import com.kuka.rcs.as.pathplanner.fleet.plan.service.util.JtsUtils;
import com.kuka.rcs.as.pathplanner.fleet.plan.util.FleetUtil;
import com.kuka.rcs.as.pathplanner.fleet.plan.util.RecoveryNodesAndEdgesUtil;
import com.kuka.rcs.as.pathplanner.nav.NavUtil;
import com.kuka.rcs.as.pathplanner.nav.OrientationMode;
import com.kuka.rcs.as.pathplanner.nav.OrientationUtils;
import com.kuka.rcs.as.pathplanner.nav.Pose;
import com.kuka.rcs.as.pathplanner.nav.Position;
import com.kuka.rcs.as.pathplanner.nav.Tuple2D;
import com.kuka.rcs.as.pathplanner.nav.geometry.BezierCurveDiscretizationInfo;
import com.kuka.rcs.as.pathplanner.nav.geometry.BlendingDiscretizationInfo;
import com.kuka.rcs.as.pathplanner.nav.robot.data.DriveType;
import com.kuka.rcs.as.pathplanner.nav.robot.data.RobotTypeClass;
import com.kuka.rcs.as.pathplanner.share.graph.alg.Astar;
import com.kuka.rcs.as.pathplanner.share.graph.impl.FastUncheckedGraph;
import com.kuka.util.Pair;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MissionGraph
extends FastUncheckedGraph<BaseMissionNode, MissionEdge> {
    private static final Logger LOG = LoggerFactory.getLogger(MissionGraph.class);
    private static final Comparator<BaseMissionNode> ORIENTATION_COMP = (o1, o2) -> {
        assert (o1.isTopology() && o2.isTopology()) : "Orientations should only be compared for topology nodes.";
        return (int)Math.signum(o1.getOrientation() - o2.getOrientation());
    };
    public static final double WAIT_COST = 0.001;
    private static final double ROTATION_BLENDING_COST = 0.001;
    private static final double BLENDING_BONUS = 0.9999;
    private static final double EPSILON = 1.0E-11;
    private static final List<Double> ORTHOGONALITY_ANGLES = Arrays.asList(0.0, Math.toRadians(180.0), Math.toRadians(90.0), Math.toRadians(-90.0));
    private final int agentId;
    private final TopologyGraph topoGraph;
    private final AgentConfig config;
    private final CustomActionRegistry reg;
    private final Map<TopologyNode, Map<Pair<Double, Double>, BaseMissionNode>> topNodeToTopMissionNodes = new HashMap<TopologyNode, Map<Pair<Double, Double>, BaseMissionNode>>();
    private final Set<BaseMissionNode> missionNodesRotationHandled = new HashSet<BaseMissionNode>();
    private final Set<TopologyNode> topNodesRotationHandled = new HashSet<TopologyNode>();
    private final MissionCostFunction costFunction;
    private BaseMissionNode start;
    private Set<BaseMissionNode> goals = new HashSet<BaseMissionNode>();
    private TopologyNode topGoal;
    private final Map<BaseMissionNode, Set<MissionEdge>> incomingEdges = new HashMap<BaseMissionNode, Set<MissionEdge>>();
    private final Map<Pair<BaseMissionNode, BaseMissionNode>, CollisionInformation> collisionInfo = new HashMap<Pair<BaseMissionNode, BaseMissionNode>, CollisionInformation>();
    private final Map<BlendingKey, BaseMissionNode> blendingNodes = new HashMap<BlendingKey, BaseMissionNode>();
    private final MissionGraphConfig cfg;
    Map<Map<TopologyNode, TopologyNode>, Double[]> bezierCurves = new HashMap<Map<TopologyNode, TopologyNode>, Double[]>();
    private boolean recovered = false;
    private boolean needChangeGraph = true;
    private MissionGraphAccelerationTimeCalculator accelerationTimeCalculator;
    private final MissionCostFunctionForAstar costFunctionForAstar;
    private final List<Astar.Listener<BaseMissionNode, MissionEdge>> listeners = new ArrayList<Astar.Listener<BaseMissionNode, MissionEdge>>();

    public MissionGraph(TopologyGraph graph, int agentId, AgentConfig config, CustomActionRegistry registry, MissionGraphConfig cfg) {
        super(null, null);
        this.agentId = agentId;
        this.topoGraph = graph;
        this.config = config;
        this.reg = registry;
        this.bezierCurves = graph.getBezierCurves();
        this.cfg = cfg;
        this.costFunction = CostFunctionFactory.getMissionCostFunction(this);
        this.costFunctionForAstar = new MissionCostFunctionForAstar(this.costFunction);
        if (cfg.getWeightCalculationMethod() == WeightCalculationMethod.MANUAL || cfg.isWeightCalculationMixed()) {
            this.costFunctionForAstar.addCostAccessor(new MissionCostFunctionManualWeightSupport(this.costFunction));
        }
    }

    public void setForDynamicWeight(Map<TopologyEdge, Double> dynamicWeightMap) {
        if (this.cfg.getWeightCalculationMethod() != WeightCalculationMethod.DYNAMIC_WEIGHT) {
            return;
        }
        LOG.debug("DYNAMIC_WEIGHT enabled");
        this.costFunctionForAstar.addCostAccessor(new MissionCostFunctionDynamicWeightSupport(this.costFunction, dynamicWeightMap));
    }

    public void setForDynamicCost(MotionGraph motionGraph) {
        if (this.cfg.getWeightCalculationMethod() != WeightCalculationMethod.DYNAMIC_COST) {
            return;
        }
        LOG.debug("DYNAMIC_COST enabled");
        MissionGraphWaitCostCalculator waitCostCalculator = new MissionGraphWaitCostCalculator(this.agentId, this, motionGraph);
        this.accelerationTimeCalculator = new MissionGraphAccelerationTimeCalculator(this.config, this);
        this.listeners.add(waitCostCalculator);
        this.listeners.add(this.accelerationTimeCalculator);
        this.costFunctionForAstar.addCostAccessor(new MissionCostFunctionWaitCostSupport(this.costFunction, waitCostCalculator));
    }

    public void setForCrossObstacle(MissionColliCheckerForPlan checker) {
        LOG.debug("FIND_AVOID_STEP enabled");
        MissionCostFunctionObstacleCostSupport obstacleCostSupport = new MissionCostFunctionObstacleCostSupport(this.costFunction, checker);
        this.costFunctionForAstar.addCostAccessor(obstacleCostSupport);
        this.listeners.add(obstacleCostSupport);
    }

    public void setForCrossObstacle(MissionColliChecker checker, double maxCost) {
        LOG.debug("CHECK_GOAL enabled");
        MissionCostFunctionObstacleCostSupport obstacleCostSupport = new MissionCostFunctionObstacleCostSupport(this.costFunction, checker, maxCost);
        this.costFunctionForAstar.addCostAccessor(obstacleCostSupport);
        this.listeners.add(obstacleCostSupport);
    }

    public TopologyGraph getTopologyGraph() {
        return this.topoGraph;
    }

    @Override
    public Set<BaseMissionNode> neighbors(BaseMissionNode node) {
        this.checkInterrupedStatus();
        if (this.containsNode(node) && !node.getNeighborsCreated() && this.checkGraphChange(node)) {
            MissionNodeType type = node.getType();
            switch (type) {
                case RECOVERY: {
                    this.expandFromTopologyNode(node, Collections.singleton(((RecoveryMissionNode)node).getRecoverEdge()));
                    break;
                }
                case TOPOLOGY: {
                    this.expandFromTopologyNode(node, this.topoGraph.outgoingEdges(node.getOriginalNode()));
                    break;
                }
                case BLENDING: {
                    this.expandFromBlendingNode(node);
                    break;
                }
                default: {
                    String error = "Trying to expand neighbors of " + (Object)((Object)type) + "-mission graph node which should be expanded upon construction.";
                    LOG.error(error);
                    throw new IllegalArgumentException(error);
                }
            }
            node.setNeighborsCreated();
        }
        return super.neighbors(node);
    }

    @Override
    public Set<MissionEdge> incomingEdges(BaseMissionNode node) {
        return this.incomingEdges.getOrDefault(node, Collections.emptySet());
    }

    private boolean checkGraphChange(BaseMissionNode node) {
        if (!this.needChangeGraph) {
            return node.getOriginalNode().getGraphId() == this.start.getOriginalNode().getGraphId();
        }
        return true;
    }

    private void checkInterrupedStatus() {
        if (Thread.currentThread().isInterrupted()) {
            LOG.debug("Planner thread was interrupted. Stopping planning.");
            throw new NoPlanFoundException("The planner thread was interrupted. Stopping planning.");
        }
    }

    private void expandFromTopologyNode(BaseMissionNode n, Set<TopologyEdge> edgesToExpand) {
        boolean isRotationAllowed;
        this.checkAndAddGoalNode(n);
        boolean bl = isRotationAllowed = this.config.getRobotType().getIsRotationAllowed() != null ? this.config.getRobotType().getIsRotationAllowed() : true;
        if (!this.config.isTuggingPlanning() && PropertyUtil.isAllowed(n.getOriginalNode(), this.config) && !n.isRecoveredStartNode() && this.topNodesRotationHandled.add(n.getOriginalNode()) && isRotationAllowed) {
            ArrayList<BaseMissionNode> existingMissionNodes = new ArrayList<BaseMissionNode>(this.getTopToMissionMapping(n.getOriginalNode()).values());
            this.createRotationNodesAndEdges(n);
            if (this.config.getPayloadDiff() != null) {
                for (BaseMissionNode missionNode : existingMissionNodes) {
                    if (this.missionNodesRotationHandled.contains(missionNode) || Double.isNaN(missionNode.getPayloadOri()) || Double.isNaN(missionNode.getPose().getTheta())) continue;
                    this.createRotationNodesAndEdges(missionNode);
                }
            }
        }
        for (TopologyEdge e : edgesToExpand) {
            if (!PropertyUtil.isAllowed(e, this.config, this.reg)) continue;
            this.expand(n, n.getGoalOrientation(), e, new OrientationSet(FleetUtil.collectGoalOrientations(e, n.getGoalOrientation(), this.config)));
        }
    }

    private void checkAndAddGoalNode(BaseMissionNode cur) {
        if (cur.isStart() || cur.isRecoveredStartNode() || cur.getOriginalNode() != this.topGoal || this.goals.contains(cur)) {
            return;
        }
        for (BaseMissionNode goal : this.goals) {
            if (!this.isGoal(goal, cur)) continue;
            this.goals.add(cur);
            break;
        }
    }

    private boolean isGoal(BaseMissionNode goal, BaseMissionNode cur) {
        if (!Double.isNaN(goal.getPose().getTheta()) && !goal.getPose().approxEqualsMax(cur.getPose())) {
            return false;
        }
        if (goal.getPayloadOri() != null && !Double.isNaN(goal.getPayloadOri()) && NavUtil.angularDifference(goal.getPayloadOri(), cur.getPayloadOri()) > this.cfg.getTurningTableTrackingAccuracyRadian() + 1.0E-11) {
            return false;
        }
        boolean allowedLastBlending = BeanHolder.getBean(GraphPlannerConfig.class).isAllowedLastBlending();
        if (cur.isBlending()) {
            return allowedLastBlending;
        }
        return true;
    }

    private double getResolution(TopologyNode n) {
        return TopologyHelper.isTmpNode(n.getTags()) ? OrientationSet.RESOLUTION + Math.toRadians(1.0) : OrientationSet.RESOLUTION;
    }

    private void expandFromBlendingNode(BaseMissionNode node) {
        assert (node.isBlending());
        this.checkAndAddGoalNode(node);
        TopologyNode blendedNode = node.getOriginalNode();
        OrientationSet orisAtBlendedNode = this.createOrientationSet();
        if (PropertyUtil.isRotationAllowed(blendedNode, this.config)) {
            orisAtBlendedNode.addAll((Collection<? extends Double>)FleetUtil.collectAllowedOrientations(blendedNode, this.topoGraph, this.config));
        }
        for (TopologyEdge e : this.topoGraph.outgoingEdges(blendedNode)) {
            if (!PropertyUtil.isAllowed(e, this.config, this.reg) || !PropertyUtil.isBlendingToAllowed(e, this.config)) continue;
            OrientationSet srcOris = this.getAllowedStartOris(node, e, orisAtBlendedNode);
            for (double srcOri : srcOris) {
                OrientationSet tgtOris = this.createOrientationSet();
                tgtOris.addAll((Collection<? extends Double>)FleetUtil.collectGoalOrientations(e, srcOri, this.config));
                this.expand(node, srcOri, e, tgtOris);
            }
        }
    }

    private OrientationSet getAllowedStartOris(BaseMissionNode n, TopologyEdge toEdge, OrientationSet orisAtBlendedNode) {
        OrientationSet srcOris = this.createOrientationSet();
        if (this.config.getDriveType() == DriveType.HOLONOMIC) {
            srcOris.add(n.getGoalOrientation());
            if (!PropertyUtil.getMotion(toEdge, this.config).isRotationAllowed()) {
                srcOris.addAll(orisAtBlendedNode);
            }
            return srcOris;
        }
        TopologyEdge fromEdge = n.getFromEdge();
        double fromAngle = NavUtil.getLineAngle(((TopologyNode)fromEdge.getSource()).getPosition(), ((TopologyNode)fromEdge.getTarget()).getPosition());
        double relAngle = OrientationSet.getOri(NavUtil.normalizeAngle(n.getGoalOrientation() - fromAngle));
        double toAngle = NavUtil.getLineAngle(((TopologyNode)toEdge.getSource()).getPosition(), ((TopologyNode)toEdge.getTarget()).getPosition());
        OrientationSet candidateOris = this.createOrientationSet();
        candidateOris.add(n.getGoalOrientation());
        candidateOris.addAll(orisAtBlendedNode);
        for (double candidate : candidateOris) {
            if (relAngle != OrientationSet.getOri(NavUtil.normalizeAngle(candidate - toAngle))) continue;
            srcOris.add(candidate);
            return srcOris;
        }
        return srcOris;
    }

    private void expand(BaseMissionNode n, double startOri, TopologyEdge e, OrientationSet goalOris) {
        for (double ori : goalOris) {
            BaseMissionNode missionNode;
            if (this.config.getPayloadDiff() == null) {
                missionNode = this.createTopologyMissionNode(ori, (TopologyNode)e.getTarget(), null, null);
            } else {
                double threshold = BeanHolder.getBean(MissionGraphConfig.class).getTurningTableTrackingAccuracyRadian();
                Tuple2D payloadDiff = n.getPayloadDiff().withTheta(OrientationUtils.orthogonalizeWithThreshold(n.getPayloadDiff().getTheta(), threshold));
                Double payloadTgtOri = OrientationSet.getOri(ori + payloadDiff.getTheta());
                missionNode = this.createTopologyMissionNode(ori, (TopologyNode)e.getTarget(), payloadTgtOri, payloadDiff);
            }
            this.connectMissionNodes(n, e, missionNode);
        }
        if (this.isBlendingFromAllowed(e, n)) {
            goalOris.forEach(goalOri -> this.connectMissionNodes(n, e, this.getBlendingNode(startOri, e, (double)goalOri, n.getPose().getTheta(), n.getPayloadOri(), n.getPayloadDiff())));
        }
    }

    private boolean isBlendingFromAllowed(TopologyEdge e, BaseMissionNode n) {
        if (!this.config.getRobotType().isBlendingPermit()) {
            return false;
        }
        if (!PropertyUtil.isBlendingFromAllowed(e, this.config, this.reg)) {
            return false;
        }
        if (!this.neighbors(this.start).contains(n)) {
            return true;
        }
        if (!TopologyHelper.isTmpNode(this.start.getOriginalNode().getTags())) {
            return true;
        }
        return this.start.getPose().toPosition().distance(((TopologyNode)e.getTarget()).getPosition()) >= PropertyUtil.getBlendingCart(e, this.config);
    }

    private BaseMissionNode getBlendingNode(double startOri, TopologyEdge e, double goalOri, double robotStartOri, Double payloadStartOri, Tuple2D payloadDiff) {
        BlendingKey key = BlendingKey.of(startOri, e, goalOri);
        Double payloadTargetOri = payloadStartOri != null ? Double.valueOf(OrientationSet.getOri(NavUtil.normalizeAngle(goalOri - robotStartOri + payloadStartOri))) : null;
        return this.blendingNodes.computeIfAbsent(key, k -> {
            Pose blendingPose = FleetUtil.getBlendingStartPose(startOri, e, goalOri, this.config);
            BlendingNode blendingNode = new BlendingNode(startOri, e, blendingPose, goalOri, payloadTargetOri, payloadDiff);
            this.insert(blendingNode);
            return blendingNode;
        });
    }

    private void connectMissionNodes(BaseMissionNode s, TopologyEdge e, BaseMissionNode t) {
        Pose startPose;
        BlendingDiscretizationInfo blendingInfo;
        String robotTypeCode = this.config.getRobotTypeCode();
        double time = 0.001;
        if (s.isTopology() && s.isRecoveredStartNode()) {
            time += 0.001 + FleetUtil.estimateDuration(e, this.config, this.start.getPose(), s.getPose());
        }
        time += s.isTopology() ? PropertyUtil.getNodeCost((TopologyNode)e.getSource(), this.config.getRobotTypeCode(), this.reg) : 0.0;
        time += t.isTopology() ? PropertyUtil.getNodeCost((TopologyNode)e.getTarget(), this.config.getRobotTypeCode(), this.reg) : 0.0;
        if (s.isBlending() && this.config.getRobotType().isBlendingPermit()) {
            blendingInfo = FleetUtil.getBlendingPoses(s.getOrientation(), s.getFromEdge(), s.getGoalOrientation(), e, t.getGoalOrientation(), this.config);
            assert (!blendingInfo.getPoses().isEmpty());
            List<Pair<Pose, Double>> blendingPoses = blendingInfo.getPoses();
            if (blendingInfo.getRadius() < PropertyUtil.getEffectiveBlendingCart(s.getFromEdge(), this.config)) {
                time += FleetUtil.estimateDuration(s.getFromEdge(), this.config, s.getPose(), (Pose)blendingPoses.get(0).getA());
            }
            startPose = (Pose)blendingPoses.get(blendingPoses.size() - 1).getA();
            time += FleetUtil.estimateDurationBlendingSegment(e, blendingPoses, 0.001, this.config);
            time *= 0.9999;
        } else {
            startPose = s.getPose();
            blendingInfo = null;
        }
        BezierCurveDiscretizationInfo bezierCurveDiscretizationInfo = this.config.getRobotType().isCurvePermit() ? TopologyGraphBezierCurveSampleUtils.getBezierPoseSamples(e) : null;
        time += FleetUtil.estimateDuration(e, this.config, startPose, t.getPose());
        if (this.accelerationTimeCalculator != null && !FleetUtil.graphChange(e)) {
            double accTime = Math.max(0.0, this.accelerationTimeCalculator.getAdditionalAccelerationTime(s, t));
            if (this.config.getDriveType() == DriveType.HOLONOMIC) {
                accTime = Math.min(0.001, accTime);
            }
            time += accTime;
        }
        if (Double.isFinite(time)) {
            Supplier<List<Tuple2D>> getMotionAdapter = () -> {
                if (FleetUtil.graphChange(e)) {
                    return this.getMotionTransformationBetweenMaps(s, e, t);
                }
                if (blendingInfo != null) {
                    return this.getMotionTransformationForBlending(s, e, t, blendingInfo);
                }
                if (bezierCurveDiscretizationInfo != null) {
                    return this.getMotionTansformationsForBezier(s, e, t, bezierCurveDiscretizationInfo);
                }
                return this.getMotionTransformations(s, e, t);
            };
            CollisionInformation collisionInfo = this.getCollisionInfo(s, e, t, getMotionAdapter);
            this.addCollisionInformation(s, t, collisionInfo);
            int numInterNodes = this.computeNumInterNodes(time);
            double cost = time / (double)(numInterNodes + 1);
            this.createEdges(s, t, numInterNodes, cost);
            if (this.supportAdditionalCost(s, e, t)) {
                for (IMissionCostFunction.CostAccessor costAccessor : this.costFunctionForAstar.getCostAccessorList()) {
                    costAccessor.setCost(s, e, t, time);
                }
            }
        } else {
            LOG.warn("The cost for moving from source:{} to target:{} is Infinity or NaN.", (Object)s.getPose(), (Object)t.getPose());
        }
    }

    private boolean supportAdditionalCost(BaseMissionNode s, TopologyEdge e, BaseMissionNode t) {
        return !this.isWaitingNodeOrWaitingZoneNodeWithWaitNode(s) && !this.isWaitingNodeOrWaitingZoneNodeWithWaitNode(t) && !FleetUtil.graphChange(e);
    }

    private boolean isWaitingNodeOrWaitingZoneNodeWithWaitNode(BaseMissionNode node) {
        TopologyNode originalNode = node.getOriginalNode();
        boolean isWaitingZoneNodeWithWaitingNode = PropertyUtil.hasWaitingZone(originalNode) && PropertyUtil.getWaitingZone(originalNode).getWaitingNodes().size() != 0;
        boolean isWaitingNode = NodeType.QUEUING_POINT.equals((Object)PropertyUtil.getNodeType(originalNode));
        return isWaitingZoneNodeWithWaitingNode || isWaitingNode;
    }

    private CollisionInformation getCollisionInfo(BaseMissionNode src, TopologyEdge e, BaseMissionNode tgt, Supplier<List<Tuple2D>> motionsSupplier) {
        Pose startPose = this.getColliStartPose(src, tgt);
        List<Tuple2D> motions = Collections.unmodifiableList(motionsSupplier.get());
        CollisionInformation moveInfo = CollisionInformationFactory.createMoveInfo(startPose, motions, src.getPayloadDiff(), this.config, e);
        if (src.isTopology() && src.isRecoveredStartNode() && this.config.getDriveType() == DriveType.DIFFERENTIAL) {
            return this.config.isTuggingPlanning() ? this.convertToTuggingEntireInfo(moveInfo, e) : this.convertToEntireInfo(moveInfo, e);
        }
        return moveInfo;
    }

    private CollisionInformation convertToEntireInfo(CollisionInformation info, TopologyEdge e) {
        Position startPose = this.start.getPose().toPosition();
        double distance = this.getDistance(e, startPose);
        double maxRadius = info.getUnionShape().getRadiusPolygonalPart();
        double radius = distance + maxRadius;
        PlanningShape entireShape = PlanningShape.fromRadius(radius, true);
        return CollisionInformationFactory.toEntireInfo(info, info.getStart(), info.getMotions(), entireShape);
    }

    private CollisionInformation convertToTuggingEntireInfo(CollisionInformation info, TopologyEdge e) {
        Position startPose = this.start.getPose().toPosition();
        double distance = this.getDistance(e, startPose);
        double extraPadding = CollisionInformationFactory.getTuggingPadding(this.config);
        Polygon g = new GeometryFactory().createPolygon(info.getRobotShape().getConvexHull());
        double paddingSum = this.config.isTuggingSoftLink() ? extraPadding + distance : distance;
        g = JtsUtils.enlarge((Geometry)g, 0.0, 0.0, paddingSum, paddingSum);
        double r = info.getRobotShape().getRadiusPolygonalPart();
        PlanningShape entireShape = PlanningShape.fromGeometry((Geometry)g, false, r);
        return CollisionInformationFactory.toEntireInfo(info, info.getStart(), info.getMotions(), entireShape);
    }

    private double getDistance(TopologyEdge e, Position actualPose) {
        EdgeBezierCurveProperty bezierCurveProperty = PropertyUtil.getBezierCurveProperty(e);
        if (bezierCurveProperty != null) {
            Position pointOnEdge = BezierCurveTopologyGraphUtil.getBezierPosition(e, bezierCurveProperty, this.start.getPose().toPosition());
            double distanceToSourceNode = actualPose.distance(((TopologyNode)e.getSource()).getPosition());
            double distanceToTargetNode = actualPose.distance(((TopologyNode)e.getTarget()).getPosition());
            double distanceToEdge = actualPose.distance(pointOnEdge);
            return distanceToSourceNode <= distanceToTargetNode ? Math.min(distanceToSourceNode, distanceToEdge) : Math.min(distanceToTargetNode, distanceToEdge);
        }
        return NavUtil.distance(actualPose, ((TopologyNode)e.getSource()).getPosition(), ((TopologyNode)e.getTarget()).getPosition());
    }

    private Pose getColliStartPose(BaseMissionNode src, BaseMissionNode tgt) {
        TopologyEdge edge = (TopologyEdge)this.topoGraph.getEdge(src.getOriginalNode(), tgt.getOriginalNode());
        if (PropertyUtil.hasBezierCurveProperty(edge)) {
            return new Pose(src.getOriginalNode().getPosition().getX(), src.getOriginalNode().getPosition().getY(), src.getPose().getTheta());
        }
        if (!src.isTopology() || !src.isRecoveredStartNode()) {
            return src.getPose();
        }
        if (this.config.getDriveType() == DriveType.HOLONOMIC) {
            return this.start.getPose();
        }
        return src.getPose().withTheta(tgt.getGoalOrientation());
    }

    private List<Tuple2D> getMotionTransformations(BaseMissionNode s, TopologyEdge e, BaseMissionNode t) {
        ArrayList<Tuple2D> motionTransformations = new ArrayList<Tuple2D>();
        EdgeMotion mtn = PropertyUtil.getMotion(e, this.config);
        if (s.isRecoveredStartNode()) {
            if (this.config.getDriveType() == DriveType.DIFFERENTIAL) {
                Pose startPose = s.getPose().withTheta(t.getGoalOrientation());
                motionTransformations.addAll(mtn.getMotions(startPose, t.getPose()));
                return motionTransformations;
            }
            motionTransformations.add(CollisionUtil.transformation(this.start.getPose(), s.getPose()));
        }
        motionTransformations.addAll(mtn.getMotions(s.getPose(), t.getPose()));
        return motionTransformations;
    }

    private Pose projectionOnLine(BaseMissionNode s, TopologyEdge e, double goalOrientation) {
        Position actualPose = s.getPose().toPosition();
        Position source = ((TopologyNode)e.getSource()).getPosition();
        Position target = ((TopologyNode)e.getTarget()).getPosition();
        Pose projection = new Pose();
        double xa = actualPose.getX();
        double xs = source.getX();
        double xt = target.getX();
        double ya = actualPose.getY();
        double ys = source.getY();
        double yt = target.getY();
        double a = yt - ys;
        double b = xs - xt;
        double c = xt * ys - xs * yt;
        if (a == 0.0) {
            return projection.withX(xa).withY(ys).withTheta(goalOrientation);
        }
        if (b == 0.0) {
            return projection.withX(xs).withY(ya).withTheta(goalOrientation);
        }
        double xp = (b * (-b * xa + a * ya) + a * c) / (-Math.pow(a, 2.0) - Math.pow(b, 2.0));
        double yp = (b * c - a * (-b * xa + a * ya)) / (-Math.pow(a, 2.0) - Math.pow(b, 2.0));
        return projection.withX(xp).withY(yp).withTheta(goalOrientation);
    }

    private Pose projectionOnLine2(BaseMissionNode s, TopologyEdge e, double goalOrientation) {
        Position src = ((TopologyNode)e.getSource()).getPosition();
        Position tgt = ((TopologyNode)e.getTarget()).getPosition();
        double t = NavUtil.locationOnEdge(s.getPose().toPosition(), src, tgt);
        t = Math.max(0.0, Math.min(1.0, t));
        return new Pose(src.getX() * (1.0 - t) + tgt.getX() * t, src.getY() * (1.0 - t) + tgt.getY() * t, goalOrientation);
    }

    private List<Tuple2D> getMotionTansformationsForBezier(BaseMissionNode s, TopologyEdge e, BaseMissionNode t, BezierCurveDiscretizationInfo info) {
        ArrayList<Tuple2D> motionTrafos = new ArrayList<Tuple2D>();
        EdgeMotion mtn = PropertyUtil.getMotion(e, this.config);
        if (this.config.getDriveType() == DriveType.HOLONOMIC && mtn.getMode() == OrientationMode.RELATIVE_TO_GRAPH) {
            double robotOri = s.getOrientation();
            for (int i = 1; i < info.getPoses().size(); ++i) {
                Pose currentPose = ((Pose)info.getPoses().get(i - 1).getA()).withTheta(robotOri);
                Pose nextPose = ((Pose)info.getPoses().get(i).getA()).withTheta(robotOri);
                motionTrafos.addAll(mtn.getMotions(currentPose, nextPose));
            }
        } else {
            motionTrafos.addAll(TopologyGraphBezierCurveSampleUtils.getBezierMotions(info, s.getOrientation(), mtn));
        }
        return motionTrafos;
    }

    private List<Tuple2D> getMotionTransformationForBlending(BaseMissionNode s, TopologyEdge e, BaseMissionNode t, BlendingDiscretizationInfo info) {
        ArrayList<Tuple2D> motionTransformations = new ArrayList<Tuple2D>();
        EdgeMotion mtn = PropertyUtil.getMotion(e, this.config);
        assert (info != null);
        if (info.getRadius() < PropertyUtil.getEffectiveBlendingCart(s.getFromEdge(), this.config)) {
            motionTransformations.add(CollisionUtil.transformation(s.getPose(), (Tuple2D)info.getPoses().get(0).getA()));
        }
        motionTransformations.addAll(FleetUtil.getMotions(mtn, info.getPoses(), t.getPose()));
        return motionTransformations;
    }

    private List<Tuple2D> getMotionTransformationBetweenMaps(BaseMissionNode s, TopologyEdge e, BaseMissionNode t) {
        double rotation = PropertyUtil.getGraphTransition(e, this.config.getRobotTypeCode()).getRotation();
        return Collections.singletonList(new Tuple2D(0.0, 0.0, NavUtil.normalizeAngle(t.getOrientation() - (s.getOrientation() + rotation))));
    }

    public CollisionInformation getCollisionInfo(BaseMissionNode src, BaseMissionNode tgt) {
        BaseMissionNode realSrc = src.getPredecessor();
        if (src == tgt) {
            return CollisionInformationFactory.createNoMotionInfo(realSrc, this.config);
        }
        BaseMissionNode realTgt = tgt.getSuccessor();
        CollisionInformation colliInfo = this.collisionInfo.get(Pair.create((Object)realSrc, (Object)realTgt));
        assert (colliInfo != null) : "Edge (" + realSrc + "," + realTgt + ") is not contained in the graph.";
        return colliInfo;
    }

    private void addCollisionInformation(BaseMissionNode src, BaseMissionNode tgt, CollisionInformation colliInfo) {
        Pair edge = Pair.create((Object)src, (Object)tgt);
        assert (!this.collisionInfo.containsKey(edge));
        this.collisionInfo.put((Pair<BaseMissionNode, BaseMissionNode>)edge, colliInfo);
    }

    public AgentConfig getAgentConfig() {
        return this.config;
    }

    public BaseMissionNode getStart() {
        assert (this.start != null) : "A mission graph's start node must have been created before accessing it.";
        return this.start;
    }

    private void insert(BaseMissionNode n) {
        boolean isCreatedEdge;
        if (n.isTopology() && !n.isRecoveredStartNode()) {
            this.getTopToMissionMapping(n.getOriginalNode()).put((Pair<Double, Double>)new Pair((Object)n.getOrientation(), (Object)n.getPayloadOri()), n);
        }
        boolean bl = isCreatedEdge = super.addNode(n) && this.cfg.getDeltaT() > 0.0 && !n.isIntermediate() && (!n.isTopology() || !n.isRecoveredStartNode());
        if (isCreatedEdge) {
            this.createEdge(n, n, 0.001);
        }
    }

    @Override
    public String toString() {
        int length = 2;
        StringBuilder sb = new StringBuilder("({");
        for (BaseMissionNode n : this.nodes()) {
            sb.append(n).append(", ");
        }
        if (!this.nodes().isEmpty()) {
            sb.delete(sb.length() - length, sb.length());
        }
        sb.append("},\n{");
        for (MissionEdge e : this.edges()) {
            sb.append("(").append(e.getSource()).append(", ").append(e.getTarget()).append("), ");
        }
        if (!this.nodes().isEmpty()) {
            sb.delete(sb.length() - length, sb.length());
        }
        sb.append("})");
        return sb.toString();
    }

    private void createRotationNodesAndEdges(BaseMissionNode missionNode) {
        TopologyNode topNode = missionNode.getOriginalNode();
        NodeRotationType nodeRotationType = PropertyUtil.getNodeRotationType(topNode, this.config);
        if (PropertyUtil.isRotationAllowed(topNode, this.config)) {
            boolean nodeSetRelativeRotation;
            boolean robotAllowRelativeRotation = this.config.getRobotType().isRelativeRotationPermit();
            boolean bl = nodeSetRelativeRotation = nodeRotationType == NodeRotationType.RELATIVE_ROTATION;
            if (this.config.hasPayload() && nodeSetRelativeRotation && !robotAllowRelativeRotation) {
                return;
            }
            OrientationSet orientations = this.createOrientationSet();
            orientations.addAll((Collection<? extends Double>)FleetUtil.collectAllowedOrientations(topNode, this.topoGraph, this.config));
            if (missionNode.getOriginalNode() == this.topGoal && !this.goals.contains(missionNode)) {
                orientations.addAll((Collection<? extends Double>)this.collectPossibleGoalOri(this.goals, missionNode));
            }
            this.createRotationNodes(orientations, missionNode);
            this.createPossibleGoalMissionNode(missionNode);
            Set missionNodes = this.getTopToMissionMapping(topNode).values().stream().filter(v -> this.isConnectableNode(missionNode, (BaseMissionNode)v, false)).collect(Collectors.toSet());
            this.missionNodesRotationHandled.addAll(missionNodes);
            this.createRotationEdges(new ArrayList<BaseMissionNode>(missionNodes));
        }
    }

    private void createPossibleGoalMissionNode(BaseMissionNode missionNode) {
        TopologyNode topNode = missionNode.getOriginalNode();
        if (!this.config.hasPayload() || topNode != this.topGoal) {
            return;
        }
        Set<BaseMissionNode> missionNodes = this.getTopToMissionMapping(topNode).values().stream().filter(v -> this.isConnectableNode(missionNode, (BaseMissionNode)v, true)).collect(Collectors.toSet());
        missionNodes.forEach(m -> {
            if (Double.isNaN(m.getPayloadOri())) {
                if (PropertyUtil.isRelativeRotation(topNode, this.config)) {
                    this.createTopologyMissionNode(m.getOrientation(), topNode, missionNode.getPayloadOri(), missionNode.getPayloadDiff().withTheta(NavUtil.normalizeAngle(missionNode.getPayloadOri() - m.getOrientation())));
                } else if (PropertyUtil.isAbsoluteRotation(topNode, this.config)) {
                    double payloadOri = OrientationSet.getOri(NavUtil.normalizeAngle(m.getOrientation() + missionNode.getPayloadDiff().getTheta()));
                    this.createTopologyMissionNode(m.getOrientation(), topNode, payloadOri, missionNode.getPayloadDiff());
                }
            }
        });
    }

    List<Double> collectPossibleGoalOri(Set<BaseMissionNode> goals, BaseMissionNode current) {
        return goals.stream().map(goal -> this.getPossibleGoalOri((BaseMissionNode)goal, current)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    Double getPossibleGoalOri(BaseMissionNode goal, BaseMissionNode current) {
        if (Double.isNaN(goal.getOrientation()) && goal.getPayloadDiff() != null && !Double.isNaN(goal.getPayloadOri()) && PropertyUtil.isAbsoluteRotation(current.getOriginalNode(), this.config) && !current.getPayloadOri().equals(goal.getPayloadOri())) {
            double payloadRotateOri = NavUtil.normalizeAngle(goal.getPayloadOri() - current.getPayloadOri());
            return NavUtil.normalizeAngle(current.getPose().getTheta() + payloadRotateOri);
        }
        return null;
    }

    boolean isConnectableNode(BaseMissionNode src, BaseMissionNode tgt, boolean considerUncertainContainerOri) {
        TopologyNode topNode = src.getOriginalNode();
        if (this.config.hasPayload()) {
            if (Double.isNaN(tgt.getPose().getTheta())) {
                return false;
            }
            boolean isRelativeConnected = PropertyUtil.isRelativeRotation(topNode, this.config) && NavUtil.angularDifference(tgt.getPayloadOri(), src.getPayloadOri()) < 1.0E-11;
            boolean isAbsoluteConnected = PropertyUtil.isAbsoluteRotation(topNode, this.config) && NavUtil.angularDifference(NavUtil.normalizeAngle(tgt.getPose().getTheta() - src.getPose().getTheta()), NavUtil.normalizeAngle(tgt.getPayloadOri() - src.getPayloadOri())) < 1.0E-11;
            return considerUncertainContainerOri && Double.isNaN(tgt.getPayloadOri()) || isRelativeConnected || isAbsoluteConnected;
        }
        return !Double.isNaN(tgt.getPose().getTheta());
    }

    private void createRotationEdges(List<BaseMissionNode> nodes) {
        if (nodes.size() <= 1) {
            return;
        }
        nodes.sort(ORIENTATION_COMP);
        int idx = IterableUtils.indexOf(nodes, n -> n.getPayloadDiff() != null);
        if (idx > 0) {
            Collections.rotate(nodes, -idx);
        }
        for (int i = 0; i < nodes.size(); ++i) {
            for (int j = i + 1; j < nodes.size(); ++j) {
                this.connectRotationNodes(nodes.get(i), nodes.get(j));
            }
        }
    }

    private void connectRotationNodes(BaseMissionNode n1, BaseMissionNode n2) {
        this.createRotationEdges(n1, n2);
        this.createRotationEdges(n2, n1);
    }

    private void createRotationEdges(BaseMissionNode s, BaseMissionNode t) {
        RotateType rotateType;
        Pair edge = Pair.create((Object)s, (Object)t);
        if (this.collisionInfo.containsKey(edge)) {
            return;
        }
        TopologyNode n = s.getOriginalNode();
        double sourceOri = s.getOrientation();
        double targetOri = t.getOrientation();
        double maxVelocityRot = PropertyUtil.getMaxVelocityRot(n, this.config);
        double rotTime = FleetUtil.rotationTime(sourceOri, targetOri, maxVelocityRot);
        if (this.accelerationTimeCalculator != null) {
            double accTime = Math.max(0.0, this.accelerationTimeCalculator.getAdditionalAccelerationTime(s, t));
            if (this.config.getDriveType() == DriveType.HOLONOMIC) {
                accTime = Math.min(0.001, accTime);
            }
            rotTime += accTime;
        }
        int numInterNodes = this.computeNumInterNodes(rotTime);
        double rotTimPerInterEdge = rotTime / (double)(numInterNodes + 1) + 0.001;
        double rotationAngle = OrientationSet.getOri(NavUtil.normalizeAngle(targetOri - sourceOri));
        if (!this.config.hasPayload()) {
            rotateType = RotateType.BOTH;
        } else if (PropertyUtil.isRelativeRotation(n, this.config)) {
            rotateType = RotateType.ONLY_ROBOT;
        } else if (PropertyUtil.isAbsoluteRotation(n, this.config)) {
            rotateType = RotateType.BOTH;
        } else {
            LOG.error("can not create edge for {} -> {}", (Object)s, (Object)t);
            return;
        }
        this.createEdges(s, t, numInterNodes, rotTimPerInterEdge);
        this.addCollisionInformation(s, t, CollisionInformationFactory.createRotationInfo(s.getPose(), s.getPayloadDiff(), rotateType, rotationAngle, this.config, s.getOriginalNode()));
    }

    private void createEdges(BaseMissionNode s, BaseMissionNode t, int numInterNodes, double cost) {
        BaseMissionNode lastNode = s;
        for (int i = 0; i < numInterNodes; ++i) {
            IntermediateMissionNode nextInterNode = new IntermediateMissionNode(t, (double)(numInterNodes - i) * cost, s, (double)(i + 1) * cost, t.getGraphId());
            this.insert(nextInterNode);
            this.createEdge(lastNode, nextInterNode, cost);
            lastNode = nextInterNode;
        }
        this.createEdge(lastNode, t, cost);
    }

    public Set<BaseMissionNode> createTopologyMissionNodeForStartAndGoal(TopologyNode topNode, List<Double> robotOris, List<Double> payloadOris, DispatchedGraphMotion mtn) {
        List<Double> robotOriList;
        HashSet<BaseMissionNode> missionNodes = new HashSet<BaseMissionNode>();
        List<Double> payloadOirList = CollectionUtils.isEmpty(payloadOris) ? Collections.singletonList(Double.NaN) : payloadOris;
        List<Double> list = robotOriList = CollectionUtils.isEmpty(robotOris) ? Collections.singletonList(Double.NaN) : robotOris.stream().map(OrientationSet::getOri).collect(Collectors.toList());
        if (!this.config.hasPayload()) {
            robotOriList.forEach(robotOri -> missionNodes.add(this.createTopologyMissionNode((Double)robotOri, topNode, null, null)));
        } else {
            for (Double robotOri2 : robotOriList) {
                for (Double payloadOri : payloadOirList) {
                    missionNodes.add(this.createTopologyMissionNode(robotOri2, topNode, payloadOri, mtn.getAgentConfig().getPayloadDiff().withTheta(NavUtil.normalizeAngle(payloadOri - robotOri2))));
                }
            }
        }
        return robotOris.isEmpty() && payloadOris.isEmpty() ? new HashSet<BaseMissionNode>(this.getTopToMissionMapping(topNode).values()) : missionNodes;
    }

    public Set<BaseMissionNode> createBlendingGoals(TopologyNode goal, Double orientation, DispatchedGraphMotion mtn, Double payloadOri) {
        HashSet<BaseMissionNode> goals = new HashSet<BaseMissionNode>();
        Set<TopologyEdge> outEdges = this.topoGraph.outgoingEdges(goal);
        for (TopologyEdge e : this.topoGraph.incomingEdges(goal)) {
            if (!PropertyUtil.isBlendingFromAllowed(e, this.config, this.reg) || mtn.getPhase() != DispatchedGraphMotion.Phase.SECOND_BLEND_GOAL && !FleetUtil.checkBlendableEdgeExists(outEdges, (TopologyNode)e.getSource(), this.config)) continue;
            OrientationSet srcOris = this.createOrientationSet();
            srcOris.addAll((Collection<? extends Double>)FleetUtil.collectAllowedOrientations((TopologyNode)e.getSource(), this.topoGraph, this.config));
            for (double srcOri : srcOris) {
                OrientationSet goalOris = this.createOrientationSet();
                goalOris.addAll((Collection<? extends Double>)FleetUtil.collectGoalOrientations(e, srcOri, this.config));
                if (orientation != null) {
                    goalOris.retainAll(Collections.singleton(orientation));
                }
                for (double goalOri : goalOris) {
                    Tuple2D payloadDiff = payloadOri != null ? mtn.getAgentConfig().getPayloadDiff().withTheta(NavUtil.normalizeAngle(payloadOri - goalOri)) : null;
                    Double payloadStartOri = payloadOri != null ? Double.valueOf(OrientationSet.getOri(NavUtil.normalizeAngle(payloadOri - (goalOri - srcOri)))) : null;
                    goals.add(this.getBlendingNode(srcOri, e, goalOri, srcOri, payloadStartOri, payloadDiff));
                }
            }
        }
        return goals;
    }

    public boolean createStart(DispatchedGraphMotion mtn, TopologyNode topNode, Pose actualStart, Tuple2D payloadDiff) {
        double roundedOri = OrientationSet.getOri(actualStart.getTheta());
        Double payloadOri = null;
        if (payloadDiff != null) {
            payloadOri = OrientationSet.getOri(NavUtil.normalizeAngle(actualStart.getTheta() + payloadDiff.getTheta()));
        }
        this.start = new StartMissionNode(topNode, actualStart.withTheta(roundedOri), payloadDiff, payloadOri);
        this.insert(this.start);
        double dist = topNode.getPosition().distance(actualStart.toPosition());
        if (dist <= this.cfg.getTransTrackingAccuracy()) {
            if (mtn.getPayloadRotationActionAngle() != 0.0) {
                payloadOri = OrientationSet.getOri(NavUtil.normalizeAngle(this.start.getPose().getTheta() + this.start.getPayloadDiff().getTheta() + mtn.getPayloadRotationActionAngle()));
            }
            this.createTopologyMissionNodeForStartAndGoal(topNode, Collections.singletonList(this.start.getOrientation()), Collections.singletonList(payloadOri), mtn);
            Collection<BaseMissionNode> startMissionNodes = this.getTopToMissionMapping(topNode).values();
            if (1 != startMissionNodes.size()) {
                throw new PlannerException("size of startMissionNodes should be 1. size:" + startMissionNodes.size());
            }
            this.connectStartMissionNode(mtn.getPayloadRotationActionAngle(), startMissionNodes.iterator().next());
            return false;
        }
        LOG.debug("Not connecting start node ({}) to unrecovered node {} for dist: {} > default tracking accuracy: {}.", new Object[]{this.start, topNode, dist, this.cfg.getTransTrackingAccuracy()});
        return true;
    }

    @Deprecated
    private double getAlignedOri(BaseMissionNode node, double actualOri) {
        double ori = node.getOrientation();
        double rotAcc = this.cfg.getRotatoryTrackingAccuracyRadian();
        if (this.topoGraph.outgoingEdges(node.getOriginalNode()).size() != 0) {
            for (TopologyEdge edge : this.topoGraph.outgoingEdges(node.getOriginalNode())) {
                double edgeAngle = FleetProviderUtil.getEdgeStartAngle(edge);
                if (NavUtil.angularDifference(actualOri, edgeAngle) - rotAcc <= 1.0E-11) {
                    return edgeAngle;
                }
                if (!(NavUtil.angularDifference(actualOri, NavUtil.normalizeAngle(edgeAngle + Math.PI)) - rotAcc <= 1.0E-11)) continue;
                return NavUtil.normalizeAngle(edgeAngle + Math.PI);
            }
        }
        return ori;
    }

    public void createRecoveredNodes(TopologyNode topNode, List<TopologyEdge> blockedEdgeList) {
        if (!TopologyHelper.isTmpNode(topNode.getTags())) {
            this.handleNodeRecovery(topNode);
        } else {
            for (TopologyEdge e : TopologyHelper.tryGetEdgesForTmpNode(topNode.getTags())) {
                boolean availableSource = true;
                boolean availableTarget = true;
                boolean avaliableEdge = true;
                if (CollectionUtils.isNotEmpty(blockedEdgeList)) {
                    for (TopologyEdge edge : blockedEdgeList) {
                        if (((TopologyNode)edge.getTarget()).equals(e.getSource())) {
                            availableSource = false;
                        }
                        if (((TopologyNode)edge.getTarget()).equals(e.getTarget())) {
                            availableTarget = false;
                        }
                        if (!edge.equals(e)) continue;
                        avaliableEdge = false;
                    }
                }
                if (availableSource) {
                    this.handleEdgeRecoveryNeighbor(topNode.getMapId(), (TopologyNode)e.getSource());
                }
                if (availableTarget) {
                    this.handleEdgeRecoveryNeighbor(topNode.getMapId(), (TopologyNode)e.getTarget());
                }
                if (!avaliableEdge) continue;
                this.handleRecoveryOnEdge(topNode, e);
            }
        }
    }

    private void handleEdgeRecoveryNeighbor(int startMapId, TopologyNode n) {
        if (startMapId == n.getMapId()) {
            this.handleNodeRecovery(n);
        }
    }

    private void handleRecoveryOnEdge(TopologyNode tmp, TopologyEdge e) {
        this.createRecoveredNodes((TopologyEdge)this.topoGraph.getEdge(tmp, e.getTarget()));
    }

    private void handleNodeRecovery(TopologyNode topNode) {
        this.topoGraph.outgoingEdges(topNode).forEach(this::createRecoveredNodes);
        this.topoGraph.incomingEdges(topNode).forEach(this::createRecoveredNodes);
    }

    private void createRecoveredNodes(TopologyEdge e) {
        if (PropertyUtil.isAllowed(e, this.config, this.reg) && PropertyUtil.isRecoverableMotion(e, this.config)) {
            TopologyNode src = (TopologyNode)e.getSource();
            OrientationSet allowedOris = new OrientationSet(FleetUtil.collectAllowedOrientations(src, this.topoGraph, this.config));
            double rotTol = PropertyUtil.getRotatoryToleranceForStartRecovery(src, this.config);
            double transTol = PropertyUtil.getTranslatoryToleranceForStartRecovery(src, this.config);
            double rotTolEdge = PropertyUtil.getRotatoryToleranceForEdgeRecovery(e, this.config);
            double transTolEdge = PropertyUtil.getTranslatoryToleranceForEdgeRecovery(e, this.config);
            Position intendedPosition = src.getPosition();
            Position intendedPositionOnEdge = RecoveryNodesAndEdgesUtil.getIntendedPositionOnEdge(e, this.config, this.start.getPose().toPosition());
            boolean rotationMotion = PropertyUtil.getMotion(e, this.config).isRotationAllowed();
            for (double ori : allowedOris) {
                if (this.config.isTuggingPlanning() && Math.abs(NavUtil.normalizeAngle(ori - this.start.getOrientation())) > Math.toRadians(this.cfg.getTuggerRecoverAngle())) continue;
                double recoverOri = RecoveryNodesAndEdgesUtil.getRecoverOri(e, rotationMotion, this.start, ori, this.config);
                double intendedOri = rotationMotion ? this.start.getOrientation() : ori;
                Pose recoverPoseOnEdge = intendedPositionOnEdge.toPose(recoverOri);
                Pose intendedPoseOnEdge = intendedPositionOnEdge.toPose(intendedOri);
                Pose intendedPose = intendedPosition.toPose(intendedOri);
                boolean crossTarget = intendedPositionOnEdge.distance(((TopologyNode)e.getTarget()).getPosition()) < 1.0E-11;
                OrientationSet goalOris = new OrientationSet(FleetUtil.collectGoalOrientations(e, ori, this.config));
                if (crossTarget || goalOris.isEmpty()) continue;
                if (this.checkRecovery(recoverPoseOnEdge, rotTolEdge, transTolEdge)) {
                    this.addRecoveredStart(e, intendedPoseOnEdge);
                    continue;
                }
                if (!this.checkRecovery(intendedPose, rotTol, transTol)) continue;
                this.addRecoveredStart(e, intendedPose);
            }
        }
    }

    private void addRecoveredStart(TopologyEdge e, Pose intendedPoseOnEdge) {
        RecoveryMissionNode recoveredStart;
        Tuple2D payloadDiff = this.config.getPayloadDiff();
        Double payloadOri = !this.config.getRobotType().isRelativeRotationPermit() || PropertyUtil.hasBezierCurveProperty(e) || this.config.getRobotType().getRobotTypeClass().equals((Object)RobotTypeClass.FIXED_CARRY_CLASS) ? (payloadDiff != null ? Double.valueOf(OrientationSet.getOri(NavUtil.normalizeAngle(intendedPoseOnEdge.getTheta() + payloadDiff.getTheta()))) : null) : ((payloadDiff = this.calculateRecoverPayloadDiff(this.start.getPose().getTheta(), this.start.getPayloadOri(), this.start.getPayloadDiff())) != null ? Double.valueOf(NavUtil.normalizeAngle(intendedPoseOnEdge.getTheta() + payloadDiff.getTheta())) : null);
        RecoveryMissionNode recoveryMissionNode = recoveredStart = this.isAllowedCreating(intendedPoseOnEdge.getTheta(), payloadOri) ? new RecoveryMissionNode(e, intendedPoseOnEdge, payloadDiff, payloadOri) : null;
        if (recoveredStart != null && !this.containsNode(recoveredStart)) {
            this.insert(recoveredStart);
            this.connectStartMissionNode(0.0, recoveredStart);
            this.setRecovered(true);
        }
    }

    private Tuple2D calculateRecoverPayloadDiff(double startRobotOri, Double startPayloadOri, Tuple2D startPayloadDiff) {
        if (startPayloadDiff == null || ORTHOGONALITY_ANGLES.contains(startPayloadDiff.getTheta())) {
            return startPayloadDiff;
        }
        double resultDiffOri = 0.0;
        for (double candidatePayloadDiffOri : ORTHOGONALITY_ANGLES) {
            if (Math.abs(startPayloadDiff.getTheta() - candidatePayloadDiffOri) <= Math.toRadians(10.0)) {
                return startPayloadDiff;
            }
            if (Math.abs(resultDiffOri - candidatePayloadDiffOri) < 1.0E-11 || !(Math.abs(NavUtil.normalizeAngle(startRobotOri + candidatePayloadDiffOri - startPayloadOri)) < Math.abs(NavUtil.normalizeAngle(startRobotOri + resultDiffOri - startPayloadOri)))) continue;
            resultDiffOri = candidatePayloadDiffOri;
        }
        return new Tuple2D(startPayloadDiff.getX(), startPayloadDiff.getY(), resultDiffOri);
    }

    private boolean checkRecovery(Pose intended, double rotTol, double transTol) {
        return this.start.getPose().distanceTrans(intended) <= transTol && this.start.getPose().distanceRot(intended) <= rotTol - OrientationSet.RESOLUTION + 1.0E-11;
    }

    private void connectStartMissionNode(double payloadRotationActionAngle, BaseMissionNode neighbor) {
        this.createEdge(this.start, neighbor, neighbor.isRecoveredStartNode() ? this.cfg.getRecoveryCost() : 0.001);
        CollisionInformation collisionInformation = payloadRotationActionAngle == 0.0 ? CollisionInformationFactory.createNoMotionInfo(this.start, this.config) : CollisionInformationFactory.createTurntableRotationInfo(this.start.getPose(), this.config.getPayloadDiff(), payloadRotationActionAngle, this.config, this.start.getOriginalNode());
        this.addCollisionInformation(this.start, neighbor, collisionInformation);
    }

    public void createBlendingStart(double srcOri, TopologyEdge e, double tgtOri, Tuple2D payloadDiff) {
        LOG.debug("Creating blending start node on {} (source or: {}. target ori: {}).", new Object[]{e, srcOri, tgtOri});
        double payloadStartOri = payloadDiff == null ? Double.NaN : OrientationSet.getOri(NavUtil.normalizeAngle(srcOri + payloadDiff.getTheta()));
        double payloadTargetOri = OrientationSet.getOri(NavUtil.normalizeAngle(tgtOri - srcOri + payloadStartOri));
        BaseMissionNode n = this.getBlendingNode(srcOri, e, tgtOri, srcOri, payloadStartOri, payloadDiff);
        this.start = new StartMissionNode((TopologyNode)e.getTarget(), n.getPose(), payloadDiff, payloadTargetOri);
        this.insert(this.start);
        LOG.debug("Created start mission node {} for start from blending pose.", (Object)this.start);
        this.connectStartMissionNode(0.0, n);
    }

    private Set<BaseMissionNode> createRotationNodes(OrientationSet oris, BaseMissionNode missionNode) {
        HashSet<BaseMissionNode> missionNodes = new HashSet<BaseMissionNode>();
        TopologyNode n = missionNode.getOriginalNode();
        if (this.config.hasPayload()) {
            double robotOri = missionNode.getPose().getTheta();
            Double payloadOri = missionNode.getPayloadOri();
            if (PropertyUtil.isRelativeRotation(n, this.config)) {
                oris.forEach(ori -> missionNodes.add(this.createTopologyMissionNode((Double)ori, n, payloadOri, missionNode.getPayloadDiff().withTheta(NavUtil.normalizeAngle(payloadOri - ori)))));
            } else if (PropertyUtil.isAbsoluteRotation(n, this.config)) {
                oris.forEach(ori -> missionNodes.add(this.createTopologyMissionNode((Double)ori, n, OrientationSet.getOri(NavUtil.normalizeAngle(payloadOri + (ori - robotOri))), missionNode.getPayloadDiff())));
            }
        } else {
            oris.forEach(ori -> missionNodes.add(this.createTopologyMissionNode((Double)ori, n, null, null)));
        }
        return missionNodes;
    }

    private BaseMissionNode createTopologyMissionNode(Double ori, TopologyNode n, Double payloadOri, Tuple2D payloadDiff) {
        Map<Pair<Double, Double>, BaseMissionNode> existingMissionNodes = this.getTopToMissionMapping(n);
        if (!existingMissionNodes.containsKey(new Pair((Object)ori, (Object)payloadOri)) && this.isAllowedCreating(ori, payloadOri)) {
            this.insert(new TopologyMissionNode(n, n.getPosition().toPose(ori), payloadOri, payloadDiff));
        }
        return existingMissionNodes.get(new Pair((Object)ori, (Object)payloadOri));
    }

    private boolean isAllowedCreating(Double ori, Double payloadOri) {
        if (this.config.getAllowedOrientationRange() == null || payloadOri == null || Double.isNaN(payloadOri) || Double.isNaN(ori)) {
            return true;
        }
        double payloadDiffOri = NavUtil.normalizeAngle(payloadOri - ori);
        return payloadDiffOri >= this.config.getAllowedOrientationRange().getMinOri() - 1.0E-11 && payloadDiffOri <= this.config.getAllowedOrientationRange().getMaxOri() + 1.0E-11;
    }

    private void createEdge(BaseMissionNode source, BaseMissionNode target, double cost) {
        MissionEdge e = new MissionEdge(source, target);
        super.addEdge(e);
        this.incomingEdges.computeIfAbsent(target, k -> new HashSet()).add(e);
        this.costFunction.setCost(source, target, cost);
    }

    private int computeNumInterNodes(double time) {
        return this.cfg.getDeltaT() > 0.0 ? (int)Math.floor(time / this.cfg.getDeltaT()) : 0;
    }

    private Map<Pair<Double, Double>, BaseMissionNode> getTopToMissionMapping(TopologyNode topNode) {
        return this.topNodeToTopMissionNodes.computeIfAbsent(topNode, k -> new HashMap(16));
    }

    private OrientationSet createOrientationSet() {
        return new OrientationSet();
    }

    @Override
    public boolean addNode(BaseMissionNode node) {
        return false;
    }

    @Override
    public boolean addEdge(MissionEdge edge) {
        throw new UnsupportedOperationException("It is not allowed to actively add edges.");
    }

    public int getAgentId() {
        return this.agentId;
    }

    public TopologyGraph getTopoGraph() {
        return this.topoGraph;
    }

    public AgentConfig getConfig() {
        return this.config;
    }

    public CustomActionRegistry getReg() {
        return this.reg;
    }

    public Map<TopologyNode, Map<Pair<Double, Double>, BaseMissionNode>> getTopNodeToTopMissionNodes() {
        return this.topNodeToTopMissionNodes;
    }

    public Set<BaseMissionNode> getMissionNodesRotationHandled() {
        return this.missionNodesRotationHandled;
    }

    public Set<TopologyNode> getTopNodesRotationHandled() {
        return this.topNodesRotationHandled;
    }

    public MissionCostFunction getCostFunction() {
        return this.costFunction;
    }

    public Set<BaseMissionNode> getGoals() {
        return this.goals;
    }

    public TopologyNode getTopGoal() {
        return this.topGoal;
    }

    public Map<BaseMissionNode, Set<MissionEdge>> getIncomingEdges() {
        return this.incomingEdges;
    }

    public Map<Pair<BaseMissionNode, BaseMissionNode>, CollisionInformation> getCollisionInfo() {
        return this.collisionInfo;
    }

    public Map<BlendingKey, BaseMissionNode> getBlendingNodes() {
        return this.blendingNodes;
    }

    public MissionGraphConfig getCfg() {
        return this.cfg;
    }

    public boolean isRecovered() {
        return this.recovered;
    }

    public boolean isNeedChangeGraph() {
        return this.needChangeGraph;
    }

    public MissionGraphAccelerationTimeCalculator getAccelerationTimeCalculator() {
        return this.accelerationTimeCalculator;
    }

    public MissionCostFunctionForAstar getCostFunctionForAstar() {
        return this.costFunctionForAstar;
    }

    public void setGoals(Set<BaseMissionNode> goals) {
        this.goals = goals;
    }

    public void setTopGoal(TopologyNode topGoal) {
        this.topGoal = topGoal;
    }

    public Map<Map<TopologyNode, TopologyNode>, Double[]> getBezierCurves() {
        return this.bezierCurves;
    }

    public void setBezierCurves(Map<Map<TopologyNode, TopologyNode>, Double[]> bezierCurves) {
        this.bezierCurves = bezierCurves;
    }

    public void setRecovered(boolean recovered) {
        this.recovered = recovered;
    }

    public void setNeedChangeGraph(boolean needChangeGraph) {
        this.needChangeGraph = needChangeGraph;
    }

    public List<Astar.Listener<BaseMissionNode, MissionEdge>> getListeners() {
        return this.listeners;
    }
}
