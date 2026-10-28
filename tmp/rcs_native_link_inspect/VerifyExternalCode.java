import java.lang.reflect.Method;
import com.kuka.rcs.bd.iisy.data.mapper.MapGraphMapper;
import com.kuka.rcs.bd.map.model.NodeDto;
import com.kuka.mobilerobotics.graph.v1alpha1.TopologyNode;
import com.kuka.mobilerobotics.graph.v1alpha1.StationProperty;

// Offline only: calls the mapper against temporary in-memory nodes.
public class VerifyExternalCode {
    public static void main(String[] args) throws Exception {
        MapGraphMapper mapper = new MapGraphMapper();
        Method method = MapGraphMapper.class.getDeclaredMethod(
            "setStationProperty", TopologyNode.Builder.class, NodeDto.class);
        method.setAccessible(true);
        for (String foreignCode : new String[]{"", "SECS_ST_89", "SECS_ST_97"}) {
            NodeDto node = new NodeDto();
            node.setForeignCode(foreignCode);
            TopologyNode.Builder mapped = TopologyNode.newBuilder();
            method.invoke(mapper, mapped, node);
            if (foreignCode.isEmpty()) {
                if (mapped.getPropertiesCount() != 0) throw new AssertionError();
                System.out.println("EMPTY_EXTERNAL_CODE -> NO_STATION_PROPERTY");
            } else {
                if (mapped.getPropertiesCount() != 1) throw new AssertionError();
                var any = mapped.getProperties(0).getProperty();
                var property = any.unpack(StationProperty.class);
                if (!property.getStationUuid().equals(foreignCode)) throw new AssertionError();
                System.out.println("EXTERNAL_CODE=" + foreignCode
                    + " -> " + any.getTypeUrl()
                    + " station_uuid=" + property.getStationUuid());
            }
        }
    }
}
