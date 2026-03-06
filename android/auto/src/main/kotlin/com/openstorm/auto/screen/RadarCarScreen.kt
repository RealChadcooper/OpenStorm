package com.openstorm.auto.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Radar status pane for Android Auto.
 * Shows current station info, product, and last update time.
 * Designed for quick glance while driving — no complex interactions.
 */
class RadarCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Station")
                    .addText("Nearest: auto-detecting…")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Product")
                    .addText("Base Reflectivity (N0Q)")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Last Update")
                    .addText("Loading…")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Coverage")
                    .addText("230 km range")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener { invalidate() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Radar Status")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
