package studyfix.assignment;

import studyfix.Server;
import studyfix.WebGatewayServer;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.util.concurrent.CountDownLatch;

/** Safe additive launcher. Existing studyfix.Server/main/build.xml are not changed. */
public final class AssignmentEnabledServerMain {
 private AssignmentEnabledServerMain(){}
 public static void main(String[] args)throws Exception{
  AssignmentModuleConfig c=AssignmentModuleConfig.load();Server core=new Server(c.controlPort(),c.coreDatabase());AssignmentTcpServer assignments=null;WebGatewayServer web=null;
  try{assignments=new AssignmentTcpServer(core.gateway(),c.learningDatabase(),c.assignmentPort(),c.queueSize(),c.workers(),c.dockerImage());web=new WebGatewayServer(core,assignments);WebGatewayServer finalWeb=web;AssignmentTcpServer finalAssignments=assignments;Runtime.getRuntime().addShutdownHook(new Thread(()->{finalWeb.close();finalAssignments.close();core.close();},"studyfix-assignment-shutdown"));core.start();assignments.start();web.start();System.out.println("StudyFix full mode: control="+core.port()+", screen="+core.screenSharePort()+", assignment="+assignments.port()+", web="+web.port());new CountDownLatch(1).await();}catch(Exception e){if(web!=null)web.close();if(assignments!=null)assignments.close();core.close();throw e;}
 }
}
