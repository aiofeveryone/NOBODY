package com.aoai.autobahn

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class AutobahnSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return AutobahnScreen(carContext)
    }
}
