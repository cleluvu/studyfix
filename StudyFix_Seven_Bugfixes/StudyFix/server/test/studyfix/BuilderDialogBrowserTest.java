package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
/** Isolated UI regression; the browser exercises the actual authoring endpoints and Docker. */
public final class BuilderDialogBrowserTest {
 public static void main(String[] args)throws Exception {
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"builder-dialog-browser-");
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,16,1,"eclipse-temurin:21-jdk");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String modules=System.getenv("STUDYFIX_BUILDER_BROWSER_MODULES");if(modules==null||modules.isBlank())throw new IllegalStateException("Playwright modules required");
   var command=new ProcessBuilder("C:/Program Files/nodejs/node.exe",Path.of("../web-client/tests/builder_complete_browser.cjs").toAbsolutePath().toString(),"http://127.0.0.1:"+web.port()).inheritIO();command.environment().put("STUDYFIX_BROWSER_TEST_MODULES",modules);Process child=command.start();if(!child.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("Dialog browser timeout");}if(child.exitValue()!=0)throw new AssertionError("Dialog Chrome failed");
  }
 }
}
