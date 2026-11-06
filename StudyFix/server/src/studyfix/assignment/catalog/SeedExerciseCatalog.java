package studyfix.assignment.catalog;

import studyfix.assignment.model.Exercise;
import studyfix.assignment.model.ExerciseMode;
import java.util.*;

/** Danh sách bài tập mẫu. Lời giải tham khảo và test ẩn chỉ được giữ ở server. */
public final class SeedExerciseCatalog {
 private SeedExerciseCatalog(){}
 private static Exercise.TestCase t(String in,String out,int weight,boolean hidden){return new Exercise.TestCase(in,out,weight,hidden);}
 private static final String TEMPLATE="import java.util.*;\npublic class Main {\n  public static void main(String[] args) {\n    Scanner sc = new Scanner(System.in);\n    // TODO: implement the solution\n  }\n}\n";
 public static List<Exercise> all(){List<Exercise>x=new ArrayList<>();
 x.add(new Exercise("ARR001","Tính tổng mảng","Mảng","Dễ","Cho n và n số nguyên. In tổng của các phần tử.","Dòng 1: n; dòng 2: n số nguyên","Một số nguyên là tổng các phần tử","1 <= n <= 10000; |a[i]| <= 10^6",TEMPLATE,"Gợi ý 1: duyệt từng phần tử. Gợi ý 2: dùng biến long để tránh tràn số.","import java.util.*; public class Main { public static void main(String[] z){ Scanner s=new Scanner(System.in); int n=s.nextInt(); long sum=0; for(int i=0;i<n;i++)sum+=s.nextInt(); System.out.println(sum); } }",List.of(t("3\n1 2 3\n","6\n",1,false),t("1\n-5\n","-5\n",1,false),t("5\n1 2 3 4 5\n","15\n",2,true))));
 x.add(new Exercise("ARR002","Tổng số chẵn","Mảng","Dễ","Cho n và n số nguyên. In tổng các số chẵn.","n và dãy n số","Tổng các phần tử chẵn","1 <= n <= 10000",TEMPLATE,"Dùng phép chia dư a % 2 == 0.","import java.util.*; public class Main { public static void main(String[] z){Scanner s=new Scanner(System.in);int n=s.nextInt();long sum=0;for(int i=0;i<n;i++){int a=s.nextInt();if(a%2==0)sum+=a;}System.out.println(sum);}}",List.of(t("4\n1 2 3 4\n","6\n",1,false),t("3\n1 3 5\n","0\n",1,false),t("4\n-2 -4 3 1\n","-6\n",2,true))));
 x.add(new Exercise("ARR003","Phần tử lớn nhất","Mảng","Dễ","Đọc n và n số nguyên, in giá trị lớn nhất.","n; dãy số","Một số nguyên","n >= 1",TEMPLATE,"Khởi tạo max bằng phần tử đầu tiên, không phải 0.","import java.util.*; public class Main { public static void main(String[] z){Scanner s=new Scanner(System.in);int n=s.nextInt(),m=s.nextInt();for(int i=1;i<n;i++)m=Math.max(m,s.nextInt());System.out.println(m);}}",List.of(t("4\n-7 -2 -9 -4\n","-2\n",1,false),t("1\n9\n","9\n",1,false),t("5\n1 8 3 8 2\n","8\n",2,true))));
 x.add(new Exercise("ARR004","Đếm số dương","Mảng","Dễ","Đếm các phần tử lớn hơn 0.","n và dãy số","Số lượng phần tử dương","1 <= n <= 10000",TEMPLATE,"Điều kiện là x > 0; số 0 không dương.","import java.util.*; public class Main { public static void main(String[] z){Scanner s=new Scanner(System.in);int n=s.nextInt(),c=0;for(int i=0;i<n;i++)if(s.nextInt()>0)c++;System.out.println(c);}}",List.of(t("5\n-1 0 2 3 -4\n","2\n",1,false),t("3\n-3 -2 -1\n","0\n",1,false),t("4\n1 1 1 1\n","4\n",2,true))));
 x.add(new Exercise("STR001","Đảo ngược chuỗi","Chuỗi","Dễ","Đọc một dòng văn bản và in chuỗi theo thứ tự ký tự ngược lại.","Một dòng UTF-8","Chuỗi đảo ngược","Độ dài <= 10000",TEMPLATE,"Dùng StringBuilder.reverse().", "import java.io.*; public class Main {public static void main(String[] z)throws Exception{BufferedReader b=new BufferedReader(new InputStreamReader(System.in));String s=b.readLine();System.out.println(new StringBuilder(s==null?\"\":s).reverse());}}",List.of(t("studyfix\n","xifyduts\n",1,false),t("abc\n","cba\n",1,false),t("a b c\n","c b a\n",2,true))));
 x.add(new Exercise("STR002","Kiểm tra palindrome","Chuỗi","Dễ","Đọc một dòng, bỏ khoảng trắng ở hai đầu và so sánh không phân biệt hoa thường. In YES nếu đối xứng, ngược lại NO.","Một dòng","YES hoặc NO","Độ dài <= 10000",TEMPLATE,"Chuẩn hóa bằng trim() và toLowerCase(Locale.ROOT), sau đó so sánh với chuỗi đảo.","import java.io.*;import java.util.*;public class Main{public static void main(String[]z)throws Exception{String s=new BufferedReader(new InputStreamReader(System.in)).readLine();s=(s==null?\"\":s.trim().toLowerCase(Locale.ROOT));System.out.println(s.equals(new StringBuilder(s).reverse().toString())?\"YES\":\"NO\");}}",List.of(t("Level\n","YES\n",1,false),t("StudyFix\n","NO\n",1,false),t("abc cba\n","YES\n",2,true))));
 x.add(new Exercise("MATH001","Giai thừa","Toán","Dễ","Cho n (0 <= n <= 20), in n!.","Một số nguyên n","n! dưới dạng số nguyên 64-bit","0 <= n <= 20",TEMPLATE,"0! = 1; dùng long.","import java.util.*;public class Main{public static void main(String[]z){Scanner s=new Scanner(System.in);int n=s.nextInt();long f=1;for(int i=2;i<=n;i++)f*=i;System.out.println(f);}}",List.of(t("0\n","1\n",1,false),t("5\n","120\n",1,false),t("20\n","2432902008176640000\n",2,true))));
 x.add(new Exercise("MATH002","Số nguyên tố","Toán","Trung bình","Cho n, in YES nếu n là số nguyên tố, ngược lại NO.","Một số nguyên n","YES hoặc NO","0 <= n <= 10^9",TEMPLATE,"Kiểm tra ước từ 2 đến khi i*i > n.","import java.util.*;public class Main{public static void main(String[]z){Scanner s=new Scanner(System.in);long n=s.nextLong();boolean p=n>=2;for(long i=2;i*i<=n&&p;i++)if(n%i==0)p=false;System.out.println(p?\"YES\":\"NO\");}}",List.of(t("2\n","YES\n",1,false),t("1\n","NO\n",1,false),t("999983\n","YES\n",2,true))));
 x.add(new Exercise("MATH003","Ước chung lớn nhất","Toán","Dễ","Cho hai số nguyên không âm a, b. In ước chung lớn nhất.","Hai số nguyên a b","Một số nguyên","0 <= a,b <= 10^18; không đồng thời bằng 0",TEMPLATE,"Dùng thuật toán Euclid.","import java.util.*;public class Main{public static void main(String[]z){Scanner s=new Scanner(System.in);long a=s.nextLong(),b=s.nextLong();while(b!=0){long t=a%b;a=b;b=t;}System.out.println(a);}}",List.of(t("12 18\n","6\n",1,false),t("7 0\n","7\n",1,false),t("1000000000000 250000000000\n","250000000000\n",2,true))));
 x.add(new Exercise("SEQ001","Dãy Fibonacci","Vòng lặp","Dễ","Cho n (0 <= n <= 92), in F(n) với F(0)=0, F(1)=1.","Một số nguyên n","F(n)","0 <= n <= 92",TEMPLATE,"Dùng hai biến để cập nhật tuần tự.","import java.util.*;public class Main{public static void main(String[]z){Scanner s=new Scanner(System.in);int n=s.nextInt();long a=0,b=1;for(int i=0;i<n;i++){long t=a+b;a=b;b=t;}System.out.println(a);}}",List.of(t("0\n","0\n",1,false),t("10\n","55\n",1,false),t("92\n","7540113804746346429\n",2,true))));
 x.add(new Exercise("SORT001","Sắp xếp tăng dần","Mảng","Trung bình","Đọc n số nguyên, in chúng theo thứ tự tăng dần trên một dòng, cách nhau một dấu cách.","n; dãy n số","Dãy tăng dần","1 <= n <= 10000",TEMPLATE,"Dùng Arrays.sort().","import java.util.*;public class Main{public static void main(String[]z){Scanner s=new Scanner(System.in);int n=s.nextInt();int[]a=new int[n];for(int i=0;i<n;i++)a[i]=s.nextInt();Arrays.sort(a);for(int i=0;i<n;i++){System.out.print(a[i]);if(i+1<n)System.out.write(32);}System.out.println();}}",List.of(t("4\n4 1 3 2\n","1 2 3 4\n",1,false),t("3\n-1 0 -1\n","-1 -1 0\n",1,false),t("1\n7\n","7\n",2,true))));
 x.add(new Exercise("STACK001","Kiểm tra ngoặc hợp lệ","Stack","Trung bình","Chuỗi chỉ gồm (), [], {}. In YES nếu các ngoặc đóng/mở đúng thứ tự, ngược lại NO.","Một chuỗi ngoặc","YES hoặc NO","Độ dài <= 100000",TEMPLATE,"Dùng stack: ngoặc đóng phải khớp với phần tử đỉnh.","import java.io.*;import java.util.*;public class Main{public static void main(String[]z)throws Exception{String s=new BufferedReader(new InputStreamReader(System.in)).readLine();Deque<Character>d=new ArrayDeque<>();boolean ok=true;for(char c:(s==null?\"\":s).toCharArray()){if(c=='('||c=='['||c=='{')d.push(c);else{if(d.isEmpty()){ok=false;break;}char a=d.pop();if((c==')'&&a!='(')||(c==']'&&a!='[')||(c=='}'&&a!='{')){ok=false;break;}}}System.out.println(ok&&d.isEmpty()?\"YES\":\"NO\");}}",List.of(t("([])\n","YES\n",1,false),t("([)]\n","NO\n",1,false),t("{{{}}}\n","YES\n",2,true))));
 x.add(new Exercise("CLASS001","Đếm từ","Chuỗi","Dễ","Đếm số từ phân tách bởi một hoặc nhiều khoảng trắng. Dòng rỗng có 0 từ.","Một dòng","Số từ","Độ dài <= 100000",TEMPLATE,"Dùng trim; nếu rỗng thì trả 0, ngược lại split theo \\s+.","import java.io.*;public class Main{public static void main(String[]z)throws Exception{String s=new BufferedReader(new InputStreamReader(System.in)).readLine();s=s==null?\"\":s.trim();System.out.println(s.isEmpty()?0:s.split(\"\\\\s+\").length);}}",List.of(t("hello study fix\n","3\n",1,false),t("   \n","0\n",1,false),t("one   two\n","2\n",2,true))));
 x.add(debug("DBG_ARR001","Sửa lỗi: Tính tổng mảng","Mảng","Dễ","Cho n và n số nguyên. In tổng các phần tử.","n; dòng 2 gồm n số nguyên","Một số nguyên là tổng các phần tử","1 <= n <= 10000",TEMPLATE,"Kiểm tra chỉ số vòng lặp: hợp lệ từ 0 đến n-1.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); long sum=0; int[] a=new int[n]; for(int i=0;i<n;i++)a[i]=s.nextInt(); for(int i=0;i<=n;i++)sum+=a[i]; System.out.println(sum); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); long sum=0; for(int i=0;i<n;i++)sum+=s.nextInt(); System.out.println(sum); } }""",
   List.of(t("3\n1 2 3\n","6\n",1,false),t("1\n-5\n","-5\n",1,false),t("4\n2 4 6 8\n","20\n",2,true))));
 x.add(debug("DBG_ARR002","Sửa lỗi: Đếm số dương","Mảng","Dễ","Đếm phần tử lớn hơn 0; số 0 không được tính là số dương.","n và dãy n số","Số lượng phần tử dương","1 <= n <= 10000",TEMPLATE,"Phân biệt lớn hơn 0 với lớn hơn hoặc bằng 0.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(), c=0; for(int i=0;i<n;i++) if(s.nextInt()>=0)c++; System.out.println(c); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(), c=0; for(int i=0;i<n;i++) if(s.nextInt()>0)c++; System.out.println(c); } }""",
   List.of(t("5\n-1 0 2 3 -4\n","2\n",1,false),t("3\n0 0 0\n","0\n",1,false),t("4\n-5 -1 1 7\n","2\n",2,true))));
 x.add(debug("DBG_ARR003","Sửa lỗi: Phần tử lớn nhất","Mảng","Dễ","Đọc n số nguyên và in phần tử lớn nhất, kể cả khi tất cả đều âm.","n; dãy n số","Một số nguyên","1 <= n <= 10000",TEMPLATE,"Khởi tạo max bằng phần tử đầu tiên.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(), max=0; for(int i=0;i<n;i++)max=Math.max(max,s.nextInt()); System.out.println(max); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(), max=s.nextInt(); for(int i=1;i<n;i++)max=Math.max(max,s.nextInt()); System.out.println(max); } }""",
   List.of(t("4\n-7 -2 -9 -4\n","-2\n",1,false),t("1\n-9\n","-9\n",1,false),t("5\n1 8 3 8 2\n","8\n",2,true))));
 x.add(debug("DBG_STR001","Sửa lỗi: Đảo ngược chuỗi","Chuỗi","Dễ","Đọc một dòng và in các ký tự theo thứ tự ngược lại.","Một dòng UTF-8","Chuỗi đảo ngược","Độ dài <= 10000",TEMPLATE,"Không bỏ mất ký tự cuối khi lấy chuỗi con.",
   """
import java.io.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); if(s==null)s=""; System.out.println(new StringBuilder(s.substring(0,Math.max(0,s.length()-1))).reverse()); } }""",
   """
import java.io.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); if(s==null)s=""; System.out.println(new StringBuilder(s).reverse()); } }""",
   List.of(t("studyfix\n","xifyduts\n",1,false),t("a\n","a\n",1,false),t("a b c\n","c b a\n",2,true))));
 x.add(debug("DBG_STR002","Sửa lỗi: Kiểm tra palindrome","Chuỗi","Dễ","Bỏ khoảng trắng hai đầu và so sánh không phân biệt hoa thường. In YES nếu đối xứng, ngược lại NO.","Một dòng","YES hoặc NO","Độ dài <= 10000",TEMPLATE,"Chuẩn hóa chữ hoa/chữ thường trước khi so sánh.",
   """
import java.io.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); s=(s==null?"":s.trim()); System.out.println(s.equals(new StringBuilder(s).reverse().toString())?"YES":"NO"); } }""",
   """
import java.io.*; import java.util.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); s=(s==null?"":s.trim().toLowerCase(Locale.ROOT)); System.out.println(s.equals(new StringBuilder(s).reverse().toString())?"YES":"NO"); } }""",
   List.of(t("Level\n","YES\n",1,false),t("StudyFix\n","NO\n",1,false),t("Madam\n","YES\n",2,true))));
 x.add(debug("DBG_MATH001","Sửa lỗi: Tính giai thừa","Toán","Dễ","Cho n (0 <= n <= 20), in n!.","Một số nguyên n","n! dưới dạng số nguyên 64-bit","0 <= n <= 20",TEMPLATE,"Vòng lặp phải nhân cả n; 0! = 1.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); long f=1; for(int i=2;i<n;i++)f*=i; System.out.println(f); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); long f=1; for(int i=2;i<=n;i++)f*=i; System.out.println(f); } }""",
   List.of(t("0\n","1\n",1,false),t("5\n","120\n",1,false),t("20\n","2432902008176640000\n",2,true))));
 x.add(debug("DBG_MATH002","Sửa lỗi: Số nguyên tố","Toán","Trung bình","Cho n, in YES nếu n là số nguyên tố, ngược lại NO.","Một số nguyên n","YES hoặc NO","0 <= n <= 10^9",TEMPLATE,"Số nguyên tố phải lớn hơn hoặc bằng 2.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); long n=s.nextLong(); boolean p=true; for(long i=2;i*i<=n&&p;i++)if(n%i==0)p=false; System.out.println(p?"YES":"NO"); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); long n=s.nextLong(); boolean p=n>=2; for(long i=2;i*i<=n&&p;i++)if(n%i==0)p=false; System.out.println(p?"YES":"NO"); } }""",
   List.of(t("2\n","YES\n",1,false),t("1\n","NO\n",1,false),t("0\n","NO\n",2,true))));
 x.add(debug("DBG_SEQ001","Sửa lỗi: Dãy Fibonacci","Vòng lặp","Dễ","Cho n (0 <= n <= 92), in F(n) với F(0)=0 và F(1)=1.","Một số nguyên n","F(n)","0 <= n <= 92",TEMPLATE,"Kiểm tra số vòng lặp và giá trị cần in.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); long a=0,b=1; for(int i=0;i<n;i++){long t=a+b;a=b;b=t;} System.out.println(b); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); long a=0,b=1; for(int i=0;i<n;i++){long t=a+b;a=b;b=t;} System.out.println(a); } }""",
   List.of(t("0\n","0\n",1,false),t("10\n","55\n",1,false),t("20\n","6765\n",2,true))));
 x.add(debug("DBG_SORT001","Sửa lỗi: Sắp xếp tăng dần","Mảng","Trung bình","Đọc n số nguyên, sắp xếp tăng dần và in trên một dòng, cách nhau một dấu cách.","n; dãy n số","Dãy tăng dần","1 <= n <= 10000",TEMPLATE,"Kiểm tra biên của vòng lặp xuất kết quả.",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); int[] a=new int[n]; for(int i=0;i<n;i++)a[i]=s.nextInt(); Arrays.sort(a); for(int i=0;i<=n;i++){if(i>0)System.out.write(32);System.out.print(a[i]);} System.out.println(); } }""",
   """
import java.util.*;
public class Main { public static void main(String[] z) { Scanner s=new Scanner(System.in); int n=s.nextInt(); int[] a=new int[n]; for(int i=0;i<n;i++)a[i]=s.nextInt(); Arrays.sort(a); for(int i=0;i<n;i++){if(i>0)System.out.write(32);System.out.print(a[i]);} System.out.println(); } }""",
   List.of(t("4\n4 1 3 2\n","1 2 3 4\n",1,false),t("1\n7\n","7\n",1,false),t("3\n-1 0 -1\n","-1 -1 0\n",2,true))));
 x.add(debug("DBG_STACK001","Sửa lỗi: Ngoặc hợp lệ","Stack","Trung bình","Chuỗi chỉ chứa (), [] và {}. In YES nếu các ngoặc mở/đóng đúng loại và đúng thứ tự; ngược lại NO.","Một chuỗi ngoặc","YES hoặc NO","Độ dài <= 100000",TEMPLATE,"Khi gặp ngoặc đóng, phải đối chiếu đúng loại với ngoặc mở trên đỉnh stack.",
   """
import java.io.*; import java.util.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); Deque<Character>d=new ArrayDeque<>(); boolean ok=true; for(char c:(s==null?"":s).toCharArray()){if(c=='('||c=='['||c=='{')d.push(c);else{if(d.isEmpty()){ok=false;break;}d.pop();}} System.out.println(ok&&d.isEmpty()?"YES":"NO"); } }""",
   """
import java.io.*; import java.util.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); Deque<Character>d=new ArrayDeque<>(); boolean ok=true; for(char c:(s==null?"":s).toCharArray()){if(c=='('||c=='['||c=='{')d.push(c);else{if(d.isEmpty()){ok=false;break;}char a=d.pop();if((c==')'&&a!='(')||(c==']'&&a!='[')||(c=='}'&&a!='{')){ok=false;break;}}} System.out.println(ok&&d.isEmpty()?"YES":"NO"); } }""",
   List.of(t("([])\n","YES\n",1,false),t("([)]\n","NO\n",1,false),t("{{{}}}\n","YES\n",2,true))));
 x.add(debug("DBG_TEXT001","Sửa lỗi: Đếm từ","Chuỗi","Dễ","Đếm số từ phân tách bởi một hoặc nhiều khoảng trắng. Dòng rỗng có 0 từ.","Một dòng","Số từ","Độ dài <= 100000",TEMPLATE,"Xử lý dòng rỗng và nhiều khoảng trắng liên tiếp.",
   """
import java.io.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); s=s==null?"":s.trim(); System.out.println(s.isEmpty()?0:s.split(" ").length); } }""",
   """
import java.io.*;
public class Main { public static void main(String[] z)throws Exception { String s=new BufferedReader(new InputStreamReader(System.in)).readLine(); s=s==null?"":s.trim(); System.out.println(s.isEmpty()?0:s.split("\\s+").length); } }""",
   List.of(t("hello study fix\n","3\n",1,false),t("   \n","0\n",1,false),t("one   two\n","2\n",2,true))));
 return List.copyOf(x);}
 private static Exercise debug(String id,String title,String topic,String difficulty,String statement,String input,String output,String constraints,String template,String hints,String starter,String solution,List<Exercise.TestCase> tests){
   return new Exercise(id,title,topic,difficulty,statement,input,output,constraints,template,hints,solution,ExerciseMode.DEBUG_COLLAB,starter,tests);
 }

}
