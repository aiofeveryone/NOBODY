package com.aoai.autobahn

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat

class AutobahnScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Current Location")
                    .addText("Exploring the road ahead")
                    .setMetadata(
                        Metadata.Builder()
                            .setPlace(
                                Place.Builder(CarLocation.create(0.0, 0.0))
                                    .setMarker(PlaceMarker.Builder().build())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Premium Destinations")
                    .addText("Search for your next journey")
                    .setOnClickListener { 
                        // Navigation to search screen on car
                    }
                    .build()
            )
            .build()

        return PlaceListMapTemplate.Builder()
            .setItemList(itemList)
            .setTitle("AUTOBAHN")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Settings")
                            .setOnClickListener { /* Open settings */ }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
