/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dataio

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based authenticated encryption for UltimateGadget backups (`.ugbak`).
 *
 * These are health data, so the backup ZIP is encrypted with AES-256-GCM. The key is derived
 * from the user password with PBKDF2WithHmacSHA256 (random salt, 210 000 iterations). GCM provides
 * confidentiality AND integrity: a wrong password or a corrupted file makes the authentication tag
 * fail on decrypt ([WrongPasswordException]).
 *
 * Only the JDK's own `javax.crypto` is used; no extra dependencies.
 *
 * File layout (all big-endian, header is authenticated as GCM AAD):
 * ```
 *   offset  size  field
 *   0       4     magic  = "UGBK" (0x55 0x47 0x42 0x4B)
 *   4       1     version = 1
 *   5       1     reserved = 0
 *   6       16    salt (PBKDF2)
 *   22      12    iv   (GCM nonce)
 *   34      ...   ciphertext of the ZIP (GCM, 16-byte tag appended)
 * ```
 */
object UltimateBackupCrypto {

    private val MAGIC = byteArrayOf('U'.code.toByte(), 'G'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Int = 1

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000

    private const val HEADER_LEN = 4 + 1 + 1 + SALT_LEN + IV_LEN // 34

    /** Thrown when decryption fails the GCM auth check: wrong password or corrupted/altered file. */
    class WrongPasswordException(cause: Throwable?) :
        IOException("Contraseña incorrecta o fichero corrupto", cause)

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        try {
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun header(salt: ByteArray, iv: ByteArray): ByteArray {
        val h = ByteArray(HEADER_LEN)
        System.arraycopy(MAGIC, 0, h, 0, 4)
        h[4] = VERSION.toByte()
        h[5] = 0
        System.arraycopy(salt, 0, h, 6, SALT_LEN)
        System.arraycopy(iv, 0, h, 6 + SALT_LEN, IV_LEN)
        return h
    }

    /**
     * Encrypts [plaintext] with [password] and writes magic+header+ciphertext to [out].
     * The caller owns (and should close) [out].
     */
    fun encrypt(plaintext: ByteArray, password: CharArray, out: OutputStream) {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val head = header(salt, iv)
        cipher.updateAAD(head) // bind the header so it cannot be tampered with
        val ciphertext = cipher.doFinal(plaintext)

        out.write(head)
        out.write(ciphertext)
        out.flush()
    }

    /**
     * Reads the header from [input], derives the key from [password] and returns the decrypted
     * plaintext. Throws [WrongPasswordException] on a bad password / corrupted file, or [IOException]
     * if this is not an UltimateGadget backup.
     */
    fun decrypt(input: InputStream, password: CharArray): ByteArray {
        val din = DataInputStream(input)

        val magic = ByteArray(4)
        din.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw IOException("Este fichero no es una copia de UltimateGadget (.ugbak)")
        }
        val version = din.readUnsignedByte()
        if (version != VERSION) {
            throw IOException("Versión de copia no soportada: $version")
        }
        din.readUnsignedByte() // reserved
        val salt = ByteArray(SALT_LEN).also { din.readFully(it) }
        val iv = ByteArray(IV_LEN).also { din.readFully(it) }
        val ciphertext = din.readBytes() // rest of the stream

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header(salt, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException(e)
        } catch (e: javax.crypto.BadPaddingException) {
            throw WrongPasswordException(e)
        }
    }
}
