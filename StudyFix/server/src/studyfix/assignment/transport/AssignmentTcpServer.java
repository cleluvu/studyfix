package studyfix.assignment.transport;

import studyfix.StudyFixGateway;
import studyfix.assignment.protocol.AssignmentJson;
import studyfix.assignment.protocol.AssignmentWire;
import studyfix.assignment.service.AssignmentService;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated port 5002; never reads from control channel 5000 or screen relay 5001. */
public final class AssignmentTcpServer implements AutoCloseable {
 private final ServerSocket listener; private final AssignmentService service; private final ExecutorService handlers= new ThreadPoolExecutor(0,64,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"studyfix-assignment-client");t.setDaemon(true);return t;}); private final AtomicBoolean closed=new AtomicBoolean(); private Thread acceptThread;
 public AssignmentTcpServer(StudyFixGateway gateway,Path db,int port,int queueSize,int workers,String image)throws Exception{listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(port));service=new AssignmentService(gateway,db,queueSize,workers,image);}
 public int port(){return listener.getLocalPort();}
 /** In-process adapter used by the authenticated HTTP gateway; identity and room checks remain in AssignmentService. */
 public Map<String,Object> handleWebRequest(Map<String,Object> request){if(closed.get())throw new IllegalStateException("Assignment service đã dừng.");return service.handle(request);}
 public boolean isRunning(){return !closed.get()&&acceptThread!=null&&acceptThread.isAlive();}
 public AssignmentService.Metrics metrics(){return service.metrics();}
 public studyfix.assignment.model.Exercise classroomExercise(String id) throws java.sql.SQLException { return service.classroomExercise(id); }
 public synchronized void start(){if(acceptThread!=null)throw new IllegalStateException("Assignment listener đã chạy");acceptThread=new Thread(this::acceptLoop,"studyfix-assignment-listener");acceptThread.setDaemon(true);acceptThread.start();System.out.println("StudyFix assignment listener chạy ở cổng "+port());}
 private void acceptLoop(){while(!closed.get()){try{Socket s=listener.accept();if(closed.get()){s.close();continue;}s.setSoTimeout(15000);s.setTcpNoDelay(true);try{handlers.execute(()->serve(s));}catch(RejectedExecutionException e){s.close();}}catch(IOException e){if(!closed.get())System.err.println("Assignment listener: "+e.getMessage());}}}
 private void serve(Socket socket){try(socket;DataInputStream in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));DataOutputStream out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))){while(!closed.get()&&!socket.isClosed()){String raw;try{raw=AssignmentWire.read(in);}catch(EOFException e){break;}Map<String,Object> req;try{req=AssignmentJson.object(raw);}catch(RuntimeException e){AssignmentWire.write(out,AssignmentJson.stringify(AssignmentJson.mapOf("status","ERROR","errorCode","INVALID_REQUEST","message","JSON yêu cầu không hợp lệ.")));continue;}Map<String,Object> response=service.handle(req);AssignmentWire.write(out,AssignmentJson.stringify(response));}}catch(SocketTimeoutException ignored){}catch(Exception e){if(!closed.get())System.err.println("Assignment connection đóng: "+e.getMessage());}}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;try{listener.close();}catch(IOException ignored){}service.close();handlers.shutdownNow();}
}
