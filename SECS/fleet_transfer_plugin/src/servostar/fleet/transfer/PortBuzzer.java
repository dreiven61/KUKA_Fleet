package servostar.fleet.transfer;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** A configured PLC register triggers a PLC-owned, self-resetting buzzer pulse. */
final class PortBuzzer {
    private final Map<String,Output> outputs=new HashMap<>();

    PortBuzzer(Object configuration) {
        if(!(configuration instanceof Map))throw new IllegalArgumentException("portBuzzers must be an object");
        for(Map.Entry<?,?> entry:((Map<?,?>)configuration).entrySet()){
            String port=String.valueOf(entry.getKey());
            if(!WorkPorts.identifier(port,64) || !(entry.getValue() instanceof Map))
                throw new IllegalArgumentException("Invalid buzzer port binding");
            Map<?,?> settings=(Map<?,?>)entry.getValue();
            Object address=settings.get("host");
            if(!(address instanceof String) || !((String)address).matches("[A-Za-z0-9.:-]{1,253}"))
                throw new IllegalArgumentException("Invalid buzzer PLC host");
            Object node=settings.get("nodeCode");
            if(!(node instanceof String) || !WorkPorts.identifier((String)node,64))
                throw new IllegalArgumentException("Invalid buzzer Fleet node binding");
            outputs.put(port,new Output((String)node,(String)address,number(settings,"port",1,65535),
                number(settings,"unitId",0,255),number(settings,"register",0,65535),
                number(settings,"value",1,65535)));
        }
    }

    private static int number(Map<?,?> settings,String name,int min,int max) {
        Object value=settings.get(name);
        if(!(value instanceof Integer) || (int)value<min || (int)value>max)
            throw new IllegalArgumentException("Invalid buzzer PLC "+name);
        return (int)value;
    }

    boolean pulse(String port,String currentNode)throws IOException {
        Output output=outputs.get(port);
        return output!=null && output.node.equals(currentNode) && output.pulse();
    }

    private static final class Output {
        final String node,host;
        final int port,unit,register,value;

        Output(String node,String host,int port,int unit,int register,int value) {
            this.node=node;
            this.host=host;
            this.port=port;
            this.unit=unit;
            this.register=register;
            this.value=value;
        }

        boolean pulse()throws IOException {
            // Each socket carries one Modbus TCP transaction. FC06 echoes its request.
            byte[] frame=ByteBuffer.allocate(12).putShort((short)1).putShort((short)0)
                .putShort((short)6).put((byte)unit).put((byte)6)
                .putShort((short)register).putShort((short)value).array();
            try(Socket socket=new Socket()){
                try{socket.connect(new InetSocketAddress(host,port),1000);}
                catch(IOException unavailable){return false;}
                socket.setSoTimeout(1000);
                socket.getOutputStream().write(frame);
                DataInputStream input=new DataInputStream(socket.getInputStream());
                byte[] header=new byte[7];
                input.readFully(header);
                int length=ByteBuffer.wrap(header).getShort(4)&0xffff;
                if(length==3 && header[0]==0 && header[1]==1 && header[2]==0 && header[3]==0 && header[6]==(byte)unit){
                    int function=input.readUnsignedByte();
                    input.readUnsignedByte();
                    if(function==0x86)return false;
                }
                if(length!=6)throw new IOException("Invalid Modbus buzzer response length");
                byte[] reply=Arrays.copyOf(header,12);
                input.readFully(reply,7,5);
                if(!Arrays.equals(frame,reply))throw new IOException("Mismatched Modbus buzzer response");
                return true;
            }
        }
    }
}
