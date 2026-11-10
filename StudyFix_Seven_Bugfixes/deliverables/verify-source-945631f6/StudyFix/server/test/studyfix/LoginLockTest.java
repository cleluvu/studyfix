package studyfix;

import java.nio.file.Path;
import java.security.*;
import java.security.spec.*;
import java.util.concurrent.*;
import javax.crypto.*;
import static studyfix.ClassroomIntegrationTest.check;

/** Test provider pauses real PBKDF2 so lock contention can be checked without timing benchmarks. */
public final class LoginLockTest {
    public static final class PausedFactory extends SecretKeyFactorySpi {
        static final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        private SecretKeyFactory delegate() throws NoSuchAlgorithmException {
            try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", "SunJCE"); }
            catch (NoSuchProviderException e) { throw new NoSuchAlgorithmException(e); }
        }
        @Override protected SecretKey engineGenerateSecret(KeySpec spec) throws InvalidKeySpecException {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new InvalidKeySpecException("test timeout");
                return delegate().generateSecret(spec);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InvalidKeySpecException(e); }
            catch (NoSuchAlgorithmException e) { throw new InvalidKeySpecException(e); }
        }
        @Override protected KeySpec engineGetKeySpec(SecretKey key, Class<?> spec) throws InvalidKeySpecException {
            try { return delegate().getKeySpec(key, spec); } catch (NoSuchAlgorithmException e) { throw new InvalidKeySpecException(e); }
        }
        @Override protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
            try { return delegate().translateKey(key); } catch (NoSuchAlgorithmException e) { throw new InvalidKeyException(e); }
        }
    }
    static void run(Path database) throws Exception {
        try (Store store = new Store(database)) {
            Provider provider = new Provider("StudyFixTestPBKDF", "1", "Test-only PBKDF pause") { };
            provider.put("SecretKeyFactory.PBKDF2WithHmacSHA256", PausedFactory.class.getName());
            Security.insertProviderAt(provider, 1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            char[] password = "Teacher123!".toCharArray();
            try {
                Future<Identity> login = executor.submit(() -> store.login("teacher", password));
                check(PausedFactory.entered.await(3, TimeUnit.SECONDS), "login reached paused password verification");
                Future<?> read = executor.submit(() -> { store.loadRooms(); return null; });
                read.get(2, TimeUnit.SECONDS);
                check(!login.isDone(), "room database read completes while password verification is still paused");
                PausedFactory.release.countDown();
                check(login.get(3, TimeUnit.SECONDS).username().equals("teacher"), "password verification still authenticates correctly");
                check(new String(password).chars().allMatch(c -> c == 0), "login clears supplied password after verification");
            } finally {
                PausedFactory.release.countDown(); executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS);
                Security.removeProvider(provider.getName());
            }
        }
    }
}
