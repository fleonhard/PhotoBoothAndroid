package com.herbornsoftware.photobooth.core

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
//
//class RestService private constructor(context: Context) {
//    private val queue: RequestQueue = Volley.newRequestQueue(context)
//
//    companion object {
//        private var INSTANCE: RestService? = null
//        fun getInstance(context: Context) = INSTANCE?:let {
//            INSTANCE = RestService(context)
//            INSTANCE!!
//        }
//    }
//
//    fun get(endpoint: Endpoint, errorCallback: (String?) -> Unit, callback: (JSONObject) -> Unit = {}) {
//        get(endpoint.url, errorCallback, callback)
//    }
//
//    fun get(url: String, errorCallback: (String?) -> Unit, callback: (JSONObject) -> Unit = {}) {
//        queue.add(JsonObjectRequest(
//            Request.Method.GET,
//            url,
//            null,
//            Response.Listener { callback(it) },
//            Response.ErrorListener { errorCallback(it.localizedMessage) }
//        ))
//    }
//}