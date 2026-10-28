
Ü
google/protobuf/wrappers.protogoogle.protobuf"#
DoubleValue
value (Rvalue""

FloatValue
value (Rvalue""

Int64Value
value (Rvalue"#
UInt64Value
value (Rvalue""

Int32Value
value (Rvalue"#
UInt32Value
value (Rvalue"!
	BoolValue
value (Rvalue"#
StringValue
value (	Rvalue""

BytesValue
value (RvalueBÉ
com.google.protobufBWrappersProtoPZ1google.golang.org/protobuf/types/known/wrapperspb¯¢GPB™Google.Protobuf.WellKnownTypesbproto3
‰
google/protobuf/any.protogoogle.protobuf"6
Any
type_url (	RtypeUrl
value (RvalueBv
com.google.protobufBAnyProtoPZ,google.golang.org/protobuf/types/known/anypb¢GPB™Google.Protobuf.WellKnownTypesbproto3
◊
7kuka/mobilerobotics/graph/v1alpha1/resource_group.proto"kuka.mobilerobotics.graph.v1alpha1"0
ResourceGroup
resource_id (	R
resourceIdB>
&com.kuka.mobilerobotics.graph.v1alpha1BResourceGroupProtoPbproto3
ó
7kuka/mobilerobotics/graph/v1alpha1/graph_property.proto"kuka.mobilerobotics.graph.v1alpha1google/protobuf/any.proto7kuka/mobilerobotics/graph/v1alpha1/resource_group.proto"õ
GraphProperty0
property (2.google.protobuf.AnyRpropertyX
resource_group (21.kuka.mobilerobotics.graph.v1alpha1.ResourceGroupRresourceGroupB>
&com.kuka.mobilerobotics.graph.v1alpha1BGraphPropertyProtoPbproto3
¶
6kuka/mobilerobotics/graph/v1alpha1/topology_edge.proto"kuka.mobilerobotics.graph.v1alpha17kuka/mobilerobotics/graph/v1alpha1/graph_property.proto"«
TopologyEdge
uuid (	Ruuid&
start_node_uuid (	RstartNodeUuid(
target_node_uuid (	RtargetNodeUuidQ

properties (21.kuka.mobilerobotics.graph.v1alpha1.GraphPropertyR
propertiesB=
&com.kuka.mobilerobotics.graph.v1alpha1BTopologyEdgeProtoPbproto3
ú
%kuka/core/geometry/v1/vector_2d.protokuka.core.geometry.v1"&
Vector2D
x (Rx
y (RyB,
com.kuka.core.geometry.v1BVector2DProtoPbproto3
˚
6kuka/mobilerobotics/graph/v1alpha1/topology_node.proto"kuka.mobilerobotics.graph.v1alpha1%kuka/core/geometry/v1/vector_2d.proto7kuka/mobilerobotics/graph/v1alpha1/graph_property.proto"ı
TopologyNode
uuid (	Ruuid

graph_uuid (	R	graphUuid
id (Rid
name (	Rname;
position (2.kuka.core.geometry.v1.Vector2DRpositionQ

properties (21.kuka.mobilerobotics.graph.v1alpha1.GraphPropertyR
propertiesB=
&com.kuka.mobilerobotics.graph.v1alpha1BTopologyNodeProtoPbproto3
Î
7kuka/mobilerobotics/graph/v1alpha1/topology_graph.proto"kuka.mobilerobotics.graph.v1alpha17kuka/mobilerobotics/graph/v1alpha1/graph_property.proto6kuka/mobilerobotics/graph/v1alpha1/topology_edge.proto6kuka/mobilerobotics/graph/v1alpha1/topology_node.proto"ö
TopologyGraph
uuid (	Ruuid
name (	Rname
map_uuid (	RmapUuidF
nodes (20.kuka.mobilerobotics.graph.v1alpha1.TopologyNodeRnodesF
edges (20.kuka.mobilerobotics.graph.v1alpha1.TopologyEdgeRedgesZ
node_properties (21.kuka.mobilerobotics.graph.v1alpha1.GraphPropertyRnodePropertiesZ
edge_properties (21.kuka.mobilerobotics.graph.v1alpha1.GraphPropertyRedgePropertiesB>
&com.kuka.mobilerobotics.graph.v1alpha1BTopologyGraphProtoPbproto3
Å
=kuka/mobilerobotics/graph/v1alpha1/graph_editor_service.proto"kuka.mobilerobotics.graph.v1alpha1google/protobuf/wrappers.proto6kuka/mobilerobotics/graph/v1alpha1/topology_edge.proto7kuka/mobilerobotics/graph/v1alpha1/topology_graph.proto6kuka/mobilerobotics/graph/v1alpha1/topology_node.proto"`
UpsertGraphsRequestI
graphs (21.kuka.mobilerobotics.graph.v1alpha1.TopologyGraphRgraphs"7
UpsertGraphsResponse
graph_uuids (	R
graphUuids"6
RemoveGraphsRequest
graph_uuids (	R
graphUuids"
RemoveGraphsResponse"{
UpsertNodesRequest

graph_uuid (	R	graphUuidF
nodes (20.kuka.mobilerobotics.graph.v1alpha1.TopologyNodeRnodes"4
UpsertNodesResponse

node_uuids (	R	nodeUuids"R
RemoveNodesRequest

graph_uuid (	R	graphUuid

node_uuids (	R	nodeUuids"
RemoveNodesResponse"€
SplitEdgeRequest

graph_uuid (	R	graphUuid&
start_node_uuid (	RstartNodeUuid(
target_node_uuid (	RtargetNodeUuidV
node_position_percentage (2.google.protobuf.DoubleValueRnodePositionPercentage"h
SplitEdgeResponse4
intermediate_node_uuid (	RintermediateNodeUuid

edge_uuids (	R	edgeUuids"{
UpsertEdgesRequest

graph_uuid (	R	graphUuidF
edges (20.kuka.mobilerobotics.graph.v1alpha1.TopologyEdgeRedges"4
UpsertEdgesResponse

edge_uuids (	R	edgeUuids"R
RemoveEdgesRequest

graph_uuid (	R	graphUuid

edge_uuids (	R	edgeUuids"
RemoveEdgesResponse2ñ
GraphEditorServiceÅ
UpsertGraphs7.kuka.mobilerobotics.graph.v1alpha1.UpsertGraphsRequest8.kuka.mobilerobotics.graph.v1alpha1.UpsertGraphsResponseÅ
RemoveGraphs7.kuka.mobilerobotics.graph.v1alpha1.RemoveGraphsRequest8.kuka.mobilerobotics.graph.v1alpha1.RemoveGraphsResponse~
UpsertNodes6.kuka.mobilerobotics.graph.v1alpha1.UpsertNodesRequest7.kuka.mobilerobotics.graph.v1alpha1.UpsertNodesResponse~
RemoveNodes6.kuka.mobilerobotics.graph.v1alpha1.RemoveNodesRequest7.kuka.mobilerobotics.graph.v1alpha1.RemoveNodesResponse~
UpsertEdges6.kuka.mobilerobotics.graph.v1alpha1.UpsertEdgesRequest7.kuka.mobilerobotics.graph.v1alpha1.UpsertEdgesResponse~
RemoveEdges6.kuka.mobilerobotics.graph.v1alpha1.RemoveEdgesRequest7.kuka.mobilerobotics.graph.v1alpha1.RemoveEdgesResponsex
	SplitEdge4.kuka.mobilerobotics.graph.v1alpha1.SplitEdgeRequest5.kuka.mobilerobotics.graph.v1alpha1.SplitEdgeResponseBC
&com.kuka.mobilerobotics.graph.v1alpha1BGraphEditorServiceProtoPbproto3