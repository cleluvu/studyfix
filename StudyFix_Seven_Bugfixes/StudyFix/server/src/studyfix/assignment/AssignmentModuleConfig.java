package studyfix.assignment;
import java.nio.file.*;
public record AssignmentModuleConfig(int controlPort,int screenPort,int assignmentPort,Path coreDatabase,Path learningDatabase,int queueSize,int workers,String dockerImage){
 public static AssignmentModuleConfig load(){return new AssignmentModuleConfig(Integer.getInteger("studyfix.port",5000),Integer.getInteger("studyfix.screen.port",5001),Integer.getInteger("studyfix.assignment.port",5002),Path.of(System.getProperty("studyfix.db","data/studyfix.db")),Path.of(System.getProperty("studyfix.learning.db","data/studyfix-learning.db")),Integer.getInteger("studyfix.assignment.queue",32),Integer.getInteger("studyfix.assignment.workers",2),System.getProperty("studyfix.assignment.docker.image","eclipse-temurin:21-jdk"));}
}
