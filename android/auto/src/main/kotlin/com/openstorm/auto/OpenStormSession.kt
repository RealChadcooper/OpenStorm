package com.openstorm.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.openstorm.auto.screen.MainCarScreen

class OpenStormSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return MainCarScreen(carContext)
    }
}
