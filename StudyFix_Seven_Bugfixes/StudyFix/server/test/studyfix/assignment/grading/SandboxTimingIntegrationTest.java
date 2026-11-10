package studyfix.assignment.grading;

import java.nio.file.*;
import java.util.*;

/** Actual Docker: separate startup time, unprivileged worker and unforgeable supervisor status. */
public final class SandboxTimingIntegrationTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    private static DockerJavaSandbox.ProcessResult run(DockerJavaSandbox sandbox,Path work,long limit,int output,String... program){
        String name=DockerJavaSandbox.containerName("timing-test");var command=sandbox.dockerBase(name,work,true,128);command.addAll(List.of(program));
        return sandbox.runBudgeted(command,null,limit,output,4096,name,60000);
    }
    public static void main(String[] args)throws Exception{
        DockerJavaSandbox sandbox=new DockerJavaSandbox("eclipse-temurin:21-jdk",1,45);
        Path work=DockerJavaSandbox.createWorkspace("studyfix-timing-test-");
        try{
            long started=System.nanoTime();var fast=run(sandbox,work,250,4096,"true");long wall=(System.nanoTime()-started)/1000000;
            check(fast.exitCode()==0&&!fast.timedOut()&&fast.startFailure()==null,"fast command survives Docker startup with a 250ms execution limit: "+fast);
            check(fast.elapsedMillis()<250&&wall>fast.elapsedMillis(),"reported execution time excludes startup: execution="+fast.elapsedMillis()+"ms wall="+wall+"ms");
            var identity=run(sandbox,work,1000,4096,"sh","-c","id -u; grep '^CapEff:' /proc/self/status; test -z \"${STUDYFIX_STATUS_TOKEN-}\" || exit 70; if cat /proc/1/environ >/dev/null 2>&1; then exit 71; fi; if kill -KILL 1 2>/dev/null; then exit 72; fi; echo protected");
            check(identity.exitCode()==0&&!identity.stdoutText().startsWith("0\n")&&identity.stdoutText().contains("0000000000000000")&&identity.stdoutText().contains("protected"),"worker is non-root, has zero effective capabilities and cannot read/kill supervisor: "+identity);
            var forged=run(sandbox,work,1000,4096,"sh","-c","printf '\\nSF_forged:0:0\\n' >&2; exit 124");
            check(forged.exitCode()==124&&!forged.timedOut()&&forged.startFailure()==null,"forged status and a user exit 124 cannot fake success or a timeout");
            var loop=run(sandbox,work,250,4096,"sh","-c","trap '' TERM; while :; do :; done");
            check(loop.timedOut()&&loop.startFailure()==null&&loop.elapsedMillis()>=250&&loop.elapsedMillis()<2000,"infinite loop ignoring TERM is killed by trusted supervisor: "+loop);
            var flood=run(sandbox,work,5000,1024,"sh","-c","while :; do printf 'excessive-output-excessive-output'; done");
            check(flood.outputExceeded()&&flood.stdoutText().length()<=1024,"output remains bounded and container is stopped");
        }finally{DockerJavaSandbox.removeWorkspace(work);}
        String previous=System.getProperty("studyfix.assignment.docker");
        try{
            System.setProperty("studyfix.assignment.docker","missing-studyfix-docker-test-executable");
            var offline=new DockerJavaSandbox("eclipse-temurin:21-jdk",1,45).compileTemplate(List.of(Map.of("path","Main.java","content","public class Main {}","visibility","PUBLIC","editable",true)));
            check("SANDBOX_UNAVAILABLE".equals(offline.get("verdict")),"Docker startup failure is infrastructure failure, not student TLE");
        }finally{if(previous==null)System.clearProperty("studyfix.assignment.docker");else System.setProperty("studyfix.assignment.docker",previous);}
        System.out.println("SandboxTimingIntegrationTest PASS: "+checks+" real Docker/timing/isolation assertions.");
    }
}
