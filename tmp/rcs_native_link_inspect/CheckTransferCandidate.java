import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuka.interfaces.plugin.entity.*;
import com.kuka.interfaces.plugin.handle.RackMoveNodeActionHandler;

// Offline conversion only. No application bootstrap, API call, database or robot connection.
class CheckTransferCandidate {
  public static void main(String[] args) throws Exception {
    AgvTaskDto<?> task = new ObjectMapper().readValue(Files.readString(Path.of(args[0])), AgvTaskDto.class);
    Method convert = RackMoveNodeActionHandler.class.getDeclaredMethod("doHandle", AgvTaskDto.class);
    convert.setAccessible(true);
    List<?> actions = (List<?>)convert.invoke(new RackMoveNodeActionHandler(), task);
    if (actions.size() != 2) throw new IllegalStateException("Expected two actions");
    for (Object entry : actions) {
      NodeActionDO action = (NodeActionDO)entry;
      System.out.println(action.getNodeSequence()+" "+action.getNodeCode()+" "+action.getActionType());
    }
    NodeActionDO from = (NodeActionDO)actions.get(0), to = (NodeActionDO)actions.get(1);
    if (!"lay01-dist1-89".equals(from.getNodeCode()) || !"UP_CONTAINER".equals(from.getActionType())
        || !"lay01-dist1-97".equals(to.getNodeCode()) || !"DOWN_CONTAINER".equals(to.getActionType()))
      throw new IllegalStateException("Unexpected conversion");
    System.out.println("OFFLINE_CONVERSION_OK; NO_REQUEST_SENT");
  }
}
