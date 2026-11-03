package servostar.fleet.transfer;

import com.google.protobuf.*;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only Basic Data metadata RPCs, decoded using the supplied proto field
 * numbers without another plugin's private RCS Java classes. */
final class FleetMapReader {
    private static final int MAX_BYTES=16*1024*1024;
    private static final MethodDescriptor.Marshaller<byte[]> BYTES=new MethodDescriptor.Marshaller<byte[]>(){
        public InputStream stream(byte[] value){return new ByteArrayInputStream(value);}
        public byte[] parse(InputStream input){
            try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                while((n=input.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Metadata response exceeds limit");out.write(buf,0,n);}return out.toByteArray();
            }catch(IOException ex){throw new IllegalStateException("Metadata response unavailable",ex);}
        }
    };
    private final String target;
    FleetMapReader(String target){this.target=target;}
    private byte[] call(ManagedChannel channel,String method,byte[] request){
        MethodDescriptor<byte[],byte[]> rpc=MethodDescriptor.<byte[],byte[]>newBuilder().setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("kuka.rcs.bd.grpc.map.MapService/"+method).setRequestMarshaller(BYTES).setResponseMarshaller(BYTES).build();
        return ClientCalls.blockingUnaryCall(channel,rpc,CallOptions.DEFAULT.withDeadlineAfter(3,TimeUnit.SECONDS),request);
    }
    static UnknownFieldSet fields(byte[] bytes)throws IOException{return UnknownFieldSet.parseFrom(bytes);}
    static List<ByteString> parts(UnknownFieldSet x,int id){return x.getField(id).getLengthDelimitedList();}
    static String text(UnknownFieldSet x,int id){List<ByteString> xs=parts(x,id);return xs.isEmpty()?"":xs.get(0).toStringUtf8();}
    static long number(UnknownFieldSet x,int id){List<Long> xs=x.getField(id).getVarintList();return xs.isEmpty()?0:xs.get(0);}
    private static void status(UnknownFieldSet root)throws IOException{
        for(ByteString bytes:parts(root,1))if(number(fields(bytes.toByteArray()),1)!=0)throw new IOException("Fleet map metadata RPC rejected");
    }
    List<Map<String,Object>> read()throws Exception{
        ManagedChannel channel=ManagedChannelBuilder.forTarget("dns:///"+target).usePlaintext().maxInboundMessageSize(MAX_BYTES).build();
        try{
            UnknownFieldSet response=fields(call(channel,"GetAllMapInfo",new byte[0]));status(response);
            List<Map<String,Object>> out=new ArrayList<>();
            for(ByteString rawMap:parts(response,2)){
                UnknownFieldSet map=fields(rawMap.toByteArray());String code=text(map,2);
                for(ByteString rawFloor:parts(map,7)){
                    UnknownFieldSet floor=fields(rawFloor.toByteArray());String district=text(floor,3);
                    ByteArrayOutputStream request=new ByteArrayOutputStream();CodedOutputStream coded=CodedOutputStream.newInstance(request);
                    coded.writeString(1,code);coded.writeString(2,district);coded.flush();
                    UnknownFieldSet detail=fields(call(channel,"GetMapFloor",request.toByteArray()));status(detail);
                    if(parts(detail,2).size()!=1)throw new IOException("Fleet floor metadata incomplete");
                    floor=fields(parts(detail,2).get(0).toByteArray());
                    List<Map<String,Object>> nodes=new ArrayList<>();
                    for(ByteString rawNode:parts(floor,8))nodes.add(node(rawNode.toByteArray()));
                    out.add(TransferService.map("mapCode",code,"mapName",text(map,3),"buildingCode",text(map,4),"floorNumber",district,"nodes",nodes));
                }
            }
            if(out.isEmpty())throw new IOException("No complete Fleet map/floor metadata");return out;
        }finally{channel.shutdownNow();}
    }
    static Map<String,Object> node(byte[] bytes)throws IOException{
        UnknownFieldSet n=fields(bytes);List<Integer> functions=new ArrayList<>();
        for(ByteString full:parts(n,24)){
            UnknownFieldSet f=fields(full.toByteArray());
            for(int part:new int[]{1,2})for(ByteString function:parts(f,part))functions.add((int)number(fields(function.toByteArray()),3));
        }
        return TransferService.map("nodeCode",text(n,27),"externalCode",text(n,7),"nodeLabel",text(n,2),"nodeNumber",number(n,25),
            "mapCode",text(n,6),"floorNumber",text(n,5),"functions",functions);
    }
}
