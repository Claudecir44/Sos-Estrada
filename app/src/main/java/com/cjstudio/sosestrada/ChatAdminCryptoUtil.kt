package com.cjstudio.sosestrada

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// AES-256-GCM do campo "conteudo" do Chat Admin — mesmo esquema do Caronas
// (prefixo "ENC1:" + base64(iv + cifrado)), chave própria do SOS. A mesma
// chave está no painel web (web-admin/app.js), pra conversa continuar de um
// lado pro outro. Protege contra leitura direta no Firestore/exportação;
// não contra quem lê o código (o repositório é público, como o painel).
object ChatAdminCryptoUtil {

    private const val CHAVE_BASE64 = "Ugj2YVu36MvXnPYbylsDwXzTKyXakyl+XR2iUqmFuS0="
    private const val PREFIXO_CIFRADO = "ENC1:"
    private const val TAMANHO_IV = 12
    private const val TAMANHO_TAG_BITS = 128

    private val chaveSecreta by lazy { SecretKeySpec(Base64.decode(CHAVE_BASE64, Base64.NO_WRAP), "AES") }

    fun criptografar(texto: String?): String? {
        if (texto.isNullOrEmpty()) return texto
        return try {
            val iv = ByteArray(TAMANHO_IV).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, chaveSecreta, GCMParameterSpec(TAMANHO_TAG_BITS, iv))
            PREFIXO_CIFRADO + Base64.encodeToString(iv + cipher.doFinal(texto.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) {
            texto
        }
    }

    fun descriptografar(armazenado: String?): String? {
        if (armazenado.isNullOrEmpty() || !armazenado.startsWith(PREFIXO_CIFRADO)) return armazenado
        return try {
            val dados = Base64.decode(armazenado.removePrefix(PREFIXO_CIFRADO), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, chaveSecreta, GCMParameterSpec(TAMANHO_TAG_BITS, dados.copyOfRange(0, TAMANHO_IV)))
            String(cipher.doFinal(dados.copyOfRange(TAMANHO_IV, dados.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            armazenado
        }
    }
}
