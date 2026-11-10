package studyfix.assignment.persistence;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import studyfix.assignment.persistence.ProblemBuilderRepository.Failure;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Template files are inert text. Uploaded build scripts are never executed. */
public final class ProblemTemplate {
 private ProblemTemplate(){}
 public static String path(String name){
  if(name==null||name.length()>160||!name.matches("[A-Za-z0-9_./-]+")||name.startsWith("/")||name.contains("\\"))throw new Failure("INVALID_REQUEST","Đường dẫn file không hợp lệ.");
  for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new Failure("INVALID_REQUEST","Đường dẫn file không an toàn.");
  if(!name.matches(".*\\.(java|md|txt|json|xml|properties)$"))throw new Failure("INVALID_REQUEST","Template chỉ nhận file văn bản Java, Markdown, TXT, JSON, XML, properties.");
  return name;
 }
 public static void validate(Map<String,Object> template){
  if(!template.containsKey("files"))return;
  if(!(template.get("files") instanceof List<?> files)||files.isEmpty()||files.size()>32)throw new Failure("INVALID_REQUEST","Template cần 1–32 file.");
  Set<String> paths=new HashSet<>();int total=0;
  for(Object item:files){if(!(item instanceof Map<?,?>))throw new Failure("INVALID_REQUEST","Thông tin file không hợp lệ.");Map<String,Object> file=map(item);String name=path(text(file,"path",""));if(!paths.add(name.toLowerCase(Locale.ROOT)))throw new Failure("INVALID_REQUEST","Trùng đường dẫn file.");if(!(file.get("content") instanceof String content))throw new Failure("INVALID_REQUEST","Nội dung file phải là văn bản.");total+=content.getBytes(StandardCharsets.UTF_8).length;if(total>64*1024)throw new Failure("INVALID_REQUEST","Template giải nén vượt 64 KiB.");if(!Set.of("PUBLIC","TEACHER","JUDGE").contains(text(file,"visibility","PUBLIC")))throw new Failure("INVALID_REQUEST","Quyền xem file không hợp lệ.");if(!(file.get("editable") instanceof Boolean))throw new Failure("INVALID_REQUEST","Thiếu quyền sửa file.");if(!"PUBLIC".equals(text(file,"visibility","PUBLIC"))&&Boolean.TRUE.equals(file.get("editable")))throw new Failure("INVALID_REQUEST","File ẩn không được cho sinh viên chỉnh sửa.");}
  for(String candidate:paths)for(String other:paths)if(candidate.startsWith(other+"/"))throw new Failure("INVALID_REQUEST","Đường dẫn file trùng với thư mục chứa file khác.");
 }
 public static List<Map<String,Object>> unzip(byte[] bytes){
  validateArchive(bytes,100);
  List<Map<String,Object>> files=new ArrayList<>();Set<String> seen=new HashSet<>();int total=0,entries=0;
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
   ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(++entries>100)throw new Failure("INVALID_REQUEST","ZIP quá nhiều entry.");String name=entry.getName();if(entry.isDirectory()){path(name.replaceAll("/$","")+"/directory.txt");if(zip.readNBytes(1).length!=0)throw new Failure("INVALID_REQUEST","Entry thư mục không được chứa dữ liệu.");continue;}name=path(name);if(!seen.add(name.toLowerCase(Locale.ROOT)))throw new Failure("INVALID_REQUEST","ZIP có đường dẫn trùng.");if(files.size()>=32)throw new Failure("INVALID_REQUEST","ZIP tối đa 32 file.");byte[] data=zip.readNBytes(64*1024-total+1);total+=data.length;if(total>64*1024)throw new Failure("INVALID_REQUEST","ZIP giải nén vượt 64 KiB.");String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(data)).toString();if(content.indexOf(0)>=0)throw new Failure("INVALID_REQUEST","ZIP chứa file nhị phân.");boolean hidden=name.startsWith("tests/")||name.contains("/test/")||name.startsWith("teacher/");files.add(mapOf("path",name,"content",content,"visibility",hidden?"JUDGE":"PUBLIC","editable",!hidden&&name.endsWith(".java")));zip.closeEntry();}
  }catch(IOException|IllegalArgumentException e){throw new Failure("INVALID_REQUEST","ZIP lỗi hoặc file không phải UTF-8.");}
  if(files.isEmpty())throw new Failure("INVALID_REQUEST","ZIP không chứa file template.");validate(mapOf("files",files));return files;
 }
 public static void validateArchive(byte[] bytes,int maxEntries){
  // Central-directory Unix mode is not exposed by java.util.zip.ZipEntry.
  var buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);int end=-1;
  for(int i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--)if(buffer.getInt(i)==0x06054b50&&i+22+Short.toUnsignedInt(buffer.getShort(i+20))==bytes.length){end=i;break;}
  if(end<0||buffer.getShort(end+4)!=0||buffer.getShort(end+6)!=0)throw new Failure("INVALID_REQUEST","ZIP thiếu central directory hoặc dùng nhiều volume.");
  int count=Short.toUnsignedInt(buffer.getShort(end+10));long offset=Integer.toUnsignedLong(buffer.getInt(end+16)),size=Integer.toUnsignedLong(buffer.getInt(end+12));
  if(count>maxEntries||offset+size>end)throw new Failure("INVALID_REQUEST","ZIP64 hoặc central directory không hợp lệ.");int position=(int)offset;
  for(int index=0;index<count;index++){if(position<0||position+46>end||buffer.getInt(position)!=0x02014b50)throw new Failure("INVALID_REQUEST","ZIP central directory lỗi.");int mode=(buffer.getInt(position+38)>>>16)&0170000;if(mode==0120000)throw new Failure("INVALID_REQUEST","ZIP không được chứa symbolic link.");int length=46+Short.toUnsignedInt(buffer.getShort(position+28))+Short.toUnsignedInt(buffer.getShort(position+30))+Short.toUnsignedInt(buffer.getShort(position+32));if(position+length>end)throw new Failure("INVALID_REQUEST","ZIP central entry bị cắt.");position+=length;}
  if(position!=offset+size)throw new Failure("INVALID_REQUEST","ZIP central directory không khớp.");
 }
}
