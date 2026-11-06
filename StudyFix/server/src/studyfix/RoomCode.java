package studyfix;

import java.text.Normalizer;
import java.util.Locale;

/** Mã phòng để người dùng nhập, tách riêng với ID cố định trong database. */
final class RoomCode {
    static String normalize(String value) {
        String code=Normalizer.normalize(value==null?"":value.strip(),Normalizer.Form.NFC);
        if(code.isBlank()||code.codePointCount(0,code.length())>80||code.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x2028||c==0x2029))
            throw new IllegalArgumentException("Mã phòng phải có từ 1 đến 80 ký tự, không chứa ký tự điều khiển hoặc xuống dòng.");
        return code;
    }
    static String key(String code) {return normalize(code).toLowerCase(Locale.ROOT);}
}
