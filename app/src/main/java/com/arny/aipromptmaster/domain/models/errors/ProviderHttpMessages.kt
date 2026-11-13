package com.arny.aipromptmaster.domain.models.errors

/**
 * Maps provider HTTP failures to user-facing text.
 *
 * 403 with a Cloudflare/security-policy body means the request was blocked before reaching
 * the model API (observed from the Android emulator), so it must not look like a missing key.
 */
fun providerHttpMessage(status: Int, body: String? = null): String {
    val blocked = body != null && (
        body.contains("security policy", ignoreCase = true) ||
            body.contains("cloudflare", ignoreCase = true) ||
            body.contains("access denied", ignoreCase = true)
        )
    return when {
        status == 401 -> "Ключ провайдера не принят (401). Проверьте API-ключ в профиле провайдера."
        status == 403 && blocked ->
            "Провайдер заблокировал запрос (403, защита Cloudflare). Смените сеть или VPN; ключ здесь ни при чём."
        status == 403 -> "Доступ запрещён провайдером (403). Проверьте ключ и ограничения профиля."
        status == 404 -> "Провайдер не нашёл endpoint (404). Проверьте адрес API в профиле."
        status == 429 -> "Слишком много запросов (429). Повторите через минуту."
        status in 500..599 -> "Ошибка на стороне провайдера ($status). Повторите позже."
        else -> "Провайдер вернул ошибку $status."
    }
}
