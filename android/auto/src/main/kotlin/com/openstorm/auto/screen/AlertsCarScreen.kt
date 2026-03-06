package com.openstorm.auto.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Alerts list for Android Auto.
 * Limited to 6 items max for driver distraction compliance.
 * Each item shows alert type and area — tap for full text via LongMessageTemplate.
 */
class AlertsCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Placeholder — will be populated from AlertRepository
        listBuilder.addItem(
            Row.Builder()
                .setTitle("No Active Alerts")
                .addText("Your area is clear")
                .build()
        )

        return ListTemplate.Builder()
            .setTitle("Weather Alerts")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
