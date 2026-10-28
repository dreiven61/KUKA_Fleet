import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import com.kuka.rcs.bd.grpc.map.MapFloorGto;

class CheckFleetMap {
    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            var root = JsonParser.parseString(Files.readString(Path.of(arg))).getAsJsonObject();
            int floors = 0, nodes = 0, edges = 0;
            for (var floor : root.getAsJsonArray("floorList")) {
                var builder = MapFloorGto.newBuilder();
                // The native UI export contains fields absent from this SDK,
                // including customerUi. Validate only the SDK's known projection.
                JsonFormat.parser().ignoringUnknownFields().merge(floor.toString(), builder);
                var result = builder.build();
                floors++;
                nodes += result.getNodeListCount();
                edges += result.getEdgeListCount();
            }
            System.out.printf("SDK_KNOWN_FIELDS_OK %s floors=%d nodes=%d edges=%d%n",
                    Path.of(arg).getFileName(), floors, nodes, edges);
        }
    }
}
