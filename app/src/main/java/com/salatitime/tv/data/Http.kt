package com.salatitime.tv.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    const val APP_UA = "SalatiTimeTV/1.0 (Android TV)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
