package studyfix.assignment.grading;

import com.sun.source.util.*;
import com.sun.source.tree.*;
import javax.tools.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Trusted CLI executed only in the Docker judge. Analyzes source; never loads student classes. */
public final class StructureInspector {
 public static void main(String[] args)throws Exception {
  var contract=object(Files.readString(Path.of(args[0]),StandardCharsets.UTF_8));
  List<String> files=Arrays.asList(args).subList(1,args.length);var diagnostics=new DiagnosticCollector<JavaFileObject>();
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
   var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-implicit:none","-classpath","classes"),null,manager.getJavaFileObjectsFromStrings(files));
   task.parse();task.analyze();
   if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR)){System.out.print(stringify(mapOf("error","COMPILE_ERROR","checks",List.of())));return;}
   Elements elements=task.getElements();Types types=task.getTypes();Trees trees=Trees.instance(task);List<Map<String,Object>> checks=new ArrayList<>();
   for(Object value:list(contract.get("rules"))){var r=map(value);String ownerName=text(r,"owner",""),kind=text(r,"kind",""),name=text(r,"name","");TypeElement owner=elements.getTypeElement(ownerName);Element found=owner;boolean passed=false;String reason="Không tìm thấy lớp/kiểu "+ownerName;
    if(owner!=null&&trees.getPath(owner)!=null){
     var members=Boolean.FALSE.equals(r.get("declared"))?elements.getAllMembers(owner):owner.getEnclosedElements();
     if(kind.equals("FIELD"))found=members.stream().filter(e->e.getKind()==ElementKind.FIELD&&e.getSimpleName().contentEquals(name)).findFirst().orElse(null);
     if(Set.of("METHOD","OVERRIDE","ANNOTATION","CONSTRUCTOR").contains(kind))found=members.stream().filter(e->e instanceof ExecutableElement).map(e->(ExecutableElement)e).filter(e->kind.equals("CONSTRUCTOR")?e.getKind()==ElementKind.CONSTRUCTOR:e.getKind()==ElementKind.METHOD&&e.getSimpleName().contentEquals(name)).filter(e->parameters(e,list(r.get("parameters")))).findFirst().orElse(null);
     if(found==null)reason="Thiếu "+kind+" "+ownerName+"."+name+list(r.get("parameters"));
     else {
      passed=true;String expected=text(r,"type","");
      if(kind.equals("TYPE"))passed=found.getKind().name().equals(expected.equals("ANNOTATION")?"ANNOTATION_TYPE":expected);
      else if(!expected.isEmpty()&&found instanceof VariableElement field)passed=same(field.asType().toString(),expected);
      else if(!expected.isEmpty()&&found instanceof ExecutableElement method)passed=same(method.getReturnType().toString(),expected);
      if(kind.equals("EXTENDS")||kind.equals("IMPLEMENTS")){
       TypeElement target=elements.getTypeElement(text(r,"target",""));passed=target!=null;
       if(passed&&kind.equals("IMPLEMENTS"))passed=target.getKind()==ElementKind.INTERFACE;
       if(passed&&kind.equals("EXTENDS"))passed=target.getKind()==ElementKind.CLASS;
       if(passed){boolean direct=!Boolean.FALSE.equals(r.get("direct"));passed=direct?(kind.equals("EXTENDS")?types.isSameType(types.erasure(owner.getSuperclass()),types.erasure(target.asType())):owner.getInterfaces().stream().anyMatch(t->types.isSameType(types.erasure(t),types.erasure(target.asType())))):types.isSubtype(types.erasure(owner.asType()),types.erasure(target.asType()));}
      }
      if(kind.equals("OVERRIDE")){TypeElement target=elements.getTypeElement(text(r,"target",""));ExecutableElement method=(ExecutableElement)found;passed=target!=null&&elements.getAllMembers(target).stream().filter(e->e instanceof ExecutableElement).anyMatch(e->elements.overrides(method,(ExecutableElement)e,owner));}
      if(kind.equals("ANNOTATION"))passed=found.getAnnotationMirrors().stream().anyMatch(a->same(a.getAnnotationType().toString(),text(r,"target","")));
      String visibility=text(r,"visibility","");var flags=found.getModifiers();if(!visibility.isEmpty())passed&=visibility.equals("PACKAGE")?flags.stream().noneMatch(f->Set.of(Modifier.PUBLIC,Modifier.PROTECTED,Modifier.PRIVATE).contains(f)):flags.contains(Modifier.valueOf(visibility));
      for(Object flag:list(r.get("modifiers")))passed&=flags.contains(Modifier.valueOf(String.valueOf(flag)));
      for(Object flag:list(r.get("forbiddenModifiers")))passed&=!flags.contains(Modifier.valueOf(String.valueOf(flag)));
      reason=(passed?"Đạt: ":"Chưa đúng kiểu, quyền truy cập, modifier hoặc quan hệ: ")+kind+" "+ownerName+(name.isEmpty()?"":"."+name)+(expected.isEmpty()?"":" : "+expected)+(visibility.isEmpty()?"":" · "+visibility)+(text(r,"target","").isEmpty()?"":" → "+r.get("target"));
     }
    }
    long line=0;TreePath path=found==null?null:trees.getPath(found);if(path!=null){long position=trees.getSourcePositions().getStartPosition(path.getCompilationUnit(),path.getLeaf());if(position>=0)line=path.getCompilationUnit().getLineMap().getLineNumber(position);}
    checks.add(mapOf("ruleId",r.get("id"),"kind",kind,"passed",passed,"weight",r.get("weight"),"line",line,"message",reason));
   }
   System.out.print(stringify(mapOf("checks",checks)));
  }
 }
 private static boolean same(String a,String b){return a.replace("java.lang.","").replace(" ","").equals(b.replace("java.lang.","").replace(" ",""));}
 private static boolean parameters(ExecutableElement e,List<Object> expected){if(e.getParameters().size()!=expected.size())return false;for(int i=0;i<expected.size();i++)if(!same(e.getParameters().get(i).asType().toString(),String.valueOf(expected.get(i))))return false;return true;}
}
