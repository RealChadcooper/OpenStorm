package com.openstorm.auto.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat

/**
 * Main car screen shown when OpenStorm launches on Android Auto.
 * Uses ListTemplate for driver-distraction compliance.
 *
 * Shows:
 * - Current radar station status
 * - Active alert count
 * - Navigation to alerts list
 */
class MainCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Radar status row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Nearest Radar")
                .addText("Searching for stations…")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(RadarCarScreen(carContext)) }
                .build()
        )

        // Alerts row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Weather Alerts")
                .addText("Tap to view active alerts")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(AlertsCarScreen(carContext)) }
                .build()
        )

        // Data attribution
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Data: NOAA / NWS")
                .addText("Public domain weather data")
                .build()
        )

        return ListTemplate.Builder()
            .setTitle("OpenStorm")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
