package studyfix;

import java.io.Console;
import java.util.Arrays;

/** Công cụ cục bộ cho người vận hành có credential MySQL; không phải API cấp quyền Teacher. */
public final class ManagementCli {
    private ManagementCli(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=3||!args[0].equals("create-teacher"))throw new IllegalArgumentException("Dùng: ManagementCli create-teacher username displayName");
        ManagementConfig config=ManagementConfig.environment();if(config==null)throw new IllegalStateException("Cần cấu hình STUDYFIX_MANAGEMENT_DB=mysql.");
        Console console=System.console();if(console==null)throw new IllegalStateException("Chạy trong terminal tương tác thật để nhập mật khẩu ẩn; không truyền mật khẩu qua dòng lệnh.");
        char[] password=console.readPassword("Mat khau Teacher (it nhat 12 ky tu): "),confirmation=console.readPassword("Nhap lai mat khau: ");
        try{
            if(password==null||confirmation==null||!Arrays.equals(password,confirmation))throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
            new MySqlManagement(config).createUser(args[1],null,args[2],"TEACHER",password);
            console.printf("Da tao Teacher. Dang nhap bang tai khoan vua cap; khong co mat khau mac dinh.%n");
        }finally{if(password!=null)Arrays.fill(password,'\0');if(confirmation!=null)Arrays.fill(confirmation,'\0');}
    }
}
