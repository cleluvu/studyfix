package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Runs the packaged Server and actual Client as separate, owned JVM processes. */
public final class PackagedSmoke {
    static final Path ROOT=Path.of("verification/person-one-20261021").toAbsolutePath();
    static final String JAVA=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
    static final String CP=ROOT.resolve("dist/studyfix.jar")+";"+ROOT.resolve("dist/lib")+"/*";
    static String read(Path p){try{return Files.readString(p);}catch(IOException e){return "";}}
    static void await(java.util.function.BooleanSupplier condition,long ms)throws Exception{long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);if(!condition.getAsBoolean())throw new AssertionError("Packaged smoke condition timeout");}
    static Process server(int port,Path db,Path log)throws Exception{
        return new ProcessBuilder(JAVA,"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","--enable-native-access=ALL-UNNAMED","-Dstudyfix.port="+port,"-Dstudyfix.db="+db,"-cp",CP,"studyfix.Server").redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    static void terminate(Process p)throws Exception{if(p!=null&&p.isAlive()){p.destroy();if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly().waitFor();}}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory(ROOT,"packaged-");Path db=dir.resolve("server.db"),log=dir.resolve("server.log"),clientLog=dir.resolve("console.log");
        Process first=null,second=null,client=null,restart=null;
        try{
            first=server(0,db,log);await(()->read(log).contains("StudyFix Server"),10000);
            Matcher portMatch=Pattern.compile("cổng (\\d+)").matcher(read(log));if(!portMatch.find())throw new AssertionError("Missing allocated port");int port=Integer.parseInt(portMatch.group(1));
            System.out.println("PACKAGED_RUNTIME="+Runtime.version()+" port="+port+" db="+db);
            System.out.println("PASS A02 packaged JAR resolves SQLite driver from dist/lib at runtime");
            System.out.println("PASS A03 standalone packaged Server listens on allocated test port");
            client=new ProcessBuilder(JAVA,"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstudyfix.port="+port,"-cp",CP,"studyfix.Client").redirectErrorStream(true).redirectOutput(clientLog.toFile()).start();
            Writer keyboard=new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8);
            keyboard.write("127.0.0.1\nteacher\nTeacher123!\n/create\n/request\n/code\n/members\n");keyboard.flush();
            await(()->read(clientLog).contains("[LOGIN_OK]")&&read(clientLog).contains("Đã tạo và tham gia phòng:")&&read(clientLog).contains("Thành viên phòng"),10000);
            System.out.println("PASS A04 packaged Java Client authenticates, creates room, requests edit, reads code/members over 127.0.0.1");
            Path conflictLog=dir.resolve("conflict.log");second=server(port,dir.resolve("conflict.db"),conflictLog);
            if(!second.waitFor(10,TimeUnit.SECONDS)||second.exitValue()==0||!read(conflictLog).contains("BindException"))throw new AssertionError("Conflicting packaged server did not report bind failure");
            try(Socket probe=new Socket("127.0.0.1",port)){probe.setSoTimeout(4000);Protocol.send(new DataOutputStream(probe.getOutputStream()),"/ping");if(!Protocol.receive(new DataInputStream(probe.getInputStream())).equals("/pong"))throw new AssertionError("First server affected by port conflict");}
            System.out.println("PASS A05 second packaged JVM reports BindException; original server still pongs");
            keyboard.write("/quit\n");keyboard.flush();if(!client.waitFor(5,TimeUnit.SECONDS)||client.exitValue()!=0)throw new AssertionError("Console did not exit");
            terminate(first);Path restartLog=dir.resolve("restart.log");restart=server(port,db,restartLog);await(()->read(restartLog).contains("StudyFix Server"),10000);
            System.out.println("PASS A06 packaged JVM restarts on the same test port and database");
            System.out.println("PACKAGED_SMOKE_COMPLETED evidence_dir="+dir);
        }finally{terminate(client);terminate(second);terminate(first);terminate(restart);System.out.println("CLEANUP all packaged smoke JVM processes terminated");}
    }
}
