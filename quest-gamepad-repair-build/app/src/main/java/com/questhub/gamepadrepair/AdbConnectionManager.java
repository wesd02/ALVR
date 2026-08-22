package com.questhub.gamepadrepair;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public final class AdbConnectionManager extends AbsAdbConnectionManager {
    private static volatile AdbConnectionManager instance;
    private static final String KEY_FILE = "adb-private.key";
    private static final String CERT_FILE = "adb-certificate.pem";

    public static AdbConnectionManager getInstance(Context context) throws Exception {
        if (instance == null) {
            synchronized (AdbConnectionManager.class) {
                if (instance == null) instance = new AdbConnectionManager(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final PrivateKey privateKey;
    private final Certificate certificate;

    private AdbConnectionManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        PrivateKey loadedKey = readPrivateKey(new File(context.getFilesDir(), KEY_FILE));
        Certificate loadedCert = readCertificate(new File(context.getFilesDir(), CERT_FILE));
        if (loadedKey == null || loadedCert == null) {
            KeyPair pair = generateKeyPair();
            loadedKey = pair.getPrivate();
            loadedCert = generateCertificate(pair.getPublic(), loadedKey);
            writePrivateKey(new File(context.getFilesDir(), KEY_FILE), loadedKey);
            writeCertificate(new File(context.getFilesDir(), CERT_FILE), loadedCert);
        }
        privateKey = loadedKey;
        certificate = loadedCert;
    }

    @Override protected PrivateKey getPrivateKey() { return privateKey; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "QuestGamepadRepair"; }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        return generator.generateKeyPair();
    }

    private static Certificate generateCertificate(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        String algorithm = "SHA512withRSA";
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 10L * 365L * 24L * 60L * 60L * 1000L);
        X500Name name = new X500Name("CN=Quest Gamepad Repair");
        CertificateExtensions extensions = new CertificateExtensions();
        extensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
        extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
        info.set("subject", new CertificateSubjectName(name));
        info.set("issuer", new CertificateIssuerName(name));
        info.set("key", new CertificateX509Key(publicKey));
        info.set("validity", new CertificateValidity(notBefore, notAfter));
        info.set("extensions", extensions);
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privateKey, algorithm);
        return cert;
    }

    private static PrivateKey readPrivateKey(File file) {
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != bytes.length) return null;
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception ignored) { return null; }
    }

    private static Certificate readCertificate(File file) {
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (Exception ignored) { return null; }
    }

    private static void writePrivateKey(File file, PrivateKey key) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(key.getEncoded()); }
    }

    private static void writeCertificate(File file, Certificate cert) throws Exception {
        String pem = "-----BEGIN CERTIFICATE-----\n" + Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP) + "\n-----END CERTIFICATE-----\n";
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(pem.getBytes(StandardCharsets.US_ASCII)); }
    }
}
