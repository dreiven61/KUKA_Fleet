import org.ozsoft.secs4j.format.*;
import com.kuka.secs4kuka.app.msgs.*;
class NativeTransferProbe {
 static L p(String key,Data<?> value){return new L(new A(key),value);}
 static L body(boolean replace) {
  L info=new L(p("COMMANDID",new A("TRANSFER_TEST_001")),p("PRIORITY",new U2(1)));
  if(replace)info.addItem(p("REPLACE",new U2(0)));
  return new L(new U4(0),new A(""),new A("TRANSFER"),new L(p("COMMANDINFO",info),p("TRANSFERINFO",new L(p("CARRIERID",new A("SIM_CARGO_001")),p("SOURCEPORT",new A("SOURCE_COMPARTMENT_TBD")),p("DESTPORT",new A("DEST_COMPARTMENT_TBD"))))));
 }
 public static void main(String[] args) throws Exception {
  var parsed=(SecsTransferCommand)CommandParser.parseSecsCommand(body(true),1,true);
  if(!parsed.getCommandId().equals("TRANSFER_TEST_001") || parsed.getPriority()!=1 || parsed.replace()!=0 || parsed.getTransferInfos().size()!=1)throw new AssertionError();
  System.out.println("TRANSFER_S2F49_PARSE_OK; no network or action execution");
  try {CommandParser.parseSecsCommand(body(false),2,true);throw new AssertionError("Missing REPLACE was accepted");}
  catch(org.ozsoft.secs4j.SecsParseException expected){System.out.println("MISSING_REPLACE_REJECTED: "+expected.getMessage());}
 }
}
