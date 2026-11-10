package studyfix;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    static String hash(char[] password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    static boolean verify(char[] password, String stored) {
        String[] fields = stored.split(":");
        byte[] actual = derive(password, Base64.getDecoder().decode(fields[1]), Integer.parseInt(fields[0]));
        return MessageDigest.isEqual(actual, Base64.getDecoder().decode(fields[2]));
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
    private Passwords() { }
}
