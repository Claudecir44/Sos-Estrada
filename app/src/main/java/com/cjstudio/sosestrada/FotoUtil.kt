package com.cjstudio.sosestrada

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// Foto de perfil do admin guardada dentro do próprio documento admins/{uid}
// (texto base64 de um JPEG pequeno), porque o projeto não tem Firebase
// Storage (criar o bucket exige o plano Blaze). Recortada em quadrado e
// reduzida pra LADO px — fica em torno de 20–40 KB, bem abaixo do limite de
// 1 MB por documento do Firestore.
object FotoUtil {
    private const val LADO = 320
    private const val QUALIDADE = 80

    suspend fun paraBase64(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val original = carregar(context, uri)
        val menorLado = minOf(original.width, original.height)
        val quadrado = Bitmap.createBitmap(
            original, (original.width - menorLado) / 2, (original.height - menorLado) / 2, menorLado, menorLado
        )
        val reduzido = Bitmap.createScaledBitmap(quadrado, LADO, LADO, true)
        val bytes = ByteArrayOutputStream().use { saida ->
            reduzido.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, saida)
            saida.toByteArray()
        }
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // Mostra a foto em círculo; sem foto, o ícone padrão.
    fun mostrar(imagem: ImageView, fotoBase64: String?) {
        val bytes = fotoBase64?.takeIf { it.isNotEmpty() }?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
        if (bytes == null) {
            imagem.setImageResource(R.drawable.ic_admin)
            return
        }
        Glide.with(imagem).load(bytes).circleCrop().into(imagem)
    }

    private fun carregar(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Já aplica a rotação da câmera (EXIF) e reduz na leitura.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maior = maxOf(info.size.width, info.size.height)
                if (maior > 1600) decoder.setTargetSampleSize(maior / 1600)
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                ?: BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
        }
}
