package vn.studyfix.client.exercise;

import vn.studyfix.client.exercise.protocol.AssignmentJson;
import java.io.*;
import java.net.*;
import java.util.*;

/** One request per framed TCP connection to assignment port 5002. Never touches control reader. */
final class AssignmentConnection {
 private final String host; private final int port; private final String sessionId;
 AssignmentConnection(String host,int port,String sessionId){this.host=Objects.requireNonNull(host);this.port=port;this.sessionId=Objects.requireNonNull(sessionId);}
 Map<String,Object> request(String type,String roomId,Map<String,Object> fields)throws IOException {
  Map<String,Object> req=new LinkedHashMap<>();req.put("protocolVersion",1);req.put("requestId",UUID.randomUUID().toString());req.put("type",type);req.put("sessionId",sessionId);req.put("roomId",roomId==null?"":roomId);req.putAll(fields);
  byte[] data=AssignmentJson.stringify(req).getBytes(java.nio.charset.StandardCharsets.UTF_8);if(data.length>65536)throw new IOException("Yêu cầu vượt 64 KiB.");
  try(Socket socket=new Socket()) {socket.connect(new InetSocketAddress(host,port),3000);socket.setSoTimeout(15000);socket.setTcpNoDelay(true);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));DataInputStream in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));out.writeInt(data.length);out.write(data);out.flush();int length=in.readInt();if(length<0||length>65536)throw new IOException("Phản hồi assignment frame không hợp lệ.");byte[] response=new byte[length];in.readFully(response);return AssignmentJson.object(new String(response,java.nio.charset.StandardCharsets.UTF_8));}
  catch(ConnectException e){throw new IOException("Không kết nối được cổng bài tập "+port+" trên "+host+". Hãy chạy AssignmentEnabledServerMain.",e);}
 }
}
