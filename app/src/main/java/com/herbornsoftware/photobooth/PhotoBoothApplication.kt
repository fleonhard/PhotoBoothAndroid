package com.herbornsoftware.photobooth

import android.app.Activity
import android.app.Application
import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

class PhotoBoothApplication: Application() {

    lateinit var requestQueue: RequestQueue

    override fun onCreate() {
        super.onCreate()
        requestQueue =
            Volley.newRequestQueue(this)
    }
}

val Activity.app
    get() = application as PhotoBoothApplication