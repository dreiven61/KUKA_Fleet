/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.kuka.geometry.SpatialForm
 *  com.kuka.geometry.SpatialObject
 *  com.kuka.math.geometry.ITransformation
 *  com.kuka.math.geometry.Transformation
 *  com.kuka.mobilerobotics.agv.payload.v1.Payload
 *  com.kuka.util.Pair
 *  com.vividsolutions.jts.geom.Coordinate
 *  com.vividsolutions.jts.geom.Polygon
 *  org.apache.commons.collections4.CollectionUtils
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.kuka.rcs.as.pathplanner.fleet.plan.util;

import com.kuka.geometry.SpatialForm;
import com.kuka.geometry.SpatialObject;
import com.kuka.math.geometry.ITransformation;
import com.kuka.math.geometry.Transformation;
import com.kuka.mobilerobotics.agv.payload.v1.Payload;
import com.kuka.rcs.as.pathplanner.config.BeanHolder;
import com.kuka.rcs.as.pathplanner.exception.PlanPathExceptionCode;
import com.kuka.rcs.as.pathplanner.fleet.base.NoPlanFoundException;
import com.kuka.rcs.as.pathplanner.fleet.base.OrientationSet;
import com.kuka.rcs.as.pathplanner.fleet.base.actions.CustomizedActionType;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.BaseTopologyNodeId;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyEdge;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyGraph;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyNode;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.CustomizedActionProperty;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.CustomizedCollectionProperty;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeBezierCurveProperty;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeMaxVelocityAbs;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeMotion;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.NodeRobotStopAngleProperty;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.PropertyHolder;
import com.kuka.rcs.as.pathplanner.fleet.base.provider.util.FleetProviderUtil;
import com.kuka.rcs.as.pathplanner.fleet.base.provider.util.PropertyUtil;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.CollisionUtil;
import com.kuka.rcs.as.pathplanner.fleet.plan.colli.PlanningShape;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.GraphMotionExt;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.PlanInt;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.PlanStepExt;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.Plans;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.graph.TopologyGraphCollection;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.AgentConfig;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.MissionGraphConfig;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.motion.GraphMotion;
import com.kuka.rcs.as.pathplanner.fleet.plan.service.util.JtsUtils;
import com.kuka.rcs.as.pathplanner.fleet.plan.service.util.ProtoUtil;
import com.kuka.rcs.as.pathplanner.nav.Pose;
import com.kuka.rcs.as.pathplanner.nav.Position;
import com.kuka.rcs.as.pathplanner.nav.Tuple2D;
import com.kuka.rcs.as.pathplanner.nav.XYTheta;
import com.kuka.rcs.as.pathplanner.nav.geometry.BlendingDiscretizationInfo;
import com.kuka.rcs.as.pathplanner.nav.geometry.SplineCalculationAccess;
import com.kuka.rcs.as.pathplanner.nav.provider.NavUtil;
import com.kuka.rcs.as.pathplanner.nav.provider.geometry.Layer;
import com.kuka.rcs.as.pathplanner.nav.provider.geometry.NavSpatialObject;
import com.kuka.rcs.as.pathplanner.nav.provider.geometry.ShapeConverter;
import com.kuka.rcs.as.pathplanner.nav.provider.geometry.TransformationProvider;
import com.kuka.rcs.as.pathplanner.nav.robot.data.DriveType;
import com.kuka.rcs.as.pathplanner.nav.robot.data.MobileRobotType;
import com.kuka.util.Pair;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetUtil {
    private static final Logger log = LoggerFactory.getLogger(FleetUtil.class);
    private static final Logger LOG = LoggerFactory.getLogger(FleetUtil.class);
    public static final String SAME_GOAL_ERROR_MSG = "At least two vehicles are commanded to following goal node within coordinated graph motion: ";
    public static final String COORDINATED_MOTION_AWAITS_UPDATE_ERROR_MSG = " is part of a coordinated command and is configured to wait for updates upon arrival at the goal.";
    public static final String BLEND_TRUE_BUT_AWAIT_UPDATE_FALSE_ERROR_MSG = " is configured to blend over the goal node but not to await updates at the goal node.";
    public static final String COORDINATED_MOTION_WITH_PLAN_ERROR_MSG = " is part of a coordinated command and defines a plan.";
    public static final String MOTION_WITH_PLAN_AND_VIAS_ERROR_MSG = " defines a plan and via nodes.";
    public static final String MOTION_WITH_PLAN_AND_GOAL_ERROR_MSG = " defines a plan that does not match the motion's goal node.";
    public static final String MOTION_WITH_PLAN_BUT_WITHOUT_INSTANCE_FILTER_ERROR_MSG = " defines a plan but no instance filter.";
    public static final String MOTION_SKIPS_DEP_GRAPH_WITH_WZGOAL_ERROR_MSG = " is commanded to a waiting zone node and parameterized to skip dependency optimization.";
    public static final String COORDINATED_MOTION_SKIPS_DEP_GRAPH_ERROR_MSG = " is part of a coordinated command and parameterized to skip dependency optimization.";
    public static final int GRAPH_SAVE_TIMEOUT_MS = 10000;
    public static final List<Double> ORIENTATIONS_FORWARD = Collections.singletonList(0.0);
    public static final List<Double> ORIENTATIONS_DIFFERENTIAL = Arrays.asList(0.0, Math.PI);

    private FleetUtil() {
    }

    public static Map<Integer, TopologyNode> getGoals(PlanInt plan) {
        return plan.getRobotIds().stream().collect(Collectors.toMap(rId -> rId, rId -> Objects.requireNonNull(Plans.getLastStep(plan.getAll((int)rId))).getNode()));
    }

    public static double rotationTime(double startOrientation, double goalOrientation, double maxVelRot) {
        return NavUtil.angularDifference(startOrientation, goalOrientation) / maxVelRot;
    }

    public static double translationTime(Pose startPose, Pose goalPose, Tuple2D maxVel, DriveType driveType) {
        double maxVelTrans;
        double distance = startPose.distanceTrans(goalPose);
        if (driveType == DriveType.DIFFERENTIAL) {
            return distance / maxVel.getX();
        }
        int degreeDiff = 95;
        if (NavUtil.angularDifference(startPose.getTheta(), goalPose.getTheta()) < Math.toRadians(degreeDiff)) {
            double robotOrientation = (startPose.getTheta() + goalPose.getTheta()) * 0.5;
            double relativeOri = FleetUtil.relativeOrientation(robotOrientation, startPose.toPosition(), goalPose.toPosition());
            maxVelTrans = FleetUtil.maximumVelocity(relativeOri, maxVel);
        } else {
            double startOri = FleetUtil.relativeOrientation(startPose.getTheta(), startPose.toPosition(), goalPose.toPosition());
            double goalOri = FleetUtil.relativeOrientation(goalPose.getTheta(), startPose.toPosition(), goalPose.toPosition());
            double interOri = (startPose.getTheta() + goalPose.getTheta()) * 0.5;
            double maxVelStart = FleetUtil.maximumVelocity(startOri, maxVel);
            double maxVelGoal = FleetUtil.maximumVelocity(goalOri, maxVel);
            double maxVelInter = FleetUtil.maximumVelocity(interOri, maxVel);
            maxVelTrans = 0.3333333333333333 * (maxVelStart + maxVelInter + maxVelGoal);
        }
        return distance / maxVelTrans;
    }

    private static double relativeOrientation(double robotOrientation, Position start, Position goal) {
        double dx = goal.getX() - start.getX();
        double dy = goal.getY() - start.getY();
        double angle = Math.atan2(dy, dx);
        return angle - robotOrientation;
    }

    private static double maximumVelocity(double direction, Tuple2D maxVel) {
        double maxX = maxVel.getX();
        double maxY = maxVel.getY();
        double maxV = Math.max(maxX, maxY);
        double dirX = Math.abs(Math.cos(direction)) * maxV;
        double dirY = Math.abs(Math.sin(direction)) * maxV;
        double factorX = dirX > maxX ? maxX / dirX : 1.0;
        double factorY = dirY > maxY ? maxY / dirY : 1.0;
        double factor = Math.min(factorX, factorY);
        return maxV * factor;
    }

    public static double estimateDuration(TopologyEdge e, AgentConfig cfg, Pose startPose, Pose goalPose) {
        CustomizedActionProperty reach;
        CustomizedCollectionProperty tgtCustomized;
        CustomizedActionProperty leave;
        CustomizedActionProperty edgeCustomized;
        boolean mapChange = FleetUtil.graphChange(e);
        Tuple2D maxVel = cfg.getMaxVel();
        if (PropertyUtil.hasEdgeMaxMotionAbsProperty(e.getProperties(), cfg, EdgeMaxVelocityAbs.class)) {
            XYTheta velProp = PropertyUtil.getEdgeMaxMotionAbsProperty(e.getProperties(), cfg, EdgeMaxVelocityAbs.class).getXyTheta();
            double maxX = Math.min(maxVel.getX(), velProp.getX());
            double maxY = Math.min(maxVel.getY(), velProp.getY());
            double maxTheta = Math.min(maxVel.getTheta(), velProp.getTheta());
            maxVel = new Tuple2D(maxX, maxY, maxTheta);
        }
        double vRelTrans = cfg.getMaxRelativeMotionConfig().getMaxRelativeVelocityTrans();
        double vRelRot = cfg.getMaxRelativeMotionConfig().getMaxRelativeVelocityRot();
        maxVel = new Tuple2D(maxVel.getX() * vRelTrans, maxVel.getY() * vRelTrans, maxVel.getTheta() * vRelRot);
        EdgeBezierCurveProperty bezierCurveProperty = e.getProperties().get(EdgeBezierCurveProperty.class);
        if (bezierCurveProperty != null && !cfg.getRobotType().isCurvePermit()) {
            return Double.NaN;
        }
        double translationTime = mapChange ? 0.0 : (bezierCurveProperty != null ? bezierCurveProperty.getEdgeLength() / maxVel.getX() : FleetUtil.translationTime(startPose, goalPose, maxVel, cfg.getDriveType()));
        double rot = mapChange ? PropertyUtil.getGraphTransition(e, cfg.getRobotTypeCode()).getRotation() : 0.0;
        double rotationTime = FleetUtil.rotationTime(startPose.getTheta() + rot, goalPose.getTheta(), maxVel.getTheta());
        double result = cfg.getDriveType().equals((Object)DriveType.HOLONOMIC) && PropertyUtil.getMotion(e, cfg).isRotationAllowed() ? Math.max(translationTime + rotationTime / 1000.0, rotationTime) / PropertyUtil.getMaxRelVelocity(e.getProperties(), cfg) : (rotationTime > 0.0 ? rotationTime + 0.001 + translationTime : translationTime);
        if (PropertyUtil.hasEdgeActionProperty(e, cfg.getRobotTypeCode())) {
            result += PropertyUtil.getEdgeActionProperty(e, cfg.getRobotTypeCode()).getCost().doubleValue();
        }
        if (PropertyUtil.hasCustomActionProperty(e, cfg)) {
            result += PropertyUtil.getCustomActionProperty(e, cfg).getCost();
        }
        if ((edgeCustomized = PropertyUtil.getCustomizedActionProperty(e, cfg)) != null) {
            return edgeCustomized.getCost();
        }
        CustomizedCollectionProperty srcCustomized = PropertyUtil.getCustomizedActionProperty((TopologyNode)e.getSource(), cfg);
        if (srcCustomized != null && (leave = srcCustomized.getCustomizedActionProperty(CustomizedActionType.LEAVE)) != null) {
            result += leave.getCost();
        }
        if ((tgtCustomized = PropertyUtil.getCustomizedActionProperty((TopologyNode)e.getTarget(), cfg)) != null && (reach = tgtCustomized.getCustomizedActionProperty(CustomizedActionType.REACH)) != null) {
            result += reach.getCost();
        }
        return result;
    }

    public static double getNodeMaxVelocityAbs(PropertyHolder props, AgentConfig config) {
        double maxAllowedVelRatio = PropertyUtil.getMaxRelVelocity(props, config);
        double maxAllowedVelAbs = PropertyUtil.getNodeMaxVelocityAbs(props, config);
        return Math.min(maxAllowedVelAbs, config.getMaxVel().getTheta()) * maxAllowedVelRatio;
    }

    public static boolean graphChange(TopologyEdge e) {
        return ((TopologyNode)e.getSource()).getGraphId() != ((TopologyNode)e.getTarget()).getGraphId();
    }

    public static double estimateDurationBlendingSegment(TopologyEdge e, List<Pair<Pose, Double>> blendingInfo, double rotationCost, AgentConfig config) {
        Pair<Pose, Double> startPoseWithPathOri = blendingInfo.get(0);
        Pair<Pose, Double> endPoseWithPathOri = blendingInfo.get(blendingInfo.size() - 1);
        double diffStart = NavUtil.angularDifference(((Pose)startPoseWithPathOri.getA()).getTheta(), (Double)startPoseWithPathOri.getB());
        double diffEnd = NavUtil.angularDifference(((Pose)endPoseWithPathOri.getA()).getTheta(), (Double)endPoseWithPathOri.getB());
        double cost = NavUtil.angularDifference(diffStart, diffEnd) * rotationCost;
        for (int i = 1; i < blendingInfo.size(); ++i) {
            cost += FleetUtil.estimateDuration(e, config, (Pose)blendingInfo.get(i - 1).getA(), (Pose)blendingInfo.get(i).getA());
        }
        return cost;
    }

    public static Collection<Double> collectGoalOrientations(TopologyEdge e, double startOri, AgentConfig config) {
        if (FleetUtil.graphChange(e) && PropertyUtil.hasGraphTransition(e, config.getRobotTypeCode())) {
            NodeRobotStopAngleProperty stopAngleProperty = PropertyUtil.get((TopologyNode)e.getSource(), config, NodeRobotStopAngleProperty.class);
            if (stopAngleProperty != null && !CollectionUtils.isEmpty(stopAngleProperty.getRobotStopOris()) && !FleetUtil.isAllowedOrientation(stopAngleProperty.getRobotStopOris(), startOri, FleetUtil.findAllowedDifference(e, config))) {
                return Collections.emptyList();
            }
            Double rotation = PropertyUtil.getGraphTransition(e, config.getRobotTypeCode()).getRotation();
            return Collections.singleton(NavUtil.normalizeAngle(startOri + rotation));
        }
        if (PropertyUtil.hasMotionProperty(e, config)) {
            EdgeMotion motion = PropertyUtil.getMotion(e, config);
            if (motion.isRotationAllowed() && config.getDriveType() == DriveType.HOLONOMIC) {
                return FleetProviderUtil.collectGoalOrientations(e, config);
            }
            return new OrientationSet(FleetProviderUtil.collectGoalOrientations(e, config)).stream().filter(goalOri -> FleetUtil.isReachableOrientation(startOri, goalOri, e, motion, config)).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    public static boolean isReachableOrientation(double startOri, double goalOri, TopologyEdge e, EdgeMotion motion, AgentConfig cfg) {
        double relativeStartOri = NavUtil.normalizeAngle(startOri - FleetProviderUtil.getEdgeStartAngle(e));
        double relativeGoalOri = NavUtil.normalizeAngle(goalOri - FleetProviderUtil.getEdgeTargetAngle(e));
        double allowedDifference = FleetUtil.findAllowedDifference(e, cfg);
        block0 : switch (cfg.getDriveType()) {
            case HOLONOMIC: {
                switch (motion.getMode()) {
                    case VARIABLE: {
                        return true;
                    }
                    case RELATIVE_TO_PATH: {
                        if (FleetUtil.isAllowedOrientation(Collections.singletonList(relativeStartOri), relativeGoalOri, allowedDifference)) break block0;
                        return false;
                    }
                    case RELATIVE_TO_GRAPH: {
                        if (FleetUtil.isAllowedOrientation(Collections.singletonList(startOri), goalOri, allowedDifference)) break block0;
                        return false;
                    }
                    default: {
                        log.warn("Encountered unknown motion mode: {}.", (Object)motion.getMode());
                        return false;
                    }
                }
            }
            case DIFFERENTIAL: {
                if (!FleetUtil.isAllowedOrientation(Collections.singletonList(relativeStartOri), relativeGoalOri, allowedDifference)) {
                    return false;
                }
                if (!(!cfg.getRobotType().isBackwardPermit() || cfg.isTuggingPlanning() ? !FleetUtil.isAllowedOrientation(ORIENTATIONS_FORWARD, relativeStartOri, allowedDifference) : !FleetUtil.isAllowedOrientation(ORIENTATIONS_DIFFERENTIAL, relativeStartOri, allowedDifference))) break;
                return false;
            }
            default: {
                log.warn("Encountered unknown drive type: {}.", (Object)cfg.getDriveType());
                return false;
            }
        }
        return FleetUtil.isAllowedOrientation(motion.getStartOrientations(), startOri, allowedDifference) && FleetUtil.isAllowedOrientation(motion.getGoalOrientations(), goalOri, Math.toRadians(1.0)) && FleetUtil.isAllowedOrientation(motion.getRelativeStartOrientations(), relativeStartOri, allowedDifference) && FleetUtil.isAllowedOrientation(motion.getRelativeGoalOrientations(), relativeGoalOri, Math.toRadians(1.0));
    }

    private static double findAllowedDifference(TopologyEdge e, AgentConfig cfg) {
        MissionGraphConfig missionGraphConfig = BeanHolder.getBean(MissionGraphConfig.class);
        double allowedDifference = PropertyUtil.isRotationAllowed((TopologyNode)e.getSource(), cfg) ? missionGraphConfig.getRotatoryTrackingAccuracyRadian() : missionGraphConfig.getIgnorableAngleDifferenceRadian();
        return allowedDifference;
    }

    private static boolean isAllowedOrientation(Collection<Double> configured, Double target, double allowedDifference) {
        if (configured == null || configured.isEmpty()) {
            return true;
        }
        for (Double ori : configured) {
            if (!(com.kuka.rcs.as.pathplanner.nav.NavUtil.angularDifference(ori, target) <= allowedDifference + 1.0E-11)) continue;
            return true;
        }
        return false;
    }

    public static Collection<Double> collectAllowedStartOrientations(double goalOri, TopologyEdge e, AgentConfig config, TopologyGraph graph) {
        Collection<Double> startOris = FleetUtil.collectAllowedOrientations((TopologyNode)e.getSource(), graph, config);
        startOris.addAll(FleetUtil.collectGraphTransitionOrientations((TopologyNode)e.getSource(), config, graph));
        return startOris.stream().filter(ori -> new OrientationSet(FleetUtil.collectGoalOrientations(e, ori, config)).contains(goalOri)).collect(Collectors.toSet());
    }

    private static Collection<Double> collectGraphTransitionOrientations(TopologyNode n, AgentConfig config, TopologyGraph graph) {
        HashSet<Double> oris = new HashSet<Double>();
        for (TopologyEdge e : graph.incomingEdges(n)) {
            if (!FleetUtil.graphChange(e) || !PropertyUtil.hasGraphTransition((TopologyNode)e.getSource(), config.getRobotTypeCode())) continue;
            double rotation = PropertyUtil.getGraphTransition((TopologyNode)e.getSource(), config.getRobotTypeCode()).getRotation();
            for (double srcOri : new OrientationSet(FleetUtil.collectAllowedOrientations((TopologyNode)e.getSource(), graph, config))) {
                oris.add(NavUtil.normalizeAngle(srcOri + rotation));
            }
        }
        return oris;
    }

    public static Collection<Double> collectAllowedOrientations(TopologyNode n, TopologyGraph g, AgentConfig config) {
        HashSet<Double> oris = new HashSet<Double>();
        if (PropertyUtil.hasOrientations(n, config.getRobotTypeCode())) {
            oris.addAll(PropertyUtil.getOrientations(n, config.getRobotTypeCode()));
        } else {
            g.incomingEdges(n).forEach(e -> oris.addAll(FleetProviderUtil.collectEdgeTargetOrientations(config, e)));
        }
        NodeRobotStopAngleProperty stopAngleProperty = n.getProperties().get(config.getRobotTypeCode(), config.getPayloadDiff() != null, NodeRobotStopAngleProperty.class);
        if (stopAngleProperty != null) {
            oris.addAll(stopAngleProperty.getRobotStopOris());
        }
        for (TopologyEdge e2 : g.outgoingEdges(n)) {
            EdgeMotion m;
            if (!PropertyUtil.hasMotionProperty(e2, config) || (m = PropertyUtil.getMotion(e2, config)).isRotationAllowed()) continue;
            TopologyNode tgt = (TopologyNode)e2.getTarget();
            if (PropertyUtil.hasOrientations(tgt, config.getRobotTypeCode())) {
                oris.addAll(PropertyUtil.getOrientations(tgt, config.getRobotTypeCode()));
                continue;
            }
            oris.addAll(m.getStartOrientations());
            oris.addAll(FleetProviderUtil.collectRelativeOrientations(m.getRelativeStartOrientations(), e2, FleetProviderUtil::getEdgeStartAngle));
        }
        return oris;
    }

    public static void checkConsistency(GraphMotion[] motions) {
        if (motions == null) {
            throw new IllegalArgumentException("It is forbidden to command a list of graph motions which is null.");
        }
        if (motions.length == 0) {
            throw new IllegalArgumentException("It is forbidden to command an empty list of graph motions.");
        }
        if (Arrays.stream(motions).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("It is forbidden to command a list of graph motions containing null.");
        }
    }

    public static void loadNodes(GraphMotionExt mtn, TopologyGraphCollection graphs) {
        FleetUtil.checkNodeConsistency(mtn.getGoalNode(), mtn.getGraph(), graphs);
        TopologyNode goalNode = mtn.getGraph().hasName() ? mtn.getGoalNode().inGraph(mtn.getGraph().getName()) : mtn.getGoalNode().inGraph(mtn.getGraph().getId());
        mtn.setGoalNode(FleetUtil.loadNode(goalNode, graphs));
        FleetUtil.loadViaNodes(mtn, graphs);
        mtn.loadPlan(graphs);
        if (mtn.hasBlackList()) {
            mtn.setBlackList(mtn.getBlackList().stream().map(n -> FleetUtil.loadNode(n, graphs)).collect(Collectors.toList()));
        } else if (mtn.hasWhiteList()) {
            mtn.setWhiteList(mtn.getWhiteList().stream().map(n -> FleetUtil.loadNode(n, graphs)).collect(Collectors.toList()));
        }
    }

    private static void checkNodeConsistency(TopologyNode node, TopologyGraph graph, TopologyGraphCollection graphs) {
        if (node.hasGraphName()) {
            String nodeGraphName = node.getGraphName();
            String graphName = graph.getName();
            if (graphName == null && graphs.containsSubgraph(graph.getId())) {
                graphName = graphs.getSubgraph(graph.getId()).getName();
            }
            if (!Objects.equals(nodeGraphName, graphName)) {
                throw new IllegalArgumentException("Graph names of " + node + " and " + graph + " are inconsistent");
            }
        }
        if (node.getGraphId() > 0 && graph.getId() > 0 && graph.getId() != node.getGraphId()) {
            throw new IllegalArgumentException("Graph ids of " + node + " and " + graph + " are inconsistent: goal node graph id: " + node.getGraphId() + " goal graph id: " + graph.getId());
        }
        if (graph.hasName() && graphs.containsSubgraph(node.getGraphId()) && !Objects.equals(graph.getName(), graphs.getSubgraph(node.getGraphId()).getName())) {
            throw new IllegalArgumentException("Graph ids/names of " + node + " and " + graph + " are inconsistent: goal node graph id: " + node.getGraphId() + ", goal graph name: " + graph.getName() + " corresponding subgraph name (fleet state): " + graphs.getSubgraph(node.getGraphId()).getName());
        }
    }

    private static TopologyNode loadNode(BaseTopologyNodeId node, TopologyGraph graph) {
        TopologyNode result = graph.getNode(node);
        if (result != null) {
            return result;
        }
        if (node.hasGraphName()) {
            throw new IllegalArgumentException("Cannot load node " + node + ". It does not exist in graph " + node.getGraphName());
        }
        if (node.getGraphId() != 0) {
            throw new IllegalArgumentException("Cannot load node " + node + ". It does not exist in graph " + node.getGraphId());
        }
        throw new IllegalArgumentException("Cannot load node " + node + ". It does not exist in " + graph);
    }

    private static void loadViaNodes(GraphMotionExt motion, TopologyGraph graph) {
        boolean viaNodesWereResetBeforeLoad = motion.wereViaNodesReset();
        motion.setViaNodes(motion.getViaNodes().stream().map(n -> FleetUtil.loadNode(n, graph)).collect(Collectors.toList()));
        if (!viaNodesWereResetBeforeLoad) {
            motion.resetViaNodesWereResetFlag();
        }
    }

    public static List<Pose> getMotions(EdgeMotion m, List<Pair<Pose, Double>> blendingPoses, Pose goalPose) {
        List<Pose> result = IntStream.range(0, blendingPoses.size() - 1).mapToObj(i -> CollisionUtil.transformation((Tuple2D)((Pair)blendingPoses.get(i)).getA(), (Tuple2D)((Pair)blendingPoses.get(i + 1)).getA())).collect(Collectors.toList());
        Pose lastBlending = (Pose)blendingPoses.get(blendingPoses.size() - 1).getA();
        result.addAll(m.getMotions(lastBlending, goalPose));
        return result;
    }

    public static BlendingDiscretizationInfo getBlendingPoses(double blendingOri, TopologyEdge e1, double o12, TopologyEdge e2, double o22, AgentConfig config) {
        Position start = ((TopologyNode)e1.getSource()).getPosition();
        Pose goal1 = ((TopologyNode)e1.getTarget()).getPosition().toPose(o12);
        Pose goal2 = ((TopologyNode)e2.getTarget()).getPosition().toPose(o22);
        double blendingRad = PropertyUtil.getBlendingCart(e1, config);
        EdgeMotion m2 = PropertyUtil.getMotion(e2, config);
        return PropertyUtil.getMotion(e1, config).computeBlendingPoses(start, blendingOri, goal1, blendingRad, m2, goal2, config.getDriveType());
    }

    public static Pose getBlendingStartPose(double startOri, TopologyEdge e, double endOri, AgentConfig agentConfig) {
        if (!PropertyUtil.hasBlendingCart(e, agentConfig)) {
            throw new IllegalArgumentException("edge:" + e + " has no blending data");
        }
        return SplineCalculationAccess.getInstance().getSplineCalculation().getBlendingPose(PropertyUtil.getEffectiveBlendingCart(e, agentConfig), ((TopologyNode)e.getSource()).getPosition().toPose(startOri), ((TopologyNode)e.getTarget()).getPosition().toPose(endOri));
    }

    public static String getStringWithParams(GraphMotionExt m) {
        StringBuilder sb = new StringBuilder(m.toString()).append(":");
        if (m.getGoalOrientation() != null) {
            sb.append("\n\tgoal orientation: ").append(m.getGoalOrientation());
        } else {
            sb.append("\n\tno goal orientation set");
        }
        sb.append("\n\tpriority: ").append(m.getPriority());
        if (!m.getViaNodes().isEmpty()) {
            sb.append("\n\tvia nodes: ").append(m.getViaNodes());
        }
        sb.append("\n\tvia nodes have").append(m.wereViaNodesReset() ? "" : " not").append(" been reset");
        if (m.getBlackList() != null && !m.getBlackList().isEmpty()) {
            sb.append("\n\tblack list: ").append(m.getBlackList());
        }
        if (m.getWhiteList() != null && !m.getWhiteList().isEmpty()) {
            sb.append("\n\twhite list: ").append(m.getWhiteList());
        }
        if (m.willAwaitUpdatesAtGoal()) {
            sb.append("\n\twill await updates upon arrival at goal");
        }
        if (m.willBlendOverGoal()) {
            sb.append("\n\tconfigured to allow blending at goal");
        }
        return sb.toString();
    }

    public static PlanningShape generatePlanningShape(MobileRobotType type, TransformationProvider<SpatialObject> payload, double padding) {
        ArrayList<Coordinate[]> polygonList = new ArrayList<Coordinate[]>();
        polygonList.add(ShapeConverter.convertToPolygon(new TransformationProvider<SpatialForm>(type.getShape(), (ITransformation)Transformation.of((double)0.0, (double)0.0, (double)0.0))));
        SpatialObject load = payload.getType();
        for (Layer layer : NavSpatialObject.getNavObjectInfo(load).getFootprintLayers()) {
            layer.transform(payload.getTransformation());
            layer.getShapes().forEach(shape -> polygonList.add(ShapeConverter.convertToPolygon(shape)));
        }
        return PlanningShape.fromCoordinates(polygonList, padding, false);
    }

    public static PlanningShape generatePlanningShape(MobileRobotType type, Payload payload, double padding) {
        ArrayList<Coordinate[]> polygonList = new ArrayList<Coordinate[]>();
        polygonList.add(ShapeConverter.convertToPolygon(new TransformationProvider<SpatialForm>(type.getShape(), (ITransformation)Transformation.of((double)0.0, (double)0.0, (double)0.0))));
        Pose payloadDiff = ProtoUtil.protoPoseToNavPose(payload.getPose());
        com.kuka.rcs.as.pathplanner.nav.geometry.Polygon shape = JtsUtils.toPolygon(JtsUtils.overlay(payload.getShapesList()));
        polygonList.add(ShapeConverter.convertToPolygon(new TransformationProvider<SpatialForm>(shape, (ITransformation)Transformation.of((double)0.0, (double)0.0, (double)0.0))));
        return PlanningShape.fromCoordinates(polygonList, padding, false);
    }

    public static PlanningShape generatePlanningShape(Payload payload, double padding) {
        ArrayList<Coordinate[]> polygonList = new ArrayList<Coordinate[]>();
        com.kuka.rcs.as.pathplanner.nav.geometry.Polygon shape = JtsUtils.toPolygon(JtsUtils.overlay(payload.getShapesList()));
        polygonList.add(ShapeConverter.convertToPolygon(new TransformationProvider<SpatialForm>(shape, (ITransformation)Transformation.of((double)0.0, (double)0.0, (double)0.0))));
        return PlanningShape.fromCoordinates(polygonList, padding, false);
    }

    public static Polygon createPolygonFromSpatialForm(String spatialForm) {
        throw new UnsupportedOperationException();
    }

    public static boolean isSpecialAction(PlanStepExt step, PlanStepExt next) {
        return step.getNode() == next.getNode() && OrientationSet.equals(step.getOrientation(), next.getOrientation());
    }

    public static boolean checkBlendableEdgeExists(Collection<TopologyEdge> edges, TopologyNode forbiddenTarget, AgentConfig config) {
        return edges.stream().anyMatch(e -> e.getTarget() != forbiddenTarget && PropertyUtil.isBlendingToAllowed(e, config));
    }

    public static List<Double> getStopOrientations(List<Double> stopOrisFromTask, List<Double> stopOrisFromNode, PlanPathExceptionCode exceptionCode) {
        List<Double> stopOris;
        if (!stopOrisFromTask.isEmpty()) {
            if (!stopOrisFromNode.isEmpty()) {
                stopOris = new ArrayList<Double>(CollectionUtils.intersection(stopOrisFromTask, stopOrisFromNode));
                if (stopOris.isEmpty()) {
                    throw new NoPlanFoundException(exceptionCode, new Object[0]);
                }
            } else {
                stopOris = stopOrisFromTask;
            }
        } else {
            stopOris = stopOrisFromNode;
        }
        return stopOris;
    }

    public static double getMaxCost(double sumEdgeLength) {
        double maxCost;
        for (maxCost = 10.0; maxCost < sumEdgeLength; maxCost *= 10.0) {
        }
        return maxCost;
    }
}
