package com.aoai.autobahn

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class AutobahnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kakao Map SDK 초기화
        KakaoMapSdk.init(this, "3d1ef088d0e693d7bb46101b0b6d685a")
    }
}
