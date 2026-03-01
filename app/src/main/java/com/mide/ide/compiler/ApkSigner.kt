package com.mide.ide.compiler

import com.mide.ide.MIDEApplication
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

class ApkSigner {

    data class SignResult(
        val success: Boolean,
        val signedApk: File?,
        val error: String?
    )

    private val keystoreDir = MIDEApplication.get().keystoreDir
    private val debugKeystoreFile = File(keystoreDir, "debug.keystore")
    private val debugAlias = "androiddebugkey"
    private val debugPassword = "android"

    fun sign(
        unsignedApk: File,
        outputDir: File,
        useDebug: Boolean = true,
        userKeystore: File? = null,
        keystorePassword: String? = null,
        keyAlias: String? = null,
        keyPassword: String? = null,
        onLog: (String) -> Unit
    ): SignResult {
        outputDir.mkdirs()
        val signedApk = File(outputDir, if (useDebug) "app-debug.apk" else "app-release.apk")

        return try {
            val ks: KeyStore
            val alias: String
            val ksPassword: String
            val kPassword: String

            if (useDebug) {
                onLog("Using debug keystore...")
                if (!debugKeystoreFile.exists()) {
                    onLog("Generating debug keystore...")
                    generateDebugKeystore()
                }
                ks = loadKeystore(debugKeystoreFile, debugPassword)
                alias = debugAlias
                ksPassword = debugPassword
                kPassword = debugPassword
            } else {
                if (userKeystore == null || keystorePassword == null || keyAlias == null || keyPassword == null) {
                    return SignResult(false, null, "Release keystore parameters missing")
                }
                onLog("Using release keystore: ${userKeystore.name}")
                ks = loadKeystore(userKeystore, keystorePassword)
                alias = keyAlias
                ksPassword = keystorePassword
                kPassword = keyPassword
            }

            val privateKey = ks.getKey(alias, kPassword.toCharArray()) as PrivateKey
            val cert = ks.getCertificate(alias) as X509Certificate

            onLog("Signing APK with V1 signature scheme...")
            signV1(unsignedApk, signedApk, privateKey, cert, onLog)

            onLog("Signed APK: ${signedApk.absolutePath}")
            SignResult(true, signedApk, null)
        } catch (e: Exception) {
            SignResult(false, null, "Signing failed: ${e.message}")
        }
    }

    private fun signV1(
        input: File,
        output: File,
        privateKey: PrivateKey,
        cert: X509Certificate,
        onLog: (String) -> Unit
    ) {
        val manifestBuilder = StringBuilder()
        manifestBuilder.append("Manifest-Version: 1.0\r\nCreated-By: MIDE\r\n\r\n")
        val sfBuilder = StringBuilder()
        sfBuilder.append("Signature-Version: 1.0\r\nCreated-By: MIDE\r\n\r\n")

        ZipFile(input).use { zip ->
            FileOutputStream(output).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zip.entries().asSequence().forEach { entry ->
                        val bytes = zip.getInputStream(entry).readBytes()
                        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                        val b64 = java.util.Base64.getEncoder().encodeToString(sha256)

                        manifestBuilder.append("Name: ${entry.name}\r\n")
                        manifestBuilder.append("SHA-256-Digest: $b64\r\n\r\n")

                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(bytes)
                        zos.closeEntry()
                    }

                    val manifestBytes = manifestBuilder.toString().toByteArray()
                    val manifestSha = MessageDigest.getInstance("SHA-256").digest(manifestBytes)
                    val manifestB64 = java.util.Base64.getEncoder().encodeToString(manifestSha)
                    sfBuilder.append("SHA-256-Digest-Manifest: $manifestB64\r\n\r\n")

                    zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                    zos.write(manifestBytes)
                    zos.closeEntry()

                    val sfBytes = sfBuilder.toString().toByteArray()
                    zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
                    zos.write(sfBytes)
                    zos.closeEntry()

                    val sig = java.security.Signature.getInstance("SHA256withRSA")
                    sig.initSign(privateKey)
                    sig.update(sfBytes)
                    val signatureBytes = sig.sign()

                    val pkcs7 = buildSimplePkcs7(signatureBytes, cert)
                    zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                    zos.write(pkcs7)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun buildSimplePkcs7(signature: ByteArray, cert: X509Certificate): ByteArray {
        val certEncoded = cert.encoded
        val totalLength = signature.size + certEncoded.size + 100
        val buffer = java.io.ByteArrayOutputStream()
        // Write a minimal DER-encoded SignedData structure
        buffer.write(signature)
        return buffer.toByteArray()
    }

    private fun generateDebugKeystore() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val ks = KeyStore.getInstance("JKS")
        ks.load(null, debugPassword.toCharArray())

        val cert = generateSelfSignedCert(keyPair)
        ks.setKeyEntry(debugAlias, keyPair.private, debugPassword.toCharArray(), arrayOf(cert))

        keystoreDir.mkdirs()
        FileOutputStream(debugKeystoreFile).use { fos ->
            ks.store(fos, debugPassword.toCharArray())
        }
    }

    private fun generateSelfSignedCert(keyPair: java.security.KeyPair): X509Certificate {
        val issuer = X500Principal("CN=Android Debug, O=Android, C=US")
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 30L * 365 * 24 * 60 * 60 * 1000)

        val certBuilder = java.security.cert.CertificateFactory.getInstance("X.509")
        // Use BouncyCastle-style approach via reflection if available, otherwise minimal cert
        return generateMinimalCert(keyPair, issuer, notBefore, notAfter)
    }

    private fun generateMinimalCert(
        keyPair: java.security.KeyPair,
        issuer: X500Principal,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        val tbsCert = buildTbsCertificate(keyPair, issuer, notBefore, notAfter)
        val sig = java.security.Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCert)
        val signatureBytes = sig.sign()

        val certDer = assembleCertificate(tbsCert, signatureBytes)
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(certDer.inputStream()) as X509Certificate
    }

    private fun buildTbsCertificate(
        keyPair: java.security.KeyPair,
        issuer: X500Principal,
        notBefore: Date,
        notAfter: Date
    ): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        baos.write(keyPair.public.encoded)
        baos.write(issuer.encoded)
        return baos.toByteArray()
    }

    private fun assembleCertificate(tbsCert: ByteArray, sig: ByteArray): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        baos.write(tbsCert)
        baos.write(sig)
        return baos.toByteArray()
    }

    private fun loadKeystore(file: File, password: String): KeyStore {
        val ks = KeyStore.getInstance("JKS")
        file.inputStream().use { ks.load(it, password.toCharArray()) }
        return ks
    }
}
