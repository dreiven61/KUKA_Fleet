import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.ozsoft.secs4j.format.*;
import com.kuka.secs4kuka.app.msgs.*;
class NativeLibraryProbe {
 static Data<?> read(Element e) {
  switch(e.getTagName()) {
   case "L": L l=new L();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element)l.addItem(read((Element)n));return l;
   case "A":return new A(e.getTextContent());
   case "U2":return new U2(Integer.parseInt(e.getTextContent()));
   case "U4":return new U4(Long.parseLong(e.getTextContent()));
   default:throw new IllegalArgumentException(e.getTagName());
  }
 }
 public static void main(String[] args) throws Exception {
  var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  var doc=f.newDocumentBuilder().parse(args[0]);
  for(String name:new String[]{"Transfer89To97","Transfer97To89"}) {
   Element msg=(Element)doc.getElementsByTagName(name).item(0);
   Element data=(Element)msg.getElementsByTagName("DataItem").item(0);
   Element body=null;for(Node n=data.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element){body=(Element)n;break;}
   var parsed=(SecsTransferCommand)CommandParser.parseSecsCommand(read(body),1,true);
   var t=parsed.getTransferInfos().get(0);
   System.out.println(name+" PARSE_OK "+parsed.getCommandId()+" "+t.getCarrierId()+" "+t.getSourcePort()+" -> "+t.getDestPort());
  }
 }
}
