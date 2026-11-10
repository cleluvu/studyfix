package studyfix;

/** Không đưa credential vào log, URL hoặc mã nguồn. */
final class ManagementConfig {
    final String url,user,password;
    ManagementConfig(String url,String user,String password){
        if(url==null||!url.startsWith("jdbc:mysql://")||user==null||user.isBlank()||password==null||password.isEmpty())throw new IllegalArgumentException("Cần STUDYFIX_MYSQL_URL, STUDYFIX_MYSQL_USER, STUDYFIX_MYSQL_PASSWORD.");
        if(url.matches("(?is).*[?&](user|password)=.*")||url.substring(13).contains("@"))throw new IllegalArgumentException("Không đặt tài khoản/mật khẩu trong JDBC URL.");
        this.url=url;this.user=user;this.password=password;
    }
    static ManagementConfig environment(){
        String mode=System.getProperty("studyfix.management.db",System.getenv().getOrDefault("STUDYFIX_MANAGEMENT_DB","sqlite"));
        if(mode.equalsIgnoreCase("sqlite"))return null;
        if(!mode.equalsIgnoreCase("mysql"))throw new IllegalArgumentException("STUDYFIX_MANAGEMENT_DB phải là mysql hoặc sqlite.");
        return new ManagementConfig(System.getenv("STUDYFIX_MYSQL_URL"),System.getenv("STUDYFIX_MYSQL_USER"),System.getenv("STUDYFIX_MYSQL_PASSWORD"));
    }
    @Override public String toString(){return "ManagementConfig[mysql, credentials redacted]";}
}
