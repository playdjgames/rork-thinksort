package com.rork.thinksortandroid

import android.app.Application
import com.rork.thinksortandroid.services.NotificationService
import com.rork.thinksortandroid.services.PurchaseService

class ThinksortApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PurchaseService.configure(this)
        NotificationService.ensureChannel(this)
    }
}
