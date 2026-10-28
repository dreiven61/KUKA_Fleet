import org.ozsoft.secs4j.format.*;
import com.kuka.secs4kuka.app.msgs.*;
import java.util.HexFormat;
class NativeParameterProbe {
 public static void main(String[] args) throws Exception {
  var body=new L(new A("RUN_WORKFLOW"),new L(new L(new A("COMMANDID"),new A("WF_TEST_001")),new L(new A("WORKFLOW"),new A("W000000002"))));
  var command=CommandParser.parseSecsCommand(body,1,false);
  System.out.println(command.getName());
  System.out.println(HexFormat.of().formatHex(command.getParamData().toByteArray()));
 }
}
