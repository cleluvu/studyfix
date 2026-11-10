package studyfix;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.w3c.dom.*;

/** Small, bounded OOXML spreadsheet codec; no macros, formulas, external resources or file extraction. */
final class StudentSpreadsheet {
 private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
 private static final String REL="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
 private static Document xml(byte[] bytes)throws Exception{
  if(bytes==null)throw new IllegalArgumentException("Thiếu thành phần trong file Excel.");
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
 }
 static List<List<String>> read(byte[] bytes){
  if(bytes.length>1048576)throw new IllegalArgumentException("File Excel tối đa 1 MiB.");
  try{
   Map<String,byte[]> entries=new HashMap<>();int total=0,count=0;
   try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=zip.getNextEntry())!=null){if(++count>128)throw new IllegalArgumentException("File Excel có quá nhiều thành phần.");if(e.isDirectory())continue;String name=e.getName();if(name.contains("..")||name.contains("\\")||name.startsWith("/")||entries.containsKey(name))throw new IllegalArgumentException("Đường dẫn trong file Excel không hợp lệ.");byte[] part=zip.readNBytes(8*1024*1024-total+1);total+=part.length;if(total>8*1024*1024)throw new IllegalArgumentException("File Excel giải nén vượt giới hạn.");entries.put(name,part);}}
   var workbook=xml(entries.get("xl/workbook.xml"));var sheets=workbook.getElementsByTagNameNS(NS,"sheet");if(sheets.getLength()==0)throw new IllegalArgumentException("File Excel chưa có bảng dữ liệu.");String id=((Element)sheets.item(0)).getAttributeNS(REL,"id"),target="";
   var relationships=xml(entries.get("xl/_rels/workbook.xml.rels")).getElementsByTagNameNS("*","Relationship");for(int i=0;i<relationships.getLength();i++){var rel=(Element)relationships.item(i);if(id.equals(rel.getAttribute("Id"))){if("External".equals(rel.getAttribute("TargetMode")))throw new IllegalArgumentException("Không nhận bảng Excel liên kết ngoài.");target=rel.getAttribute("Target");}}
   if(target.isEmpty()||target.contains("\\"))throw new IllegalArgumentException("Không tìm thấy bảng dữ liệu đầu tiên.");String sheet=target.startsWith("/xl/")?target.substring(1):java.nio.file.Path.of("xl").resolve(target).normalize().toString().replace('\\','/');if(!sheet.startsWith("xl/")||sheet.contains(".."))throw new IllegalArgumentException("Bảng dữ liệu không hợp lệ.");
   List<String> shared=new ArrayList<>();if(entries.containsKey("xl/sharedStrings.xml")){var strings=xml(entries.get("xl/sharedStrings.xml")).getElementsByTagNameNS(NS,"si");if(strings.getLength()>5000)throw new IllegalArgumentException("Quá nhiều giá trị trong Excel.");for(int i=0;i<strings.getLength();i++)shared.add(text((Element)strings.item(i)));}
   var rows=xml(entries.get(sheet)).getElementsByTagNameNS(NS,"row");List<List<String>> out=new ArrayList<>();
   for(int i=0;i<rows.getLength();i++){var cells=((Element)rows.item(i)).getElementsByTagNameNS(NS,"c");List<String> row=new ArrayList<>();for(int j=0;j<cells.getLength();j++){var cell=(Element)cells.item(j);if(cell.getElementsByTagNameNS(NS,"f").getLength()>0)throw new IllegalArgumentException("Chuyển công thức Excel thành giá trị trước khi nhập.");String address=cell.getAttribute("r").replaceAll("[0-9]","");int column=0;if(address.isEmpty())column=row.size()+1;else for(char ch:address.toCharArray()){if(ch<'A'||ch>'Z')throw new IllegalArgumentException("Ô Excel không hợp lệ.");column=column*26+ch-'A'+1;if(column>16)throw new IllegalArgumentException("File nhập tối đa 16 cột.");}while(row.size()<column)row.add("");String type=cell.getAttribute("t");var values=cell.getElementsByTagNameNS(NS,"v");String value=type.equals("inlineStr")?text(cell):values.getLength()>0?values.item(0).getTextContent():"";if(type.equals("s")){int index=Integer.parseInt(value);if(index<0||index>=shared.size())throw new IllegalArgumentException("Chuỗi Excel không hợp lệ.");value=shared.get(index);}if(value.length()>4096)throw new IllegalArgumentException("Nội dung ô Excel quá dài.");row.set(column-1,value);}
    if(row.stream().anyMatch(v->!v.isBlank()))out.add(row);if(out.size()>101)throw new IllegalArgumentException("Mỗi file tối đa 100 sinh viên.");
   }if(out.size()<2)throw new IllegalArgumentException("File cần tiêu đề và ít nhất một sinh viên.");return out;
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Không đọc được file .xlsx hợp lệ.");}
 }
 private static String text(Element parent){var nodes=parent.getElementsByTagNameNS(NS,"t");StringBuilder out=new StringBuilder();for(int i=0;i<nodes.getLength();i++)out.append(nodes.item(i).getTextContent());return out.toString();}
 private static String escape(String value){return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
 static byte[] write(List<List<String>> rows){
  try{var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){
   add(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
   add(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
   add(zip,"xl/workbook.xml","<workbook xmlns=\""+NS+"\" xmlns:r=\""+REL+"\"><sheets><sheet name=\"Sinh viên\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
   add(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
   StringBuilder sheet=new StringBuilder("<worksheet xmlns=\""+NS+"\"><sheetData>");for(int i=0;i<rows.size();i++){sheet.append("<row r=\"").append(i+1).append("\">");var row=rows.get(i);for(int j=0;j<row.size();j++){int col=j+1;String label="";while(col>0){col--;label=(char)('A'+col%26)+label;col/=26;}sheet.append("<c r=\"").append(label).append(i+1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(row.get(j))).append("</t></is></c>");}sheet.append("</row>");}add(zip,"xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
  }return bytes.toByteArray();}catch(IOException e){throw new IllegalStateException("Không tạo được file Excel.",e);}
 }
 private static void add(ZipOutputStream zip,String name,String xml)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+xml).getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
