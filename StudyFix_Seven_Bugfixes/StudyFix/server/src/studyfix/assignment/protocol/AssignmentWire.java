package studyfix.assignment.protocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class AssignmentWire {
 public static final int MAX_BYTES=65536;
 private AssignmentWire(){}
 public static String read(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>MAX_BYTES)throw new IOException("Assignment frame invalid length: "+n);byte[] b=new byte[n];in.readFully(b);return new String(b,StandardCharsets.UTF_8);}
 public static void write(DataOutputStream out,String s)throws IOException{byte[]b=s.getBytes(StandardCharsets.UTF_8);if(b.length>MAX_BYTES)throw new IOException("Assignment payload exceeds 64KiB");synchronized(out){out.writeInt(b.length);out.write(b);out.flush();}}
}
