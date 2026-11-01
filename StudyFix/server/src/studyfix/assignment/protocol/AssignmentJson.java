package studyfix.assignment.protocol;

import java.util.*;

/** Small dependency-free JSON codec for the assignment channel. */
public final class AssignmentJson {
    private AssignmentJson() {}
    public static String stringify(Object value) { StringBuilder out = new StringBuilder(); write(value, out); return out.toString(); }
    private static void write(Object value, StringBuilder out) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { quote(s, out); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (var entry : map.entrySet()) { if (!first) out.append(','); first = false; quote(String.valueOf(entry.getKey()), out); out.append(':'); write(entry.getValue(), out); }
            out.append('}'); return;
        }
        if (value instanceof Iterable<?> list) { out.append('['); boolean first = true; for (Object item : list) { if (!first) out.append(','); first = false; write(item, out); } out.append(']'); return; }
        quote(String.valueOf(value), out);
    }
    private static void quote(String s, StringBuilder out) {
        out.append('"'); for (int i=0;i<s.length();i++) { char c=s.charAt(i); switch(c) { case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t"); default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); } } } out.append('"');
    }
    public static Object parse(String json) { Parser p = new Parser(json); Object result = p.value(); p.ws(); if (p.i != json.length()) throw new IllegalArgumentException("JSON còn dữ liệu thừa"); return result; }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(String json) { Object v = parse(json); if (!(v instanceof Map<?,?>)) throw new IllegalArgumentException("JSON phải là object"); return (Map<String,Object>)v; }
    private static final class Parser {
        private final String s; private int i; Parser(String s) { this.s=Objects.requireNonNull(s); }
        void ws(){ while(i<s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value(){ ws(); if(i>=s.length()) throw err("Thiếu value"); char c=s.charAt(i); if(c=='{') return object(); if(c=='[') return array(); if(c=='"') return string(); if(c=='t'&&take("true")) return true; if(c=='f'&&take("false")) return false; if(c=='n'&&take("null")) return null; return number(); }
        boolean take(String x){ if(s.startsWith(x,i)){i+=x.length();return true;}return false; }
        Map<String,Object> object(){ i++; LinkedHashMap<String,Object> m=new LinkedHashMap<>(); ws(); if(i<s.length()&&s.charAt(i)=='}'){i++;return m;} while(true){ws();if(i>=s.length()||s.charAt(i)!='"')throw err("Key phải là chuỗi");String k=string();ws();if(i>=s.length()||s.charAt(i++)!=':')throw err("Thiếu :");Object v=value();m.put(k,v);ws();if(i>=s.length())throw err("Thiếu }");char c=s.charAt(i++);if(c=='}')return m;if(c!=',')throw err("Thiếu ,");} }
        List<Object> array(){ i++;ArrayList<Object>a=new ArrayList<>();ws();if(i<s.length()&&s.charAt(i)==']'){i++;return a;}while(true){a.add(value());ws();if(i>=s.length())throw err("Thiếu ]");char c=s.charAt(i++);if(c==']')return a;if(c!=',')throw err("Thiếu ,");} }
        String string(){ i++;StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c!='\\'){if(c<0x20)throw err("Control char trong string");b.append(c);continue;}if(i>=s.length())throw err("Escape không hoàn chỉnh");char e=s.charAt(i++);switch(e){case '"','\\','/'->b.append(e);case 'b'->b.append('\b');case 'f'->b.append('\f');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'u'->{if(i+4>s.length())throw err("Unicode escape thiếu");b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}default->throw err("Escape không hợp lệ");}}throw err("String chưa đóng"); }
        Number number(){int start=i;if(i<s.length()&&s.charAt(i)=='-')i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i<s.length()&&s.charAt(i)=='.'){i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;}if(i<s.length()&&(s.charAt(i)=='e'||s.charAt(i)=='E')){i++;if(i<s.length()&&(s.charAt(i)=='+'||s.charAt(i)=='-'))i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;}if(start==i)throw err("Number không hợp lệ");String n=s.substring(start,i);try{return n.contains(".")||n.contains("e")||n.contains("E")?Double.parseDouble(n):Long.parseLong(n);}catch(NumberFormatException e){throw err("Number không hợp lệ");}}
        IllegalArgumentException err(String m){return new IllegalArgumentException(m+" tại vị trí "+i);}
    }
    public static String text(Map<String,Object> m,String key,String fallback){Object v=m.get(key);return v==null?fallback:String.valueOf(v);}
    public static long number(Map<String,Object> m,String key,long fallback){Object v=m.get(key);return v instanceof Number n?n.longValue():fallback;}
    public static int integer(Map<String,Object> m,String key,int fallback){return (int)number(m,key,fallback);}
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object o){return o instanceof Map<?,?>?(Map<String,Object>)o:Map.of();}
    @SuppressWarnings("unchecked") public static List<Object> list(Object o){return o instanceof List<?>?(List<Object>)o:List.of();}
    public static Map<String,Object> mapOf(Object... pairs){LinkedHashMap<String,Object> m=new LinkedHashMap<>();for(int i=0;i+1<pairs.length;i+=2)m.put(String.valueOf(pairs[i]),pairs[i+1]);return m;}
}
