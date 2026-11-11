package studyfix.assignment.service;

import studyfix.Identity;
import studyfix.assignment.model.Exercise;
import studyfix.assignment.persistence.*;
import java.sql.SQLException;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Builder endpoints use the existing authenticated assignment transport. */
public final class ProblemBuilderService implements AutoCloseable {
 private final ProblemBuilderWorkflow workflow;
 private final ProblemBuilderRepository drafts;
 private final studyfix.assignment.grading.DockerJavaSandbox sandbox;
 private final java.util.concurrent.Semaphore compileSlots=new java.util.concurrent.Semaphore(1);
 private final ProblemBuilderAssets assets;private final AssignmentRepository repo;private final QuestionBankRepository bank;
 public ProblemBuilderService(AssignmentRepository repo,QuestionBankRepository bank,String image)throws SQLException{this(repo,bank,image,who->List.of(mapOf("id",who.userId(),"name",who.displayName())));}
 public ProblemBuilderService(AssignmentRepository repo,QuestionBankRepository bank,String image,ProblemBuilderWorkflow.Directory directory)throws SQLException{this.repo=repo;this.bank=bank;sandbox=new studyfix.assignment.grading.DockerJavaSandbox(image,2,45);drafts=new ProblemBuilderRepository(repo);assets=new ProblemBuilderAssets(repo,drafts);workflow=new ProblemBuilderWorkflow(repo,drafts,assets,image,directory);}
 public Map<String,Object> upload(Identity who,String draft,String name,byte[] bytes)throws SQLException{return assets.upload(who,draft,name,bytes);}
 public ProblemBuilderAssets.Asset asset(Identity who,String id)throws SQLException{return assets.get(who,id);}
 public Map<String,Object> handle(Map<String,Object> req,Identity who)throws SQLException{
  String request=text(req,"requestId","");try{
   if(who.role()!=Identity.Role.TEACHER)throw new ProblemBuilderRepository.Failure("FORBIDDEN","Chỉ giáo viên được dùng Problem Builder.");
   String type=text(req,"type",""),id=text(req,"draftId","");Map<String,Object> data=workflow.handle(type,req,who);if(data!=null)return mapOf("status","OK","requestId",request,"data",data);
   switch(type){
    case "BUILDER_CAPABILITIES"->data=mapOf("schemaVersion",2,"draftStorage","server","steps",7,"implementedSteps",List.of(1,2,3,4,5,6,7),"structures",mapOf("PROGRAM",true,"METHOD",true,"CLASS",true),"compile",true,"validation",true,"publish",true,"upload",true,"maxDraftBytes",96*1024,"javaTarget",17,"testRunner","TRUSTED_SNIPPET","structureGrading",mapOf("engine","JAVAC_AST","schemaVersion",1,"maxRules",100),"junit",false,"customChecker",false);
    case "BUILDER_LIST"->data=mapOf("drafts",drafts.list(who,integer(req,"page",1)));
    case "BUILDER_GET"->data=mapOf("draft",drafts.get(who,id),"compile",drafts.latestCheck(who,id,"COMPILE"),"workflow",workflow.status(who,id));
    case "BUILDER_COMPILE"->{Map<String,Object> old=drafts.get(who,id);if(integer(req,"expectedRevision",-1)!=integer(old,"revision",0))throw new ProblemBuilderRepository.Failure("CONFLICT","Bản nháp đã thay đổi, hãy mở lại.");if(!compileSlots.tryAcquire())throw new ProblemBuilderRepository.Failure("BUSY","Đang biên dịch template khác, vui lòng thử lại.");try{Map<String,Object> template=map(map(old.get("payload")).get("template"));List<Map<String,Object>> files=new ArrayList<>();for(Object item:list(template.get("files")))files.add(map(item));if(files.isEmpty())files.add(mapOf("path","Main.java","content",text(template,"source",""),"visibility","PUBLIC","editable",true));Map<String,Object> result=new LinkedHashMap<>(sandbox.compileTemplate(files));result.put("contentHash",old.get("contentHash"));drafts.recordCheck(who,id,text(old,"contentHash",""),"COMPILE",result);data=mapOf("compile",result);}catch(java.io.IOException e){throw new ProblemBuilderRepository.Failure("SANDBOX_UNAVAILABLE","Không thể mở môi trường biên dịch cách ly.");}finally{compileSlots.release();}}
    case "BUILDER_CREATE"->{String base=text(req,"baseExerciseId","");int revision=0;Map<String,Object> initial=map(req.get("payload"));if(!base.isBlank()){
      Map<String,Object> meta=bank.info(base,who);if(!Boolean.TRUE.equals(meta.get("canManage")))throw new ProblemBuilderRepository.Failure("FORBIDDEN","Không được sửa đề của giáo viên khác.");Exercise e=repo.getExercise(base);revision=integer(meta,"revision",0);List<Map<String,Object>> tests=new ArrayList<>();for(Exercise.TestCase t:e.tests())tests.add(mapOf("input",t.input(),"expectedOutput",t.expected(),"weight",t.weight(),"hidden",t.hidden()));
      var ojConfig=new OjRepository(repo).config(base);if(ojConfig!=null){tests.clear();int n=0;for(var t:e.tests())tests.add(mapOf("id","TC"+(++n),"input",t.input(),"expectedOutput",t.expected(),"weight",t.weight()/1000000d,"hidden",t.hidden(),"group","Basic"));}else{int n=0,total=e.tests().stream().mapToInt(Exercise.TestCase::weight).sum();tests.clear();for(var t:e.tests())tests.add(mapOf("id","TC"+(++n),"input",t.input(),"expectedOutput",t.expected(),"weight",Math.round(100000000d*t.weight()/Math.max(1,total))/1000000d,"hidden",t.hidden(),"group","Basic"));}
      initial=mapOf("schemaVersion",1,"code",meta.get("displayCode"),"title",e.title(),"topic",e.topic(),"javaStructure",e.javaStructure(),"difficulty",e.difficulty(),"visibility","PUBLIC","bankScope",meta.get("bankScope"),"weekIds",List.of(),"summary",e.title(),"learningOutcome","Hoàn thành yêu cầu của đề bài.","estimatedMinutes",60,"statement",mapOf("description",studyfix.assignment.grading.StructureRules.withoutGeneratedStatement(e.statement()),"inputFormat",e.inputFormat(),"outputFormat",e.outputFormat(),"constraints",e.constraints()),"template",mapOf("mainClass",e.entryFile().replace(".java",""),"entryFile",e.entryFile(),"source",e.template()),"testGroups",List.of("Basic","Edge","Performance"),"tests",tests,"grading",mapOf("checker",ojConfig==null?"TOKEN":text(ojConfig,"checker","EXACT"),"maxScore",100,"javaVersion",17,"timeLimitMs",ojConfig==null?3000:integer(ojConfig,"time_limit_ms",3000),"memoryLimitMb",ojConfig==null?256:integer(ojConfig,"memory_limit_mb",256),"outputLimitKb",ojConfig==null?2048:integer(ojConfig,"output_limit_kb",2048),"testDelivery",ojConfig==null?"stdin":text(ojConfig,"test_delivery","stdin"),"partialScoring",ojConfig==null||!Boolean.FALSE.equals(ojConfig.get("partial_scoring")),"structure",ojConfig==null?Map.of():map(ojConfig.get("structure_rules"))),"reference",e.referenceSolution(),"incorrectSample",e.template());
      var snapshot=workflow.snapshot(base);if(!snapshot.isEmpty()){initial=new LinkedHashMap<>(snapshot);initial.put("bankScope",meta.get("bankScope"));}
     }data=mapOf("draft",drafts.create(who,text(req,"requestKey",""),base,revision,initial));}
    case "BUILDER_SAVE"->{if(!(req.get("payload") instanceof Map<?,?>))throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Thiếu dữ liệu bản nháp.");Map<String,Object> old=drafts.get(who,id);if(integer(req,"currentStep",1)!=integer(old,"currentStep",1))throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Dùng thao tác chuyển bước để kiểm tra nội dung.");data=mapOf("draft",drafts.save(who,id,integer(req,"expectedRevision",-1),integer(req,"currentStep",1),map(req.get("payload"))));}
    case "BUILDER_ADVANCE"->{Map<String,Object> old=drafts.get(who,id);int target=integer(req,"targetStep",0),current=integer(old,"currentStep",1);if(integer(req,"expectedRevision",-1)!=integer(old,"revision",0))throw new ProblemBuilderRepository.Failure("CONFLICT","Bản nháp đã thay đổi, hãy mở lại.");if(target<1||target>7||target>current+1)throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Chuyển từng bước từ 1 đến 7.");if(target>current){BuilderProblem.require(map(old.get("payload")),Math.min(current,5));if(current==6){var validation=map(workflow.status(who,id).get("validation"));if(!Boolean.TRUE.equals(validation.get("passed"))||!old.get("contentHash").equals(validation.get("contentHash")))throw new ProblemBuilderRepository.Failure("VALIDATION_REQUIRED","Cần kiểm định đạt cho nội dung hiện tại trước khi xuất bản.");}}data=mapOf("draft",drafts.save(who,id,integer(old,"revision",0),target,map(old.get("payload"))));}
    case "BUILDER_TEMPLATE_IMPORT"->{Map<String,Object> old=drafts.get(who,id);var asset=assets.get(who,text(req,"assetId",""));if(!asset.draftId().equals(id)||!asset.media().equals("application/zip"))throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Template ZIP required for this draft");Map<String,Object> payload=new LinkedHashMap<>(map(old.get("payload"))),template=new LinkedHashMap<>(map(payload.get("template")));template.put("files",ProblemTemplate.unzip(asset.bytes()));payload.put("template",template);data=mapOf("draft",drafts.save(who,id,integer(req,"expectedRevision",-1),integer(old,"currentStep",1),payload));}
    case "BUILDER_ASSETS"->data=mapOf("assets",assets.list(who,id));
    case "BUILDER_HISTORY"->data=mapOf("history",drafts.history(who,id,integer(req,"beforeRevision",Integer.MAX_VALUE)));
    case "BUILDER_RESTORE"->data=mapOf("draft",drafts.restore(who,id,integer(req,"expectedRevision",-1),integer(req,"sourceRevision",0)));
    case "BUILDER_DELETE_DRAFT"->{drafts.delete(who,id,integer(req,"expectedRevision",-1));data=Map.of();}
    case "BUILDER_SUGGEST_CODE"->data=mapOf("code",drafts.suggestCode(who));
    default->throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Thao tác Builder chưa được hỗ trợ.");
   }
   return mapOf("status","OK","requestId",request,"data",data);
  }catch(ProblemBuilderRepository.Failure e){return mapOf("status","ERROR","requestId",request,"errorCode",e.code,"message",e.getMessage());}
 }
 @Override public void close(){workflow.close();}
}
