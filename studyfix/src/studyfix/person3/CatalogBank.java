package studyfix.person3;

import java.util.*;
import java.util.function.Function;

/**
 * 100 đề stdin/stdout: IO, STRING, CLASS, STACK, QUEUE, TREE.
 * Test = edge viết tay + random seed cố định; stdout lấy từ oracle Java.
 */
public final class CatalogBank {
    public record Draft(String topic, String code, String title, String statement, int maxScore, String tests) { }

    private CatalogBank() { }

    public static List<Draft> build() {
        List<Draft> all = new ArrayList<>(100);
        io(all);
        string(all);
        clazz(all);
        stack(all);
        queue(all);
        tree(all);
        if (all.size() != 100) {
            throw new IllegalStateException("Catalog phải đúng 100 đề, đang " + all.size());
        }
        return List.copyOf(all);
    }

    private static void io(List<Draft> all) {
        all.add(prob("IO", "IO01", "Tong n so", """
                Nhap n (0..40). Dong sau: n so nguyen. In tong.
                Vi du: 3 / 1 2 3 -> 6
                """, ins -> {
            Scanner sc = sc(ins);
            int n = sc.nextInt(), s = 0;
            for (int i = 0; i < n; i++) s += sc.nextInt();
            return String.valueOf(s);
        }, "0", "1\n-5", "3\n1 2 3", "5\n0 0 0 0 0", randArr(IO01, 8, -20, 20)));

        all.add(prob("IO", "IO02", "Tich n so", """
                Nhap n (0..8). Dong sau n so (-5..5). In tich. n=0 in 1.
                """, ins -> {
            Scanner sc = sc(ins);
            int n = sc.nextInt(), p = 1;
            for (int i = 0; i < n; i++) p *= sc.nextInt();
            return String.valueOf(p);
        }, "0", "1\n0", "2\n-2 4", "3\n1 1 1", randArr(IO02, 5, -5, 5)));

        all.add(prob("IO", "IO03", "Min mang", """
                Nhap n (1..40) roi n so. In gia tri nho nhat.
                """, ins -> minMax(ins, true),
                "1\n9", "3\n5 -1 5", "4\n0 0 0 0", randArr(IO03, 7, -50, 50)));

        all.add(prob("IO", "IO04", "Max mang", """
                Nhap n (1..40) roi n so. In gia tri lon nhat.
                """, ins -> minMax(ins, false),
                "1\n-3", "3\n1 9 1", randArr(IO04, 6, -30, 30)));

        all.add(prob("IO", "IO05", "Min va max", """
                Nhap n (1..40) roi n so. In: min max
                """, ins -> {
            int[] a = readArr(ins);
            int mn = a[0], mx = a[0];
            for (int v : a) { mn = Math.min(mn, v); mx = Math.max(mx, v); }
            return mn + " " + mx;
        }, "1\n4", "5\n3 1 4 1 5", randArr(IO05, 9, -10, 10)));

        all.add(prob("IO", "IO06", "Dem so duong", """
                Nhap n roi n so. In so luong phan tu > 0.
                """, ins -> {
            int[] a = readArr(ins); int c = 0;
            for (int v : a) if (v > 0) c++;
            return String.valueOf(c);
        }, "0", "4\n-1 0 2 3", "3\n0 0 0", randArr(IO06, 10, -5, 5)));

        all.add(prob("IO", "IO07", "Dem so lan x", """
                Dong 1: n x. Dong 2: n so. In so lan x xuat hien.
                """, ins -> {
            Scanner sc = sc(ins);
            int n = sc.nextInt(), x = sc.nextInt(), c = 0;
            for (int i = 0; i < n; i++) if (sc.nextInt() == x) c++;
            return String.valueOf(c);
        }, "0 3\n", "3 1\n1 2 1", "4 0\n0 0 1 0", "5 9\n1 2 3 4 5"));

        all.add(prob("IO", "IO08", "Lon thu hai", """
                Nhap n (2..40) roi n so. In gia tri lon thu hai (sau max).
                Neu moi phan tu bang nhau, in chinh gia tri do.
                """, CatalogBank::secondMax,
                "2\n1 2", "3\n5 5 5", "4\n9 1 9 3", randArr(IO08, 8, 0, 20)));

        all.add(prob("IO", "IO09", "Dao mang", """
                Nhap n roi n so. In mang dao nguoc, cach nhau 1 khoang trang.
                n=0 in dong rong (khong in gi).
                """, ins -> {
            int[] a = readArr(ins);
            StringBuilder sb = new StringBuilder();
            for (int i = a.length - 1; i >= 0; i--) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(a[i]);
            }
            return sb.toString();
        }, "0", "1\n7", "4\n1 2 3 4", randArr(IO09, 6, 1, 9)));

        all.add(prob("IO", "IO10", "Mang tang?", """
                Nhap n roi n so. In YES neu khong giam (a[i]<=a[i+1]), nguoc lai NO.
                n<=1 in YES.
                """, ins -> {
            int[] a = readArr(ins);
            for (int i = 1; i < a.length; i++) if (a[i] < a[i - 1]) return "NO";
            return "YES";
        }, "0", "1\n5", "3\n1 2 2", "3\n1 3 2"));

        all.add(prob("IO", "IO11", "Tong so chan", """
                Nhap n roi n so. In tong cac so chan.
                """, ins -> {
            int[] a = readArr(ins); int s = 0;
            for (int v : a) if (v % 2 == 0) s += v;
            return String.valueOf(s);
        }, "0", "3\n1 3 5", "4\n2 -2 3 4", randArr(IO11, 7, -10, 10)));

        all.add(prob("IO", "IO12", "So le cuoi", """
                Nhap n roi n so. In so le cuoi cung; neu khong co in NONE.
                """, ins -> {
            int[] a = readArr(ins);
            for (int i = a.length - 1; i >= 0; i--) if (a[i] % 2 != 0) return String.valueOf(a[i]);
            return "NONE";
        }, "0", "3\n2 4 6", "4\n1 2 3 4", "3\n8 7 6"));

        all.add(prob("IO", "IO13", "Dem so 0", """
                Nhap n roi n so. In so luong phan tu bang 0.
                """, ins -> {
            int[] a = readArr(ins); int c = 0;
            for (int v : a) if (v == 0) c++;
            return String.valueOf(c);
        }, "0", "3\n0 1 0", randArr(IO13, 8, -1, 1)));

        all.add(prob("IO", "IO14", "Do lech max-min", """
                Nhap n (1..40) roi n so. In max-min.
                """, ins -> {
            int[] a = readArr(ins);
            int mn = a[0], mx = a[0];
            for (int v : a) { mn = Math.min(mn, v); mx = Math.max(mx, v); }
            return String.valueOf(mx - mn);
        }, "1\n10", "3\n-5 0 5", randArr(IO14, 6, -20, 20)));

        all.add(prob("IO", "IO15", "Tong dau cuoi", """
                Nhap n (1..40) roi n so. In a[1]+a[n] (1-index).
                """, ins -> {
            int[] a = readArr(ins);
            return String.valueOf(a[0] + a[a.length - 1]);
        }, "1\n4", "3\n10 0 5", randArr(IO15, 5, -9, 9)));

        all.add(prob("IO", "IO16", "Cong den -1", """
                Nhap day so, dung khi gap -1 (khong cong -1). In tong. Co the day rong ngay -1.
                """, ins -> {
            Scanner sc = sc(ins);
            int s = 0;
            while (sc.hasNextInt()) {
                int v = sc.nextInt();
                if (v == -1) break;
                s += v;
            }
            return String.valueOf(s);
        }, "-1", "1 -1", "1 2 3 -1", "0 0 -1", "5 5 5 5 -1"));
    }

    private static void string(List<Draft> all) {
        all.add(prob("STRING", "ST01", "Dao chuoi", """
                Nhap mot dong. In chuoi dao nguoc.
                """, ins -> new StringBuilder(ins.stripTrailing()).reverse().toString(),
                "", "a", "ab", "hello", "A1 b"));

        all.add(prob("STRING", "ST02", "Doi xung", """
                Mot dong. In YES neu doi xung, NO neu khong.
                """, ins -> {
            String s = ins.stripTrailing();
            return s.contentEquals(new StringBuilder(s).reverse()) ? "YES" : "NO";
        }, "", "a", "aa", "aba", "ab", "AbA"));

        all.add(prob("STRING", "ST03", "Dem nguyen am", """
                Mot dong. Dem aeiouAEIOU.
                """, ins -> {
            int c = 0;
            for (char ch : ins.toCharArray()) if ("aeiouAEIOU".indexOf(ch) >= 0) c++;
            return String.valueOf(c);
        }, "", "xyz", "aeiou", "Hello", "AEIOUaeiou"));

        all.add(prob("STRING", "ST04", "In hoa", "Mot dong. In uppercase.\n",
                ins -> ins.stripTrailing().toUpperCase(Locale.ROOT),
                "", "a", "AbC 12", "java"));

        all.add(prob("STRING", "ST05", "In thuong", "Mot dong. In lowercase.\n",
                ins -> ins.stripTrailing().toLowerCase(Locale.ROOT),
                "", "A", "AbC", "JAVA"));

        all.add(prob("STRING", "ST06", "Dem tu", """
                Mot dong. Dem so tu (tach khoang trang). Dong trang -> 0.
                """, ins -> {
            String s = ins.strip();
            return s.isEmpty() ? "0" : String.valueOf(s.split("\\s+").length);
        }, "", "one", "one two", "  a   b  c ", "hello world java"));

        all.add(prob("STRING", "ST07", "Do dai", "Mot dong (co the rong). In length.\n",
                ins -> String.valueOf(ins.stripTrailing().length()),
                "", "a", "abc", "a b"));

        all.add(prob("STRING", "ST08", "Dem chu so", """
                Mot dong. Dem ky tu '0'..'9'.
                """, ins -> {
            int c = 0;
            for (char ch : ins.toCharArray()) if (ch >= '0' && ch <= '9') c++;
            return String.valueOf(c);
        }, "", "abc", "a1b2", "2026 year", "000"));

        all.add(prob("STRING", "ST09", "La so nguyen?", """
                Mot token (khong space). YES neu toan chu so, co the co dau - o dau. Khong rong.
                """, ins -> {
            String s = ins.strip();
            return s.matches("-?\\d+") ? "YES" : "NO";
        }, "0", "-3", "12", "01", "+", "-"));

        all.add(prob("STRING", "ST10", "Anagram", """
                Hai dong. YES neu la anagram (khong ke hoa thuong, khong ke space), NO neu khong.
                """, CatalogBank::anagram,
                "ab\nba", "Listen\nSilent", "a\nb", "aa\na", "abc\ncb a"));

        all.add(prob("STRING", "ST11", "Chuoi con", """
                Dong 1: s. Dong 2: t. YES neu t la substring cua s.
                """, ins -> {
            String[] p = ins.split("\n", 2);
            String s = p[0].stripTrailing();
            String t = p.length > 1 ? p[1].stripTrailing() : "";
            return s.contains(t) ? "YES" : "NO";
        }, "hello\nll", "hello\nhello", "hello\n", "abc\nx", "aaa\naa"));

        all.add(prob("STRING", "ST12", "Space thanh gach", """
                Mot dong. Doi moi space thanh _.
                """, ins -> ins.stripTrailing().replace(' ', '_'),
                "", "a", "a b", "  x  y"));

        all.add(prob("STRING", "ST13", "So ky tu khac nhau", """
                Mot dong. Dem so ky tu phan biet (phan biet hoa thuong, ke space).
                """, ins -> {
            String s = ins.stripTrailing();
            Set<Character> set = new HashSet<>();
            for (char ch : s.toCharArray()) set.add(ch);
            return String.valueOf(set.size());
        }, "", "a", "aa", "ab", "aA", "a a"));

        all.add(prob("STRING", "ST14", "Dao tung tu", """
                Mot dong. Dao chu cai moi tu, giu thu tu tu. Nhieu space giu 1 space khi in (join 1 space).
                Dong trang in rong.
                """, ins -> {
            String s = ins.strip();
            if (s.isEmpty()) return "";
            String[] w = s.split("\\s+");
            for (int i = 0; i < w.length; i++) w[i] = new StringBuilder(w[i]).reverse().toString();
            return String.join(" ", w);
        }, "", "ab", "ab cd", "hello world"));

        all.add(prob("STRING", "ST15", "Dem chu hoa", """
                Mot dong. Dem 'A'..'Z'.
                """, ins -> {
            int c = 0;
            for (char ch : ins.toCharArray()) if (ch >= 'A' && ch <= 'Z') c++;
            return String.valueOf(c);
        }, "", "abc", "Abc", "ABC", "A1B2c"));

        all.add(prob("STRING", "ST16", "Xoa nguyen am", """
                Mot dong. Xoa aeiouAEIOU, giu ky tu khac.
                """, ins -> {
            StringBuilder sb = new StringBuilder();
            for (char ch : ins.stripTrailing().toCharArray())
                if ("aeiouAEIOU".indexOf(ch) < 0) sb.append(ch);
            return sb.toString();
        }, "", "a", "xyz", "Hello", "AEIOU"));

        all.add(prob("STRING", "ST17", "Ky tu dau", """
                Mot dong. In ky tu khac space dau tien; neu khong co in EMPTY.
                """, ins -> {
            for (char ch : ins.stripTrailing().toCharArray()) if (ch != ' ') return String.valueOf(ch);
            return "EMPTY";
        }, "", "   ", " a", "z", "  *x"));

        all.add(prob("STRING", "ST18", "Doi hoa thuong", """
                Mot dong. Doi hoa thanh thuong va nguoc lai; ky tu khac giu nguyen.
                """, ins -> {
            StringBuilder sb = new StringBuilder();
            for (char ch : ins.stripTrailing().toCharArray()) {
                if (Character.isUpperCase(ch)) sb.append(Character.toLowerCase(ch));
                else if (Character.isLowerCase(ch)) sb.append(Character.toUpperCase(ch));
                else sb.append(ch);
            }
            return sb.toString();
        }, "", "a", "A", "AbC123", "Java"));
    }

    private static void clazz(List<Draft> all) {
        all.add(prob("CLASS", "CL01", "Counter", """
                Mo phong class Counter, bat dau 0.
                Dong 1: q. q dong lenh: INC, DEC, GET, RESET.
                GET in gia tri hien tai. Khac khong in.
                """, CatalogBank::counter,
                "1\nGET", "3\nINC\nINC\nGET", "4\nINC\nDEC\nDEC\nGET", "3\nINC\nRESET\nGET"));

        all.add(prob("CLASS", "CL02", "Tai khoan", """
                So du ban dau 0. q lenh: DEPOSIT x, WITHDRAW x, BALANCE.
                WITHDRAW neu khong du in ERROR, khong tru. Du thi tru va in OK.
                BALANCE in so du. DEPOSIT khong in.
                """, CatalogBank::bank,
                "1\nBALANCE", "3\nDEPOSIT 10\nWITHDRAW 3\nBALANCE",
                "2\nWITHDRAW 1\nBALANCE", "4\nDEPOSIT 5\nWITHDRAW 5\nWITHDRAW 1\nBALANCE"));

        all.add(prob("CLASS", "CL03", "Cong tac", """
                Trang thai OFF. q lenh: ON, OFF, TOGGLE, STATE.
                STATE in ON hoac OFF.
                """, CatalogBank::swt,
                "1\nSTATE", "2\nON\nSTATE", "2\nTOGGLE\nSTATE", "4\nON\nTOGGLE\nTOGGLE\nSTATE"));

        all.add(prob("CLASS", "CL04", "Hinh chu nhat", """
                w=0 h=0. SETW x, SETH y, AREA in w*h, PERIMETER in 2*(w+h).
                """, CatalogBank::rect,
                "1\nAREA", "3\nSETW 3\nSETH 4\nAREA", "3\nSETW 3\nSETH 4\nPERIMETER", "2\nSETW 5\nAREA"));

        all.add(prob("CLASS", "CL05", "Diem 2D", """
                Diem (0,0). MOVEX dx, MOVEY dy, X in x, Y in y, DIST2 in x*x+y*y.
                """, CatalogBank::point,
                "1\nDIST2", "3\nMOVEX 3\nMOVEY 4\nDIST2", "2\nMOVEX -1\nX", "3\nMOVEY 5\nMOVEY -5\nY"));

        all.add(prob("CLASS", "CL06", "Trung binh", """
                Danh sach diem rong. ADD x, AVG in trung binh lam tron xuong (integer);
                neu chua co diem in 0. COUNT in so diem.
                """, CatalogBank::avg,
                "1\nAVG", "1\nCOUNT", "4\nADD 10\nADD 5\nAVG\nCOUNT", "3\nADD 1\nADD 2\nAVG"));

        all.add(prob("CLASS", "CL07", "MinMax tracker", """
                UPDATE x (nhieu lan). GETMIN GETMAX. Neu chua UPDATE, in EMPTY.
                """, CatalogBank::minmaxT,
                "1\nGETMIN", "3\nUPDATE 4\nGETMIN\nGETMAX",
                "4\nUPDATE 9\nUPDATE 1\nUPDATE 3\nGETMIN", "2\nUPDATE 0\nGETMAX"));

        all.add(prob("CLASS", "CL08", "Dimmer", """
                Muc 0 (0..100). SET x (clamp 0..100), INC, DEC, GET.
                """, CatalogBank::dimmer,
                "1\nGET", "2\nSET 50\nGET", "3\nSET 100\nINC\nGET", "3\nSET 0\nDEC\nGET"));

        all.add(prob("CLASS", "CL09", "Cap so", """
                a=0 b=0. SETA x SETB y SWAP. A in a, B in b.
                """, CatalogBank::pair,
                "2\nSETA 1\nA", "4\nSETA 1\nSETB 2\nSWAP\nA", "4\nSETA 3\nSETB 4\nSWAP\nB", "1\nB"));

        all.add(prob("CLASS", "CL10", "Dong ho phut", """
                Phut = 0. ADD x, GET in tong phut. RESET ve 0.
                """, CatalogBank::clock,
                "1\nGET", "3\nADD 10\nADD 5\nGET", "3\nADD 3\nRESET\nGET", "2\nADD 0\nGET"));

        all.add(prob("CLASS", "CL11", "Bang diem", """
                ADD name score. GET name in diem hoac MISSING.
                """, CatalogBank::scoreboard,
                "1\nGET An", "3\nADD An 9\nGET An\nGET B", "2\nADD A 1\nADD A 5"));

        all.add(prob("CLASS", "CL12", "Nhiet ke", """
                C=0. SETC x. GETC in C. GETF in C*9/5 (chia nguyen) + 32.
                """, CatalogBank::thermo,
                "1\nGETC", "2\nSETC 0\nGETF", "2\nSETC 100\nGETF", "2\nSETC -40\nGETF"));

        all.add(prob("CLASS", "CL13", "Kho", """
                count=0. ADD x, REMOVE x (neu khong du in FAIL va khong tru, du in OK), COUNT.
                """, CatalogBank::inv,
                "1\nCOUNT", "3\nADD 5\nREMOVE 3\nCOUNT", "2\nREMOVE 1\nCOUNT", "3\nADD 2\nREMOVE 2\nREMOVE 1"));

        all.add(prob("CLASS", "CL14", "Co boolean", """
                flag=0. SET, CLEAR, TOGGLE, TEST in 1 hoac 0.
                """, CatalogBank::flag,
                "1\nTEST", "2\nSET\nTEST", "2\nTOGGLE\nTEST", "3\nSET\nCLEAR\nTEST"));

        all.add(prob("CLASS", "CL15", "Accumulator", """
                v=0. ADD x SUB x GET.
                """, CatalogBank::acc,
                "1\nGET", "3\nADD 10\nSUB 3\nGET", "2\nSUB 5\nGET", "4\nADD 1\nADD 1\nSUB 2\nGET"));

        all.add(prob("CLASS", "CL16", "Khoang", """
                lo=0 hi=0. SETLO x, SETHI y, CONTAINS z in 1 neu lo<=z<=hi else 0.
                """, CatalogBank::rangeBox,
                "1\nCONTAINS 0", "3\nSETLO 1\nSETHI 5\nCONTAINS 3",
                "3\nSETLO 1\nSETHI 5\nCONTAINS 0", "3\nSETLO 5\nSETHI 1\nCONTAINS 3"));
    }

    private static void stack(List<Draft> all) {
        all.add(prob("STACK", "SK01", "Stack TOP", """
                Stack rong. q lenh: PUSH x, POP (neu rong in EMPTY), TOP (rong EMPTY).
                POP khong in neu thanh cong.
                """, in -> stackSim(in, "top"),
                "1\nTOP", "3\nPUSH 1\nPUSH 2\nTOP", "2\nPOP\nTOP", "4\nPUSH 5\nPOP\nPOP\nTOP"));

        all.add(prob("STACK", "SK02", "Stack con lai", """
                q lenh PUSH x / POP (POP rong bo qua). Sau do in stack tu dinh xuong day, cach space.
                Rong in EMPTY.
                """, in -> stackSim(in, "dump"),
                "0", "2\nPUSH 1\nPUSH 2", "3\nPUSH 1\nPUSH 2\nPOP", "2\nPOP\nPUSH 9"));

        all.add(prob("STACK", "SK03", "Ngoac dung", """
                Mot dong ngoac ()[]{}. YES neu balanced, NO neu khong.
                """, CatalogBank::balanced,
                "", "()", "(]", "([])", "([)]", "{{[]}}", "("));

        all.add(prob("STACK", "SK04", "Dao bang stack", """
                n roi n so. In dao nguoc (tuong duong stack).
                """, ins -> {
            int[] a = readArr(ins);
            StringBuilder sb = new StringBuilder();
            for (int i = a.length - 1; i >= 0; i--) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(a[i]);
            }
            return sb.length() == 0 ? "" : sb.toString();
        }, "0", "1\n8", "4\n1 2 3 4"));

        all.add(prob("STACK", "SK05", "Hau to +-*/", """
                Mot dong postfix: token cach space, toan hang so nguyen, toan tu + - *.
                In ket qua. Chia khong dung.
                """, CatalogBank::postfix,
                "3 4 +", "3 4 * 2 +", "10 2 -", "5 1 2 + 4 * +"));

        all.add(prob("STACK", "SK06", "Size stack", """
                q lenh PUSH x / POP (rong khong doi size). Cuoi in size.
                """, in -> stackSim(in, "size"),
                "0", "2\nPUSH 1\nPUSH 2", "3\nPUSH 1\nPOP\nPOP", "1\nPOP"));

        all.add(prob("STACK", "SK07", "Thu tu pop", """
                Dong 1: n. Dong 2: n so la hoan vi 1..n. Push 1..n tang dan, pop de duoc day do?
                YES/NO.
                """, CatalogBank::popSeq,
                "1\n1", "3\n1 2 3", "3\n3 2 1", "3\n3 1 2", "4\n1 3 2 4"));

        all.add(prob("STACK", "SK08", "Min stack", """
                q lenh: PUSH x, POP, MIN. MIN in min hien tai hoac EMPTY. POP rong in EMPTY.
                """, in -> stackSim(in, "min"),
                "1\nMIN", "3\nPUSH 3\nPUSH 1\nMIN", "4\nPUSH 2\nPUSH 5\nPOP\nMIN", "2\nPOP\nMIN"));

        all.add(prob("STACK", "SK09", "In cac POP", """
                q lenh PUSH x / POP. Moi POP thanh cong in gia tri; POP rong in EMPTY.
                """, in -> stackSim(in, "pops"),
                "1\nPOP", "3\nPUSH 1\nPUSH 2\nPOP", "4\nPUSH 7\nPOP\nPOP\nPUSH 8"));

        all.add(prob("STACK", "SK10", "Next greater", """
                n roi n so. Voi moi a[i], in phan tu dau tien ben phai lon hon no, khong co in -1.
                """, CatalogBank::nextGreater,
                "1\n1", "4\n4 5 2 25", "3\n3 2 1", "5\n1 1 1 1 1"));

        all.add(prob("STACK", "SK11", "Xoa cap giong nhau", """
                Mot dong chu thuong. Dung stack xoa 2 ky tu ke nhau giong nhau lap lai. In ket qua; rong in EMPTY.
                """, CatalogBank::removeDup,
                "", "a", "aa", "abbaca", "baaba"));

        all.add(prob("STACK", "SK12", "Sum stack", """
                q PUSH x / POP. In tong phan tu con lai.
                """, in -> stackSim(in, "sum"),
                "0", "2\nPUSH 3\nPUSH 4", "3\nPUSH 3\nPOP\nPUSH 10", "1\nPOP"));

        all.add(prob("STACK", "SK13", "Rong?", """
                q PUSH/POP. Cuoi in 1 neu rong else 0.
                """, in -> stackSim(in, "empty"),
                "0", "1\nPUSH 1", "2\nPUSH 1\nPOP", "3\nPUSH 1\nPUSH 2\nPOP"));

        all.add(prob("STACK", "SK14", "DUP", """
                PUSH x, DUP (nhan doi dinh; rong in EMPTY va bo qua), TOP, POP.
                """, in -> stackSim(in, "dupmix"),
                "1\nDUP", "3\nPUSH 4\nDUP\nTOP", "4\nPUSH 1\nDUP\nPOP\nTOP"));

        all.add(prob("STACK", "SK15", "2 stack cung 1 day", """
                q lenh: PUSH x (vao A), MOVE (pop A push B neu A rong in SKIP), BTOP in dinh B hoac EMPTY.
                """, CatalogBank::twoStack,
                "1\nBTOP", "3\nPUSH 1\nMOVE\nBTOP", "2\nMOVE\nBTOP", "4\nPUSH 2\nPUSH 3\nMOVE\nBTOP"));

        all.add(prob("STACK", "SK16", "Max stack", """
                PUSH x, POP, MAX in max hoac EMPTY.
                """, in -> stackSim(in, "max"),
                "1\nMAX", "3\nPUSH 1\nPUSH 8\nMAX", "4\nPUSH 8\nPUSH 1\nPOP\nMAX"));
    }

    private static void queue(List<Draft> all) {
        all.add(prob("QUEUE", "QU01", "Queue FRONT", """
                q lenh: ENQUEUE x, DEQUEUE (rong EMPTY), FRONT (rong EMPTY).
                DEQUEUE thanh cong khong in.
                """, in -> queueSim(in, "front"),
                "1\nFRONT", "3\nENQUEUE 1\nENQUEUE 2\nFRONT", "2\nDEQUEUE\nFRONT", "4\nENQUEUE 9\nDEQUEUE\nDEQUEUE\nFRONT"));

        all.add(prob("QUEUE", "QU02", "Hang con lai", """
                ENQUEUE/DEQUEUE (rong bo qua). In hang front->back space. Rong EMPTY.
                """, in -> queueSim(in, "dump"),
                "0", "2\nENQUEUE 1\nENQUEUE 2", "3\nENQUEUE 1\nENQUEUE 2\nDEQUEUE", "1\nDEQUEUE"));

        all.add(prob("QUEUE", "QU03", "Dao hang", """
                n roi n so (hang ban dau). In dao nguoc front moi.
                """, ins -> {
            int[] a = readArr(ins);
            StringBuilder sb = new StringBuilder();
            for (int i = a.length - 1; i >= 0; i--) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(a[i]);
            }
            return sb.length() == 0 ? "EMPTY" : sb.toString();
        }, "0", "1\n5", "4\n1 2 3 4"));

        all.add(prob("QUEUE", "QU04", "Nhi phan 1..n", """
                Nhap n (0..20). In nhi phan cua 1..n cach space. n=0 in rong.
                """, ins -> {
            int n = Integer.parseInt(ins.strip());
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= n; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Integer.toBinaryString(i));
            }
            return sb.toString();
        }, "0", "1", "2", "5", "8"));

        all.add(prob("QUEUE", "QU05", "Josephus", """
                n k (1-index, dem k nguoi thi loai). In thu tu loai, cach space.
                """, CatalogBank::josephus,
                "1 1", "5 2", "7 3", "4 1"));

        all.add(prob("QUEUE", "QU06", "Size hang", """
                ENQUEUE/DEQUEUE. Cuoi in size.
                """, in -> queueSim(in, "size"),
                "0", "2\nENQUEUE 1\nENQUEUE 2", "3\nENQUEUE 1\nDEQUEUE\nDEQUEUE", "1\nDEQUEUE"));

        all.add(prob("QUEUE", "QU07", "Tron 2 nua", """
                n chan roi n so. Chen nua sau xen nua dau: a0,b0,a1,b1...
                """, CatalogBank::interleave,
                "0", "2\n1 2", "4\n1 2 3 4", "6\n1 2 3 4 5 6"));

        all.add(prob("QUEUE", "QU08", "Con lai cuoi", """
                n roi n so. Dequeue het tru 1. In phan tu cuoi; n=0 in EMPTY.
                """, ins -> {
            int[] a = readArr(ins);
            return a.length == 0 ? "EMPTY" : String.valueOf(a[a.length - 1]);
        }, "0", "1\n4", "3\n1 2 3"));

        all.add(prob("QUEUE", "QU09", "Hang rong?", """
                ENQUEUE/DEQUEUE. Cuoi in 1 neu rong else 0.
                """, in -> queueSim(in, "empty"),
                "0", "1\nENQUEUE 1", "2\nENQUEUE 1\nDEQUEUE"));

        all.add(prob("QUEUE", "QU10", "In DEQUEUE", """
                Moi DEQUEUE thanh cong in gia tri; rong EMPTY.
                """, in -> queueSim(in, "deqs"),
                "1\nDEQUEUE", "3\nENQUEUE 1\nENQUEUE 2\nDEQUEUE", "4\nENQUEUE 5\nDEQUEUE\nDEQUEUE\nENQUEUE 8"));

        all.add(prob("QUEUE", "QU11", "Tong hang", """
                ENQUEUE/DEQUEUE. In tong con lai.
                """, in -> queueSim(in, "sum"),
                "0", "2\nENQUEUE 3\nENQUEUE 4", "3\nENQUEUE 3\nDEQUEUE\nENQUEUE 10"));

        all.add(prob("QUEUE", "QU12", "Max hang", """
                ENQUEUE/DEQUEUE. In max con lai hoac EMPTY.
                """, in -> queueSim(in, "max"),
                "0", "2\nENQUEUE 1\nENQUEUE 9", "3\nENQUEUE 9\nENQUEUE 1\nDEQUEUE"));

        all.add(prob("QUEUE", "QU13", "Xoay trai k", """
                Dong 1: n k. Dong 2: n so. Xoay trai k (k>=0). In hang. n=0 in EMPTY.
                """, CatalogBank::rotate,
                "0 3\n", "1 0\n5", "1 5\n5", "4 1\n1 2 3 4", "4 6\n1 2 3 4"));

        all.add(prob("QUEUE", "QU14", "Dao k dau", """
                Dong 1: n k. Dong 2 n so. Dao k phan tu dau (k<=n). In hang.
                """, CatalogBank::revFirstK,
                "0 0\n", "4 2\n1 2 3 4", "4 4\n1 2 3 4", "4 0\n1 2 3 4", "3 1\n9 8 7"));

        all.add(prob("QUEUE", "QU15", "Front sau mix", """
                q lenh ENQUEUE/DEQUEUE. In FRONT cuoi cung (rong EMPTY). Khong in trung gian.
                """, in -> queueSim(in, "frontEnd"),
                "0", "1\nENQUEUE 4", "3\nENQUEUE 1\nENQUEUE 2\nDEQUEUE", "2\nENQUEUE 8\nDEQUEUE"));

        all.add(prob("QUEUE", "QU16", "Min hang", """
                ENQUEUE/DEQUEUE. In min con lai hoac EMPTY.
                """, in -> queueSim(in, "min"),
                "0", "2\nENQUEUE 4\nENQUEUE 1", "3\nENQUEUE 1\nENQUEUE 9\nDEQUEUE"));
    }

    private static void tree(List<Draft> all) {
        all.add(prob("TREE", "TR01", "Tong cay mang", treeStmt("In tong gia tri nut."),
                in -> String.valueOf(sum(readTree(in))),
                "1\n7", "3\n1 2 3", "0", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR02", "Min cay", treeStmt("In min. n=0 in EMPTY."),
                in -> {
                    int[] t = readTree(in);
                    if (t[0] == 0) return "EMPTY";
                    int mn = t[1];
                    for (int i = 1; i <= t[0]; i++) mn = Math.min(mn, t[i]);
                    return String.valueOf(mn);
                }, "0", "1\n4", "3\n5 1 9"));

        all.add(prob("TREE", "TR03", "Max cay", treeStmt("In max. n=0 in EMPTY."),
                in -> {
                    int[] t = readTree(in);
                    if (t[0] == 0) return "EMPTY";
                    int mx = t[1];
                    for (int i = 1; i <= t[0]; i++) mx = Math.max(mx, t[i]);
                    return String.valueOf(mx);
                }, "0", "1\n4", "3\n5 1 9"));

        all.add(prob("TREE", "TR04", "Chieu cao", treeStmt("In height: n=0 -> 0, else floor(log2 n)+1."),
                in -> {
                    int n = readTree(in)[0];
                    if (n == 0) return "0";
                    return String.valueOf(32 - Integer.numberOfLeadingZeros(n));
                }, "0", "1\n1", "2\n1 2", "3\n1 2 3", "4\n1 2 3 4"));

        all.add(prob("TREE", "TR05", "Dem la", treeStmt("Dem nut la (khong co con)."),
                in -> {
                    int[] t = readTree(in);
                    int n = t[0], c = 0;
                    for (int i = 1; i <= n; i++) if (2 * i > n) c++;
                    return String.valueOf(c);
                }, "0", "1\n9", "3\n1 2 3", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR06", "Tim nut", """
                Cay mang heap. Dong 1 n, dong 2 n so, dong 3 x. YES neu x co trong cay.
                """, in -> {
            Scanner sc = sc(in);
            int n = sc.nextInt();
            int[] a = new int[n + 1];
            for (int i = 1; i <= n; i++) a[i] = sc.nextInt();
            int x = sc.nextInt();
            for (int i = 1; i <= n; i++) if (a[i] == x) return "YES";
            return "NO";
        }, "0\n5", "3\n1 2 3\n2", "3\n1 2 3\n9", "1\n8\n8"));

        all.add(prob("TREE", "TR07", "Preorder", treeStmt("In preorder (nut, trai, phai) cach space. n=0 rong."),
                in -> walk(readTree(in), "pre"),
                "0", "1\n5", "3\n1 2 3", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR08", "Inorder", treeStmt("In inorder cach space."),
                in -> walk(readTree(in), "in"),
                "0", "1\n5", "3\n1 2 3", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR09", "Postorder", treeStmt("In postorder cach space."),
                in -> walk(readTree(in), "post"),
                "0", "1\n5", "3\n1 2 3", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR10", "Cha cua x", """
                n, n so, roi x (gia tri). In gia tri cha; nhieu nut trung lay chi so nho nhat.
                Root hoac khong co in NONE.
                """, in -> treeQuery(in, "parent"),
                "1\n5\n5", "3\n1 2 3\n2", "3\n1 2 3\n1", "3\n1 2 3\n9"));

        all.add(prob("TREE", "TR11", "Con trai", """
                n, n so, x. In gia tri con trai cua nut x (chi so nho nhat). Khong co NONE.
                """, in -> treeQuery(in, "left"),
                "1\n5\n5", "3\n1 2 3\n1", "3\n1 2 3\n2", "7\n1 2 3 4 5 6 7\n2"));

        all.add(prob("TREE", "TR12", "Con phai", """
                n, n so, x. In con phai. Khong co NONE.
                """, in -> treeQuery(in, "right"),
                "1\n5\n5", "3\n1 2 3\n1", "7\n1 2 3 4 5 6 7\n3"));

        all.add(prob("TREE", "TR13", "Max-heap?", treeStmt("YES neu a[i]>=con, n=0 YES."),
                in -> {
                    int[] t = readTree(in);
                    int n = t[0];
                    for (int i = 1; i <= n; i++) {
                        if (2 * i <= n && t[i] < t[2 * i]) return "NO";
                        if (2 * i + 1 <= n && t[i] < t[2 * i + 1]) return "NO";
                    }
                    return "YES";
                }, "0", "1\n1", "3\n3 1 2", "3\n1 2 3"));

        all.add(prob("TREE", "TR14", "Muc cua x", """
                n, n so, x. Muc root=1. Khong co in 0. Trung lay chi so nho nhat.
                """, in -> treeQuery(in, "level"),
                "1\n5\n5", "3\n1 2 3\n2", "7\n1 2 3 4 5 6 7\n7", "3\n1 2 3\n9"));

        all.add(prob("TREE", "TR15", "Tong la", treeStmt("Tong nut la. n=0 in 0."),
                in -> {
                    int[] t = readTree(in);
                    int n = t[0], s = 0;
                    for (int i = 1; i <= n; i++) if (2 * i > n) s += t[i];
                    return String.valueOf(s);
                }, "0", "1\n9", "3\n1 2 3", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR16", "Nut trai nhat", treeStmt("Di con trai den la. n=0 EMPTY."),
                in -> {
                    int[] t = readTree(in);
                    if (t[0] == 0) return "EMPTY";
                    int i = 1;
                    while (2 * i <= t[0]) i = 2 * i;
                    return String.valueOf(t[i]);
                }, "0", "1\n4", "3\n1 2 3", "7\n1 2 3 4 5 6 7"));

        all.add(prob("TREE", "TR17", "Dem muc L", """
                n, n so, L (root=1). In so nut o muc L.
                """, in -> {
            Scanner sc = sc(in);
            int n = sc.nextInt();
            for (int i = 0; i < n; i++) sc.nextInt();
            int L = sc.nextInt();
            if (L < 1) return "0";
            int lo = 1 << (L - 1), hi = Math.min(n, (1 << L) - 1);
            if (lo > n) return "0";
            return String.valueOf(hi - lo + 1);
        }, "0\n1", "1\n5\n1", "3\n1 2 3\n2", "7\n1 2 3 4 5 6 7\n3", "7\n1 2 3 4 5 6 7\n4"));

        all.add(prob("TREE", "TR18", "Anh em", """
                n, n so, x. In gia tri anh em (cung cha). Khong co NONE.
                """, in -> treeQuery(in, "sib"),
                "1\n5\n5", "3\n1 2 3\n2", "3\n1 2 3\n3", "7\n1 2 3 4 5 6 7\n4"));
    }

    private static final int IO01 = 101, IO02 = 102, IO03 = 103, IO04 = 104, IO05 = 105,
            IO06 = 106, IO08 = 108, IO09 = 109, IO11 = 111, IO13 = 113, IO14 = 114, IO15 = 115;

    private static Draft prob(String topic, String code, String title, String statement,
                              Function<String, String> oracle, String... inputs) {
        String full = "Ma " + code + ". Viet public class Main, chi in dap so dung format.\n" + statement.trim() + "\n";
        List<String> parts = new ArrayList<>();
        for (String in : inputs) {
            String out = oracle.apply(in);
            if (out == null) out = "";
            parts.add(in + ">>>" + out.strip());
        }
        return new Draft(topic, code, title, full, 10, String.join(";;", parts));
    }

    private static String treeStmt(String extra) {
        return """
                Cay nhi phan day du luu mang 1-index: con trai 2*i, con phai 2*i+1.
                Dong 1: n (0..31). Dong 2: n so (neu n>0).
                """ + extra + "\n";
    }

    private static String randArr(int seed, int n, int lo, int hi) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder();
        sb.append(n).append('\n');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(r.nextInt(hi - lo + 1) + lo);
        }
        return sb.toString();
    }

    private static Scanner sc(String in) {
        return new Scanner(in);
    }

    private static int[] readArr(String in) {
        Scanner sc = sc(in);
        if (!sc.hasNextInt()) return new int[0];
        int n = sc.nextInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = sc.nextInt();
        return a;
    }

    private static String minMax(String in, boolean min) {
        int[] a = readArr(in);
        int v = a[0];
        for (int x : a) v = min ? Math.min(v, x) : Math.max(v, x);
        return String.valueOf(v);
    }

    private static String secondMax(String in) {
        int[] a = readArr(in);
        int mx = Integer.MIN_VALUE, mx2 = Integer.MIN_VALUE;
        for (int v : a) mx = Math.max(mx, v);
        boolean found = false;
        for (int v : a) {
            if (v < mx && (!found || v > mx2)) { mx2 = v; found = true; }
        }
        return String.valueOf(found ? mx2 : mx);
    }

    private static String anagram(String in) {
        String[] p = in.split("\n", 2);
        String a = p[0].toLowerCase(Locale.ROOT).replace(" ", "");
        String b = (p.length > 1 ? p[1] : "").toLowerCase(Locale.ROOT).replace(" ", "");
        char[] x = a.toCharArray(), y = b.toCharArray();
        Arrays.sort(x); Arrays.sort(y);
        return Arrays.equals(x, y) ? "YES" : "NO";
    }

    private static String cmdsBody(String in) {
        Scanner sc = sc(in);
        if (!sc.hasNextInt()) return "";
        int q = sc.nextInt();
        sc.nextLine();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q && sc.hasNextLine(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(sc.nextLine());
        }
        return sb.toString();
    }

    private static List<String> lines(String body) {
        if (body.isBlank()) return List.of();
        return Arrays.asList(body.split("\n"));
    }

    private static String joinOut(List<String> o) {
        return String.join("\n", o);
    }

    private static String counter(String in) {
        int v = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            switch (line.trim()) {
                case "INC" -> v++;
                case "DEC" -> v--;
                case "RESET" -> v = 0;
                case "GET" -> o.add(String.valueOf(v));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String bank(String in) {
        int bal = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "DEPOSIT" -> bal += Integer.parseInt(p[1]);
                case "WITHDRAW" -> {
                    int x = Integer.parseInt(p[1]);
                    if (x > bal) o.add("ERROR");
                    else { bal -= x; o.add("OK"); }
                }
                case "BALANCE" -> o.add(String.valueOf(bal));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String swt(String in) {
        boolean on = false;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            switch (line.trim()) {
                case "ON" -> on = true;
                case "OFF" -> on = false;
                case "TOGGLE" -> on = !on;
                case "STATE" -> o.add(on ? "ON" : "OFF");
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String rect(String in) {
        int w = 0, h = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "SETW" -> w = Integer.parseInt(p[1]);
                case "SETH" -> h = Integer.parseInt(p[1]);
                case "AREA" -> o.add(String.valueOf(w * h));
                case "PERIMETER" -> o.add(String.valueOf(2 * (w + h)));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String point(String in) {
        int x = 0, y = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "MOVEX" -> x += Integer.parseInt(p[1]);
                case "MOVEY" -> y += Integer.parseInt(p[1]);
                case "X" -> o.add(String.valueOf(x));
                case "Y" -> o.add(String.valueOf(y));
                case "DIST2" -> o.add(String.valueOf(x * x + y * y));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String avg(String in) {
        int n = 0, s = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "ADD" -> { n++; s += Integer.parseInt(p[1]); }
                case "AVG" -> o.add(n == 0 ? "0" : String.valueOf(s / n));
                case "COUNT" -> o.add(String.valueOf(n));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String minmaxT(String in) {
        boolean any = false;
        int mn = 0, mx = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "UPDATE" -> {
                    int v = Integer.parseInt(p[1]);
                    if (!any) { mn = mx = v; any = true; }
                    else { mn = Math.min(mn, v); mx = Math.max(mx, v); }
                }
                case "GETMIN" -> o.add(any ? String.valueOf(mn) : "EMPTY");
                case "GETMAX" -> o.add(any ? String.valueOf(mx) : "EMPTY");
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String dimmer(String in) {
        int v = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "SET" -> v = Math.max(0, Math.min(100, Integer.parseInt(p[1])));
                case "INC" -> v = Math.min(100, v + 1);
                case "DEC" -> v = Math.max(0, v - 1);
                case "GET" -> o.add(String.valueOf(v));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String pair(String in) {
        int a = 0, b = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "SETA" -> a = Integer.parseInt(p[1]);
                case "SETB" -> b = Integer.parseInt(p[1]);
                case "SWAP" -> { int t = a; a = b; b = t; }
                case "A" -> o.add(String.valueOf(a));
                case "B" -> o.add(String.valueOf(b));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String clock(String in) {
        int m = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "ADD" -> m += Integer.parseInt(p[1]);
                case "RESET" -> m = 0;
                case "GET" -> o.add(String.valueOf(m));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String scoreboard(String in) {
        Map<String, Integer> map = new LinkedHashMap<>();
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "ADD" -> map.put(p[1], Integer.parseInt(p[2]));
                case "GET" -> o.add(map.containsKey(p[1]) ? String.valueOf(map.get(p[1])) : "MISSING");
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String thermo(String in) {
        int c = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "SETC" -> c = Integer.parseInt(p[1]);
                case "GETC" -> o.add(String.valueOf(c));
                case "GETF" -> o.add(String.valueOf(c * 9 / 5 + 32));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String inv(String in) {
        int cnt = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "ADD" -> cnt += Integer.parseInt(p[1]);
                case "REMOVE" -> {
                    int x = Integer.parseInt(p[1]);
                    if (x > cnt) o.add("FAIL");
                    else { cnt -= x; o.add("OK"); }
                }
                case "COUNT" -> o.add(String.valueOf(cnt));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String flag(String in) {
        int f = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            switch (line.trim()) {
                case "SET" -> f = 1;
                case "CLEAR" -> f = 0;
                case "TOGGLE" -> f = 1 - f;
                case "TEST" -> o.add(String.valueOf(f));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String acc(String in) {
        int v = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "ADD" -> v += Integer.parseInt(p[1]);
                case "SUB" -> v -= Integer.parseInt(p[1]);
                case "GET" -> o.add(String.valueOf(v));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String rangeBox(String in) {
        int lo = 0, hi = 0;
        List<String> o = new ArrayList<>();
        for (String line : lines(cmdsBody(in))) {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "SETLO" -> lo = Integer.parseInt(p[1]);
                case "SETHI" -> hi = Integer.parseInt(p[1]);
                case "CONTAINS" -> {
                    int z = Integer.parseInt(p[1]);
                    int a = Math.min(lo, hi), b = Math.max(lo, hi);
                    o.add(z >= a && z <= b ? "1" : "0");
                }
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String stackSim(String in, String mode) {
        Deque<Integer> st = new ArrayDeque<>();
        List<String> o = new ArrayList<>();
        Scanner sc = sc(in);
        int q = sc.hasNextInt() ? sc.nextInt() : 0;
        if (sc.hasNextLine()) sc.nextLine();
        for (int i = 0; i < q && sc.hasNextLine(); i++) {
            String[] p = sc.nextLine().trim().split("\\s+");
            if (p[0].isEmpty()) continue;
            switch (p[0]) {
                case "PUSH" -> st.push(Integer.parseInt(p[1]));
                case "POP" -> {
                    if (st.isEmpty()) {
                        if (mode.equals("pops") || mode.equals("min") || mode.equals("max") || mode.equals("top"))
                            o.add("EMPTY");
                    } else {
                        int v = st.pop();
                        if (mode.equals("pops")) o.add(String.valueOf(v));
                    }
                }
                case "TOP" -> o.add(st.isEmpty() ? "EMPTY" : String.valueOf(st.peek()));
                case "DUP" -> {
                    if (st.isEmpty()) o.add("EMPTY");
                    else st.push(st.peek());
                }
                case "MIN" -> o.add(st.isEmpty() ? "EMPTY" : String.valueOf(st.stream().min(Integer::compareTo).orElseThrow()));
                case "MAX" -> o.add(st.isEmpty() ? "EMPTY" : String.valueOf(st.stream().max(Integer::compareTo).orElseThrow()));
                default -> { }
            }
        }
        if (mode.equals("front")) { /* unused */ }
        switch (mode) {
            case "dump" -> {
                if (st.isEmpty()) return "EMPTY";
                List<Integer> t = new ArrayList<>(st);
                return joinInts(t);
            }
            case "size" -> { return String.valueOf(st.size()); }
            case "sum" -> { return String.valueOf(st.stream().mapToInt(x -> x).sum()); }
            case "empty" -> { return st.isEmpty() ? "1" : "0"; }
            default -> { return joinOut(o); }
        }
    }

    private static String twoStack(String in) {
        Deque<Integer> a = new ArrayDeque<>(), b = new ArrayDeque<>();
        List<String> o = new ArrayList<>();
        Scanner sc = sc(in);
        int q = sc.nextInt();
        sc.nextLine();
        for (int i = 0; i < q && sc.hasNextLine(); i++) {
            String[] p = sc.nextLine().trim().split("\\s+");
            switch (p[0]) {
                case "PUSH" -> a.push(Integer.parseInt(p[1]));
                case "MOVE" -> {
                    if (a.isEmpty()) o.add("SKIP");
                    else b.push(a.pop());
                }
                case "BTOP" -> o.add(b.isEmpty() ? "EMPTY" : String.valueOf(b.peek()));
                default -> { }
            }
        }
        return joinOut(o);
    }

    private static String balanced(String in) {
        String s = in.stripTrailing();
        Deque<Character> st = new ArrayDeque<>();
        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == '[' || ch == '{') st.push(ch);
            else if (ch == ')' || ch == ']' || ch == '}') {
                if (st.isEmpty()) return "NO";
                char o = st.pop();
                if ((ch == ')' && o != '(') || (ch == ']' && o != '[') || (ch == '}' && o != '{')) return "NO";
            }
        }
        return st.isEmpty() ? "YES" : "NO";
    }

    private static String postfix(String in) {
        Deque<Integer> st = new ArrayDeque<>();
        for (String t : in.strip().split("\\s+")) {
            switch (t) {
                case "+" -> st.push(st.pop() + st.pop());
                case "*" -> st.push(st.pop() * st.pop());
                case "-" -> { int y = st.pop(), x = st.pop(); st.push(x - y); }
                default -> st.push(Integer.parseInt(t));
            }
        }
        return String.valueOf(st.pop());
    }

    private static String popSeq(String in) {
        Scanner sc = sc(in);
        int n = sc.nextInt();
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = sc.nextInt();
        Deque<Integer> st = new ArrayDeque<>();
        int next = 1, j = 0;
        while (next <= n) {
            st.push(next++);
            while (!st.isEmpty() && j < n && st.peek() == p[j]) { st.pop(); j++; }
        }
        while (!st.isEmpty() && j < n && st.peek() == p[j]) { st.pop(); j++; }
        return j == n ? "YES" : "NO";
    }

    private static String nextGreater(String in) {
        int[] a = readArr(in);
        int n = a.length;
        int[] ans = new int[n];
        Arrays.fill(ans, -1);
        Deque<Integer> st = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            while (!st.isEmpty() && a[st.peek()] < a[i]) ans[st.pop()] = a[i];
            st.push(i);
        }
        return joinInts(ans);
    }

    private static String removeDup(String in) {
        String s = in.stripTrailing();
        Deque<Character> st = new ArrayDeque<>();
        for (char ch : s.toCharArray()) {
            if (!st.isEmpty() && st.peek() == ch) st.pop();
            else st.push(ch);
        }
        if (st.isEmpty()) return "EMPTY";
        StringBuilder sb = new StringBuilder();
        for (char ch : st) sb.append(ch);
        return sb.reverse().toString();
    }

    private static String queueSim(String in, String mode) {
        Deque<Integer> q = new ArrayDeque<>();
        List<String> o = new ArrayList<>();
        Scanner sc = sc(in);
        int qn = sc.hasNextInt() ? sc.nextInt() : 0;
        if (sc.hasNextLine()) sc.nextLine();
        for (int i = 0; i < qn && sc.hasNextLine(); i++) {
            String[] p = sc.nextLine().trim().split("\\s+");
            if (p[0].isEmpty()) continue;
            switch (p[0]) {
                case "ENQUEUE" -> q.addLast(Integer.parseInt(p[1]));
                case "DEQUEUE" -> {
                    if (q.isEmpty()) {
                        if (mode.equals("deqs") || mode.equals("front")) o.add("EMPTY");
                    } else {
                        int v = q.removeFirst();
                        if (mode.equals("deqs")) o.add(String.valueOf(v));
                    }
                }
                case "FRONT" -> o.add(q.isEmpty() ? "EMPTY" : String.valueOf(q.peekFirst()));
                default -> { }
            }
        }
        if (mode.equals("frontEnd")) return q.isEmpty() ? "EMPTY" : String.valueOf(q.peekFirst());
        return switch (mode) {
            case "dump" -> q.isEmpty() ? "EMPTY" : joinInts(new ArrayList<>(q));
            case "size" -> String.valueOf(q.size());
            case "empty" -> q.isEmpty() ? "1" : "0";
            case "sum" -> String.valueOf(q.stream().mapToInt(x -> x).sum());
            case "max" -> q.isEmpty() ? "EMPTY" : String.valueOf(q.stream().max(Integer::compareTo).orElseThrow());
            case "min" -> q.isEmpty() ? "EMPTY" : String.valueOf(q.stream().min(Integer::compareTo).orElseThrow());
            default -> joinOut(o);
        };
    }

    private static String josephus(String in) {
        Scanner sc = sc(in);
        int n = sc.nextInt(), k = sc.nextInt();
        Deque<Integer> q = new ArrayDeque<>();
        for (int i = 1; i <= n; i++) q.addLast(i);
        List<Integer> out = new ArrayList<>();
        while (!q.isEmpty()) {
            for (int i = 1; i < k; i++) q.addLast(q.removeFirst());
            out.add(q.removeFirst());
        }
        return joinInts(out);
    }

    private static String interleave(String in) {
        int[] a = readArr(in);
        int n = a.length;
        if (n == 0) return "EMPTY";
        int h = n / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(a[i]).append(' ').append(a[h + i]);
        }
        return sb.toString();
    }

    private static String rotate(String in) {
        Scanner sc = sc(in);
        int n = sc.nextInt(), k = sc.nextInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = sc.nextInt();
        if (n == 0) return "EMPTY";
        k %= n;
        int[] b = new int[n];
        for (int i = 0; i < n; i++) b[i] = a[(i + k) % n];
        return joinInts(b);
    }

    private static String revFirstK(String in) {
        Scanner sc = sc(in);
        int n = sc.nextInt(), k = sc.nextInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = sc.nextInt();
        if (n == 0) return "EMPTY";
        k = Math.min(k, n);
        for (int i = 0, j = k - 1; i < j; i++, j--) {
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return joinInts(a);
    }

    /** t[0]=n, t[1..n] values */
    private static int[] readTree(String in) {
        Scanner sc = sc(in);
        int n = sc.hasNextInt() ? sc.nextInt() : 0;
        int[] t = new int[n + 1];
        t[0] = n;
        for (int i = 1; i <= n; i++) t[i] = sc.nextInt();
        return t;
    }

    private static int sum(int[] t) {
        int s = 0;
        for (int i = 1; i <= t[0]; i++) s += t[i];
        return s;
    }

    private static String walk(int[] t, String kind) {
        List<Integer> o = new ArrayList<>();
        walkRec(t, 1, kind, o);
        return o.isEmpty() ? "" : joinInts(o);
    }

    private static void walkRec(int[] t, int i, String kind, List<Integer> o) {
        if (i > t[0]) return;
        if (kind.equals("pre")) o.add(t[i]);
        walkRec(t, 2 * i, kind, o);
        if (kind.equals("in")) o.add(t[i]);
        walkRec(t, 2 * i + 1, kind, o);
        if (kind.equals("post")) o.add(t[i]);
    }

    private static String treeQuery(String in, String kind) {
        Scanner sc = sc(in);
        int n = sc.nextInt();
        int[] t = new int[n + 1];
        t[0] = n;
        for (int i = 1; i <= n; i++) t[i] = sc.nextInt();
        int x = sc.nextInt();
        int idx = -1;
        for (int i = 1; i <= n; i++) if (t[i] == x) { idx = i; break; }
        if (idx < 0) return kind.equals("level") ? "0" : "NONE";
        return switch (kind) {
            case "parent" -> idx == 1 ? "NONE" : String.valueOf(t[idx / 2]);
            case "left" -> 2 * idx <= n ? String.valueOf(t[2 * idx]) : "NONE";
            case "right" -> 2 * idx + 1 <= n ? String.valueOf(t[2 * idx + 1]) : "NONE";
            case "level" -> String.valueOf(32 - Integer.numberOfLeadingZeros(idx));
            case "sib" -> {
                if (idx == 1) yield "NONE";
                int p = idx / 2;
                int other = (idx == 2 * p) ? 2 * p + 1 : 2 * p;
                yield other <= n ? String.valueOf(t[other]) : "NONE";
            }
            default -> "NONE";
        };
    }

    private static String joinInts(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int v : a) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(v);
        }
        return sb.toString();
    }

    private static String joinInts(List<Integer> a) {
        StringBuilder sb = new StringBuilder();
        for (int v : a) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(v);
        }
        return sb.toString();
    }
}
