/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.rcs.as.pathplanner.fleet.base.provider.util;

import com.kuka.rcs.as.pathplanner.fleet.base.graph.BaseTopologyNodeId;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyEdge;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyGraph;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.TopologyNode;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeBezierCurveProperty;
import com.kuka.rcs.as.pathplanner.fleet.base.graph.properties.EdgeMotion;
import com.kuka.rcs.as.pathplanner.fleet.base.provider.util.PropertyUtil;
import com.kuka.rcs.as.pathplanner.fleet.plan.planner.mission.AgentConfig;
import com.kuka.rcs.as.pathplanner.nav.Position;
import com.kuka.rcs.as.pathplanner.nav.provider.NavUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FleetProviderUtil {
    private FleetProviderUtil() {
    }

    public static <T> T get(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            throw new IllegalArgumentException("Key '" + key + "' not found in params");
        }
        return (T)params.get(key);
    }

    public static TopologyNode getNode(String graphId, String nodeId, TopologyGraph graph) {
        BaseTopologyNodeId target = NavUtil.isInt(nodeId) ? BaseTopologyNodeId.withId(Integer.parseInt(nodeId)) : BaseTopologyNodeId.withName(nodeId);
        target = NavUtil.isInt(graphId) ? target.inGraph(Integer.parseInt(graphId)) : target.inGraph(graphId);
        return graph.getNode(target);
    }

    public static double getEdgeTargetAngle(TopologyEdge e) {
        EdgeBezierCurveProperty bezierCurveProperty = PropertyUtil.getBezierCurveProperty(e);
        Position sourcePos = ((TopologyNode)e.getSource()).getPosition();
        Position targetPos = ((TopologyNode)e.getTarget()).getPosition();
        assert (sourcePos.distance(targetPos) > 0.0);
        if (bezierCurveProperty != null) {
            List<Position> points = bezierCurveProperty.getControlNodeList();
            for (int i = points.size() - 1; i >= 0; --i) {
                Position startPoint = points.get(i);
                if (!(startPoint.distance(targetPos) > 1.0E-11)) continue;
                return NavUtil.getLineAngle(startPoint, targetPos);
            }
        }
        return NavUtil.getLineAngle(sourcePos, targetPos);
    }

    public static double getEdgeStartAngle(TopologyEdge e) {
        EdgeBezierCurveProperty bezierCurveProperty = PropertyUtil.getBezierCurveProperty(e);
        Position sourcePos = ((TopologyNode)e.getSource()).getPosition();
        Position targetPos = ((TopologyNode)e.getTarget()).getPosition();
        assert (sourcePos.distance(targetPos) > 0.0);
        if (bezierCurveProperty != null) {
            List<Position> points = bezierCurveProperty.getControlNodeList();
            for (Position targetPoint : points) {
                if (!(sourcePos.distance(targetPoint) > 1.0E-11)) continue;
                return NavUtil.getLineAngle(sourcePos, targetPoint);
            }
        }
        return NavUtil.getLineAngle(sourcePos, targetPos);
    }

    public static Collection<Double> collectRelativeOrientations(Collection<Double> lineOris, TopologyEdge e, Function<TopologyEdge, Double> getOri) {
        Position targetPos;
        Position sourcePos = ((TopologyNode)e.getSource()).getPosition();
        if (sourcePos.distance(targetPos = ((TopologyNode)e.getTarget()).getPosition()) <= 0.0) {
            return Collections.emptySet();
        }
        HashSet<Double> oris = new HashSet<Double>();
        double edgeOrientationMap = getOri.apply(e);
        for (Double lOri : lineOris) {
            oris.add(NavUtil.normalizeAngle(edgeOrientationMap + lOri));
        }
        return oris;
    }

    public static Collection<Double> collectEdgeTargetOrientations(AgentConfig agentConfig, TopologyEdge e) {
        if (PropertyUtil.hasMotionProperty(e, agentConfig)) {
            EdgeMotion m = PropertyUtil.getMotion(e, agentConfig);
            HashSet<Double> oris = new HashSet<Double>(m.getGoalOrientations());
            oris.addAll(FleetProviderUtil.collectRelativeOrientations(m.getRelativeGoalOrientations(), e, FleetProviderUtil::getEdgeTargetAngle));
            return oris;
        }
        return Collections.emptySet();
    }

    public static Collection<Double> collectGoalOrientations(TopologyEdge e, AgentConfig agentConfig) {
        HashSet<Double> oris = new HashSet<Double>(PropertyUtil.getOrientations((TopologyNode)e.getTarget(), agentConfig.getRobotTypeCode()));
        oris.addAll(FleetProviderUtil.collectEdgeTargetOrientations(agentConfig, e));
        return oris;
    }

    public static double getClosestGoalOrientation(TopologyEdge edge, AgentConfig agentConfig, double orientation) {
        double closestOri = Double.NaN;
        double minDiff = Double.MAX_VALUE;
        for (Double o : FleetProviderUtil.collectGoalOrientations(edge, agentConfig)) {
            double diff = NavUtil.angularDifference(orientation, o);
            if (!(diff < minDiff)) continue;
            closestOri = o;
            minDiff = diff;
        }
        return closestOri;
    }
}
