package com.herbornsoftware.photobooth.core

import android.app.Activity
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.herbornsoftware.photobooth.app
import org.json.JSONObject
import java.net.URL

typealias ErrorListener = (message: String?) -> Unit
typealias ResponseListener<T> = (response: T) -> Unit

object GoPro {

    private val TAG = GoPro::class.java.simpleName

    private enum class MediaInfo {
        FOLDER,
        SIZE,
        FILE
    }

//    fun downloadLastMedia(path: String = "", customFilename: String? = null) {
//        if(!isRecording()) {
//            if(path.isNotBlank()) {
//                Log.d(TAG, "Filename: ${getMediaInfo(MediaInfo.FILE)} Size: ${getMediaInfo(MediaInfo.SIZE)}")
//                val fileName = customFilename?:"${getMediaInfo(MediaInfo.FOLDER)}-${getMediaInfo(MediaInfo.FILE)}"
//                get
//            } else {
//                Log.d(TAG, "Filename: ${getInfoFromURL(path).second} Size: ${getMediaInfo(MediaInfo.SIZE)}")
//                val fileName = customFilename?:"${getInfoFromURL(path).first}-${getInfoFromURL(path).second}"
//            }
//        }
//    }
//
//    fun getMediaList(onError: (String?) -> Unit, callback: (mediaList: List<String>) -> Unit) {
//        restService.get(Endpoint.MEDIA_LIST, onError) { response ->
//
//        }
//    }

//    private fun getInfoFromURL(path: String): Pair<String, String> {
//
//    }
//
//    private fun getMediaInfo(s: MediaInfo): String {
//        TODO("Not yet implemented")
//    }
//
//    fun isRecording(): Boolean {
//        TODO("Not yet implemented")
//    }

    fun deleteFile(activity: Activity, file: GoProFile, errorCallback: ErrorListener, callback: ResponseListener<Unit> = {}) {
        request(activity, "gp/gpControl/command/storage/delete?p=${file.dir}/${file.name}", errorCallback) { response ->
            Log.d(TAG, "Response: $response")
            callback(Unit)
        }
    }

    fun startRecord(activity: Activity, errorCallback: ErrorListener, callback: ResponseListener<Unit> = {}) {
        request(activity, "gp/gpControl/command/shutter?p=1", errorCallback) { response ->
            Log.d(TAG, "Response: $response")
            callback(Unit)
        }
    }

    data class GoProFile(val name: String, val dir: String, val created: Long, val updated: Long, val size: Long, val url: String)

    fun getLastMedia(activity: Activity, errorCallback: ErrorListener, callback: ResponseListener<GoProFile>) {
        getMediaList(activity, errorCallback) { files -> callback(files.sortedByDescending { it.created }[0]) }
    }


    fun getMediaList(activity: Activity, errorCallback: ErrorListener, callback: ResponseListener<List<GoProFile>>) {
        val result = mutableListOf<GoProFile>()
        request(activity, "gp/gpMediaList", errorCallback) { response ->
            val dirs = response.getJSONArray("media")
            for (i in (0 until dirs.length())) {
                val dir = dirs.getJSONObject(i)
                val dirName = dir.getString("d") ?: ""
                val files = dir.getJSONArray("fs")
                for (k in (0 until files.length())) {
                    val jsonFile = files.getJSONObject(k)
                    result.add(convertGoProFile(jsonFile, dirName))
                }
            }
            callback(result.toList())
        }
    }

    private fun convertGoProFile(jsonFile: JSONObject, dirName: String): GoProFile {
        val fileName = jsonFile.getString("n")
        val file = GoProFile(
            name = fileName,
            dir = dirName,
            created = jsonFile.getLong("cre"),
            updated = jsonFile.getLong("mod"),
            size = jsonFile.getLong("s"),
            url = createURL("videos/DCIM/$dirName/$fileName")
        )
        return file
    }

    private fun request(activity: Activity, relativeUrl: String, errorCallback: ErrorListener, callback: ResponseListener<JSONObject>) =
        activity.app.requestQueue.add(JsonObjectRequest(
            Request.Method.GET,
            createURL(relativeUrl),
            null,
            Response.Listener { callback(it) },
            Response.ErrorListener { errorCallback(it.toString()) }
        )).also { Log.d(TAG, "added Request") }


    private fun createURL(relativeUrl: String) = "http://10.5.5.9/$relativeUrl"
}


sealed class Endpoint(relativeUrl: String) {
    val url = "http://10.5.5.9/gp/$relativeUrl"

    object TRIGGER_ON : Endpoint("gpControl/command/shutter?p=1")
    object TRIGGER_OFF : Endpoint("gpControl/command/shutter?p=0")

    object CH_DEFAULT_BOOT_MODE_VIDEO : Endpoint("gpControl/setting/53/0")
    object CH_DEFAULT_BOOT_MODE_PHOTO : Endpoint("gpControl/setting/53/1")
    object CH_DEFAULT_BOOT_MODE_MULTI_SHOT : Endpoint("gpControl/setting/53/2")

    object CH_PRIMARY_MODE_VIDEO : Endpoint("gpControl/command/mode?p=0")
    object CH_PRIMARY_MODE_PHOTO : Endpoint("gpControl/command/mode?p=1")
    object CH_PRIMARY_MODE_MULTI_SHOT : Endpoint("gpControl/command/mode?p=2")

    object CH_SECONDARY_MODE_VIDEO_VIDEO : Endpoint("gpControl/command/sub_mode?mode=0&sub_mode=0")
    object CH_SECONDARY_MODE_TIMELAPSE_VIDEO : Endpoint("gpControl/command/sub_mode?mode=0&sub_mode=1")
    object CH_SECONDARY_MODE_VIDEO_PHOTO_PHOTO : Endpoint("gpControl/command/sub_mode?mode=0&sub_mode=2")
    object CH_SECONDARY_MODE_LOOPING_VIDEO : Endpoint("gpControl/command/sub_mode?mode=0&sub_mode=3")
    object CH_SECONDARY_MODE_TIMEWRAP_VIDEO : Endpoint("gpControl/command/sub_mode?mode=0&sub_mode=4")
    object CH_SECONDARY_MODE_SINGE_PHOTO : Endpoint("gpControl/command/sub_mode?mode=1&sub_mode=1")
    object CH_SECONDARY_MODE_NIGHT_PHOTO : Endpoint("gpControl/command/sub_mode?mode=1&sub_mode=2")
    object CH_SECONDARY_MODE_BURST_MULTISHOT : Endpoint("gpControl/command/sub_mode?mode=2&sub_mode=0")
    object CH_SECONDARY_MODE_TIMELAPSE_MULTISHOT : Endpoint("gpControl/command/sub_mode?mode=2&sub_mode=1")
    object CH_SECONDARY_MODE_NIGHT_LAPSE_MULTI_SHOT : Endpoint("gpControl/command/sub_mode?mode=2&sub_mode=2")

    object CREATE_TAG : Endpoint("gpControl/command/storage/tag_moment")

    object LOCATE_ON : Endpoint("gpControl/command/system/locate?p=1")
    object LOCATE_OFF : Endpoint("gpControl/command/system/locate?p=0")

    object POWER_ON : Endpoint("gpControl/command/system/sleep")

//        Power On: Send a Wake On Lan command with the parameters: IP address: 10.5.5.9, Subnet Mask 255.255.255.0, Port 9.

    class ZOOM(percent: Int) : Endpoint("gpControl/command/digital_zoom?range_pcnt=$percent)")

    class PAIR(name: String) : Endpoint("gpControl/command/wireless/pair/complete?success=1&deviceName=$name")

    object CH_VIDEO_RESOLUTION_4K : Endpoint("gpControl/setting/2/1")
    object CH_VIDEO_RESOLUTION_4K_4_3 : Endpoint("gpControl/setting/2/18")
    object CH_VIDEO_RESOLUTION_2_7K : Endpoint("gpControl/setting/2/4")
    object CH_VIDEO_RESOLUTION_2_7K_4_3 : Endpoint("gpControl/setting/2/3")
    object CH_VIDEO_RESOLUTION_1440p : Endpoint("gpControl/setting/2/7")
    object CH_VIDEO_RESOLUTION_1080p : Endpoint("gpControl/setting/2/9")
    object CH_VIDEO_RESOLUTION_960p : Endpoint("gpControl/setting/2/10")
    object CH_VIDEO_RESOLUTION_720p : Endpoint("gpControl/setting/2/12")

    object CH_FRAME_RATE_240_FPS : Endpoint("gpControl/setting/3/0")
    object CH_FRAME_RATE_120_FPS : Endpoint("gpControl/setting/3/1")
    object CH_FRAME_RATE_100_FPS : Endpoint("gpControl/setting/3/2")
    object CH_FRAME_RATE_90_FPS : Endpoint("gpControl/setting/3/3")
    object CH_FRAME_RATE_80_FPS : Endpoint("gpControl/setting/3/4")
    object CH_FRAME_RATE_60_FPS : Endpoint("gpControl/setting/3/5")
    object CH_FRAME_RATE_50_FPS : Endpoint("gpControl/setting/3/6")
    object CH_FRAME_RATE_48_FPS : Endpoint("gpControl/setting/3/7")
    object CH_FRAME_RATE_30_FPS : Endpoint("gpControl/setting/3/8")
    object CH_FRAME_RATE_25_FPS : Endpoint("gpControl/setting/3/9")

    object CH_FOV_Wide : Endpoint("gpControl/setting/4/0")
    object CH_FOV_SuperView : Endpoint("gpControl/setting/4/3")
    object CH_FOV_Linear : Endpoint("gpControl/setting/4/4")

    object CH_ASPECT_RATIO_4_3 : Endpoint("gpControl/setting/108/0")
    object CH_ASPECT_RATIO_16_9 : Endpoint("gpControl/setting/108/1")

    object CH_LOW_LIGHT_ON : Endpoint("gpControl/setting/8/1")
    object CH_LOW_LIGHT_OFF : Endpoint("gpControl/setting/8/0")

    object CH_VIDEO_LOOPING_DURATION_MAX : Endpoint("gpControl/setting/6/0")
    object CH_VIDEO_LOOPING_DURATION_5MIN : Endpoint("gpControl/setting/6/1")
    object CH_VIDEO_LOOPING_DURATION_20MIN : Endpoint("gpControl/setting/6/2")
    object CH_VIDEO_LOOPING_DURATION_60MIN : Endpoint("gpControl/setting/6/3")
    object CH_VIDEO_LOOPING_DURATION_120MIN : Endpoint("gpControl/setting/6/4")

    object CH_VIDEO_PHOTO_INTERVAL_5 : Endpoint("gpControl/setting/7/1")
    object CH_VIDEO_PHOTO_INTERVAL_10 : Endpoint("gpControl/setting/7/2")
    object CH_VIDEO_PHOTO_INTERVAL_30 : Endpoint("gpControl/setting/7/3")
    object CH_VIDEO_PHOTO_INTERVAL_60MIN : Endpoint("gpControl/setting/7/4")

    object CH_VIDEO_TIMELAPSE_INTERVAL_0_5 : Endpoint("gpControl/setting/5/0")
    object CH_VIDEO_TIMELAPSE_INTERVAL_1 : Endpoint("gpControl/setting/5/1")
    object CH_VIDEO_TIMELAPSE_INTERVAL_2 : Endpoint("gpControl/setting/5/2")
    object CH_VIDEO_TIMELAPSE_INTERVAL_5 : Endpoint("gpControl/setting/5/3")
    object CH_VIDEO_TIMELAPSE_INTERVAL_10 : Endpoint("gpControl/setting/5/4")
    object CH_VIDEO_TIMELAPSE_INTERVAL_30 : Endpoint("gpControl/setting/5/5")
    object CH_VIDEO_TIMELAPSE_INTERVAL_60 : Endpoint("gpControl/setting/5/6")

    object CH_TIMEWRAP_SPEED_2X : Endpoint("gpControl/setting/111/7")
    object CH_TIMEWRAP_SPEED_5X : Endpoint("gpControl/setting/111/8")
    object CH_TIMEWRAP_SPEED_10X : Endpoint("gpControl/setting/111/9")
    object CH_TIMEWRAP_SPEED_15X : Endpoint("gpControl/setting/111/0")
    object CH_TIMEWRAP_SPEED_30X : Endpoint("gpControl/setting/111/1")

    object VIDEO_STABILISATION_ON : Endpoint("gpControl/setting/78/1")
    object VIDEO_STABILISATION_OFF : Endpoint("gpControl/setting/78/0")

    object CH_SHORT_CLIP_LENGTH_OFF : Endpoint("gpControl/setting/107/0")
    object CH_SHORT_CLIP_LENGTH_15_S : Endpoint("gpControl/setting/107/1")
    object CH_SHORT_CLIP_LENGTH_30_S : Endpoint("gpControl/setting/107/2")

    object PROTUNE_OFF : Endpoint("gpControl/setting/10/0")
    object PROTUNE_ON : Endpoint("gpControl/setting/10/1")

    object CH_WHITE_BALANCE_AUTO : Endpoint("gpControl/setting/11/0")
    object CH_WHITE_BALANCE_2300K : Endpoint("gpControl/setting/11/8")
    object CH_WHITE_BALANCE_2800K : Endpoint("gpControl/setting/11/9")
    object CH_WHITE_BALANCE_3200K : Endpoint("gpControl/setting/11/10")
    object CH_WHITE_BALANCE_4000K : Endpoint("gpControl/setting/11/5")
    object CH_WHITE_BALANCE_4500K : Endpoint("gpControl/setting/11/11")
    object CH_WHITE_BALANCE_5000K : Endpoint("gpControl/setting/11/12")
    object CH_WHITE_BALANCE_5500K : Endpoint("gpControl/setting/11/2")
    object CH_WHITE_BALANCE_6000K : Endpoint("gpControl/setting/11/7")
    object CH_WHITE_BALANCE_6500K : Endpoint("gpControl/setting/11/3")
    object CH_WHITE_BALANCE_NATIVE : Endpoint("gpControl/setting/11/4")

    object CH_COLOR_GOPRO : Endpoint("gpControl/setting/12/0")
    object CH_COLOR_FLAT : Endpoint("gpControl/setting/12/1")

    object CH_ISO_LIMIT_6400 : Endpoint("gpControl/setting/13/0")
    object CH_ISO_LIMIT_1600 : Endpoint("gpControl/setting/13/1")
    object CH_ISO_LIMIT_400 : Endpoint("gpControl/setting/13/2")
    object CH_ISO_LIMIT_3200 : Endpoint("gpControl/setting/13/3")
    object CH_ISO_LIMIT_800 : Endpoint("gpControl/setting/13/4")
    object CH_ISO_LIMIT_200 : Endpoint("gpControl/setting/13/7")
    object CH_ISO_LIMIT_100 : Endpoint("gpControl/setting/13/8")

    object CH_ISO_MODE_MAX : Endpoint("gpControl/setting/74/0")
    object CH_ISO_MODE_LOCK : Endpoint("gpControl/setting/74/1")

    object CH_SHARPNESS_HIGH : Endpoint("gpControl/setting/14/0")
    object CH_SHARPNESS_MED : Endpoint("gpControl/setting/14/1")
    object CH_SHARPNESS_LOW : Endpoint("gpControl/setting/14/2")

    object CH_MANUAL_VIDEO_EXPOSURE_AUTO_MODE : Endpoint("gpControl/setting/73/0")

    object CH_FPS_24_1_24 : Endpoint("gpControl/setting/73/3")
    object CH_FPS_24_1_48 : Endpoint("gpControl/setting/73/6")
    object CH_FPS_24_1_96 : Endpoint("gpControl/setting/73/11")

    object CH_FPS_30_1_30 : Endpoint("gpControl/setting/73/5")
    object CH_FPS_30_1_60 : Endpoint("gpControl/setting/73/8")
    object CH_FPS_30_1_120 : Endpoint("gpControl/setting/73/13")

    object CH_FPS_48_1_48 : Endpoint("gpControl/setting/73/6")
    object CH_FPS_48_1_96 : Endpoint("gpControl/setting/73/11")
    object CH_FPS_48_1_192 : Endpoint("gpControl/setting/73/16")

    object CH_FPS_60_1_60 : Endpoint("gpControl/setting/73/8")
    object CH_FPS_60_1_120 : Endpoint("gpControl/setting/73/13")
    object CH_FPS_60_1_240 : Endpoint("gpControl/setting/73/18")

    object CH_FPS_90_1_90 : Endpoint("gpControl/setting/73/10")
    object CH_FPS_90_1_180 : Endpoint("gpControl/setting/73/15")
    object CH_FPS_90_1_360 : Endpoint("gpControl/setting/73/20")

    object CH_FPS_120_1_120 : Endpoint("gpControl/setting/73/13")
    object CH_FPS_120_1_240 : Endpoint("gpControl/setting/73/18")
    object CH_FPS_120_1_480 : Endpoint("gpControl/setting/73/22")

    object CH_FPS_240_1_120 : Endpoint("gpControl/setting/73/18")
    object CH_FPS_240_1_240 : Endpoint("gpControl/setting/73/22")
    object CH_FPS_240_1_480 : Endpoint("gpControl/setting/73/23")

    object CH_EV_STEPS_P2 : Endpoint("gpControl/setting/15/0")
    object CH_EV_STEPS_P1_5 : Endpoint("gpControl/setting/15/1")
    object CH_EV_STEPS_P1 : Endpoint("gpControl/setting/15/2")
    object CH_EV_STEPS_P0_5 : Endpoint("gpControl/setting/15/3")
    object CH_EV_STEPS_0 : Endpoint("gpControl/setting/15/4")
    object CH_EV_STEPS_M0_5 : Endpoint("gpControl/setting/15/5")
    object CH_EV_STEPS_M1 : Endpoint("gpControl/setting/15/6")
    object CH_EV_STEPS_M1_5 : Endpoint("gpControl/setting/15/7")
    object CH_EV_STEPS_M2 : Endpoint("gpControl/setting/15/8")

    object CH_PHOTO_RESOLUTION_12MP_WIDE : Endpoint("gpControl/setting/17/0")
    object CH_PHOTO_RESOLUTION_12MP_LINEAR : Endpoint("gpControl/setting/17/10")
    object CH_PHOTO_RESOLUTION_12MP_MEDIUM : Endpoint("gpControl/setting/17/8")
    object CH_PHOTO_RESOLUTION_12MP_NARROW : Endpoint("gpControl/setting/17/9")

    object CH_EXPOSURE_TIME_NIGHTPHOTO_Auto : Endpoint("gpControl/setting/19/0")
    object CH_EXPOSURE_TIME_NIGHTPHOTO_2 : Endpoint("gpControl/setting/19/1")
    object CH_EXPOSURE_TIME_NIGHTPHOTO_5 : Endpoint("gpControl/setting/19/2")
    object CH_EXPOSURE_TIME_NIGHTPHOTO_10 : Endpoint("gpControl/setting/19/3")
    object CH_EXPOSURE_TIME_NIGHTPHOTO_15 : Endpoint("gpControl/setting/19/4")
    object CH_EXPOSURE_TIME_NIGHTPHOTO_20 : Endpoint("gpControl/setting/19/5")
    object CH_EXPOSURE_TIME_NIGHTPHOTO_30 : Endpoint("gpControl/setting/19/6")

    object RAW_PHOTO_ON : Endpoint("gpControl/setting/82/1")
    object RAW_PHOTO_OFF : Endpoint("gpControl/setting/82/0")

    object RAW_NIGHT_PHOTO_ON : Endpoint("gpControl/setting/98/1")
    object RAW_NIGHT_PHOTO_OFF : Endpoint("gpControl/setting/98/0")

    object SUPER_PHOTO_OFF : Endpoint("gpControl/setting/109/0")
    object SUPER_PHOTO_AUTO : Endpoint("gpControl/setting/109/1")
    object SUPER_PHOTO_HDR_ONLY : Endpoint("gpControl/setting/109/2")

    object CH_PHOTO_TIMER_OFF : Endpoint("gpControl/setting/105/0")
    object CH_PHOTO_TIMER_3_S : Endpoint("gpControl/setting/105/1")
    object CH_PHOTO_TIMER_10_S : Endpoint("gpControl/setting/105/2")

//        ProTune:
//        off: http://10.5.5.9/gp/gpControl/setting/21/0
//        on: http://10.5.5.9/gp/gpControl/setting/21/1
//        White Balance:
//        Auto: http://10.5.5.9/gp/gpControl/setting/22/0
//        3000k: http://10.5.5.9/gp/gpControl/setting/22/1
//        2300K: http://10.5.5.9/gp/gpControl/setting/22/8
//        2800K: http://10.5.5.9/gp/gpControl/setting/22/9
//        3200K: http://10.5.5.9/gp/gpControl/setting/22/10
//        4000k: http://10.5.5.9/gp/gpControl/setting/22/5
//        4500K: http://10.5.5.9/gp/gpControl/setting/22/11
//        4800k: http://10.5.5.9/gp/gpControl/setting/22/6
//        5500k: http://10.5.5.9/gp/gpControl/setting/22/2
//        6000k: http://10.5.5.9/gp/gpControl/setting/22/7
//        6500k: http://10.5.5.9/gp/gpControl/setting/22/3
//        Native: http://10.5.5.9/gp/gpControl/setting/22/4
//        Color:
//        GOPRO: http://10.5.5.9/gp/gpControl/setting/23/0
//        Flat: http://10.5.5.9/gp/gpControl/setting/23/1
//        ISO Limit:
//        800: http://10.5.5.9/gp/gpControl/setting/24/0
//        400: http://10.5.5.9/gp/gpControl/setting/24/1
//        200: http://10.5.5.9/gp/gpControl/setting/24/2
//        100: http://10.5.5.9/gp/gpControl/setting/24/3
//        ISO Min:
//        800: http://10.5.5.9/gp/gpControl/setting/75/0
//        400: http://10.5.5.9/gp/gpControl/setting/75/1
//        200: http://10.5.5.9/gp/gpControl/setting/75/2
//        100: http://10.5.5.9/gp/gpControl/setting/75/3
//        Sharpness:
//        High: http://10.5.5.9/gp/gpControl/setting/25/0
//        Med: http://10.5.5.9/gp/gpControl/setting/25/1
//        Low: http://10.5.5.9/gp/gpControl/setting/25/2
//        EV Steps:
//        Value	URL
//        +2	http://10.5.5.9/gp/gpControl/setting/26/0
//        +1.5	http://10.5.5.9/gp/gpControl/setting/26/1
//        +1	http://10.5.5.9/gp/gpControl/setting/26/2
//        +0.5	http://10.5.5.9/gp/gpControl/setting/26/3
//        0	http://10.5.5.9/gp/gpControl/setting/26/4
//        -0.5	http://10.5.5.9/gp/gpControl/setting/26/5
//        -1	http://10.5.5.9/gp/gpControl/setting/26/6
//        -1.5	http://10.5.5.9/gp/gpControl/setting/26/7
//        -2	http://10.5.5.9/gp/gpControl/setting/26/8
//        Protune Shutter:
//        Auto: http://10.5.5.9/gp/gpControl/setting/97/0
//        1/125: http://10.5.5.9/gp/gpControl/setting/97/1
//        1/250: http://10.5.5.9/gp/gpControl/setting/97/2
//        1/500: http://10.5.5.9/gp/gpControl/setting/97/3
//        1/1000: http://10.5.5.9/gp/gpControl/setting/97/4
//        1/2000: http://10.5.5.9/gp/gpControl/setting/97/5
//        MultiShot:
//        Photo Resolution for MultiShot Modes:
//        12MP Wide: http://10.5.5.9/gp/gpControl/setting/17/0
//        12MP Linear: http://10.5.5.9/gp/gpControl/setting/17/10
//        12MP Medium: http://10.5.5.9/gp/gpControl/setting/17/8
//        12MP Narrow: http://10.5.5.9/gp/gpControl/setting/17/9
//        Exposure time for NightLapse:
//        Auto: http://10.5.5.9/gp/gpControl/setting/31/0
//        2: http://10.5.5.9/gp/gpControl/setting/31/1
//        5: http://10.5.5.9/gp/gpControl/setting/31/2
//        10: http://10.5.5.9/gp/gpControl/setting/31/3
//        15: http://10.5.5.9/gp/gpControl/setting/31/4
//        20: http://10.5.5.9/gp/gpControl/setting/31/5
//        30: http://10.5.5.9/gp/gpControl/setting/31/6
//        Interval for NightLapse
//        Continuous: http://10.5.5.9/gp/gpControl/setting/32/0
//        4s: http://10.5.5.9/gp/gpControl/setting/32/4
//        5s: http://10.5.5.9/gp/gpControl/setting/32/5
//        10s: http://10.5.5.9/gp/gpControl/setting/32/10
//        15s: http://10.5.5.9/gp/gpControl/setting/32/15
//        20s: http://10.5.5.9/gp/gpControl/setting/32/20
//        30s: http://10.5.5.9/gp/gpControl/setting/32/30
//        1m: http://10.5.5.9/gp/gpControl/setting/32/60
//        2m: http://10.5.5.9/gp/gpControl/setting/32/120
//        5m: http://10.5.5.9/gp/gpControl/setting/32/300
//        30m: http://10.5.5.9/gp/gpControl/setting/32/1800
//        60m: http://10.5.5.9/gp/gpControl/setting/32/3600
//        Timelapse Interval (TIMELAPSE MODE on MultiShot):
//        0.5: http://10.5.5.9/gp/gpControl/setting/30/0
//        1: http://10.5.5.9/gp/gpControl/setting/30/1
//        2: http://10.5.5.9/gp/gpControl/setting/30/2
//        5: http://10.5.5.9/gp/gpControl/setting/30/5
//        10: http://10.5.5.9/gp/gpControl/setting/30/10
//        30: http://10.5.5.9/gp/gpControl/setting/30/30
//        60: http://10.5.5.9/gp/gpControl/setting/30/60
//        Burst Rate:
//        3/1: http://10.5.5.9/gp/gpControl/setting/29/0
//        5/1: http://10.5.5.9/gp/gpControl/setting/29/1
//        10/1: http://10.5.5.9/gp/gpControl/setting/29/2
//        10/2: http://10.5.5.9/gp/gpControl/setting/29/3
//        10/3: http://10.5.5.9/gp/gpControl/setting/29/4
//        30/1: http://10.5.5.9/gp/gpControl/setting/29/5
//        30/2: http://10.5.5.9/gp/gpControl/setting/29/6
//        30/3: http://10.5.5.9/gp/gpControl/setting/29/7
//        30/6: http://10.5.5.9/gp/gpControl/setting/29/8
//        RAW Photo TimeLapse:
//        On: http://10.5.5.9/gp/gpControl/setting/94/1
//        Off: http://10.5.5.9/gp/gpControl/setting/94/0
//        RAW Photo NightLapse:
//        On: http://10.5.5.9/gp/gpControl/setting/99/1
//        Off: http://10.5.5.9/gp/gpControl/setting/99/0
//        WDR TimeLapse:
//        On: http://10.5.5.9/gp/gpControl/setting/93/1
//        Off: http://10.5.5.9/gp/gpControl/setting/93/0
//        ProTune:
//        off: http://10.5.5.9/gp/gpControl/setting/34/0
//        on: http://10.5.5.9/gp/gpControl/setting/34/1
//        White Balance:
//        Auto: http://10.5.5.9/gp/gpControl/setting/35/0
//        2300K: http://10.5.5.9/gp/gpControl/setting/35/8
//        2800K: http://10.5.5.9/gp/gpControl/setting/35/9
//        3000k: http://10.5.5.9/gp/gpControl/setting/35/1
//        3200K: http://10.5.5.9/gp/gpControl/setting/35/10
//        4000k: http://10.5.5.9/gp/gpControl/setting/35/5
//        4500K: http://10.5.5.9/gp/gpControl/setting/35/11
//        4800k: http://10.5.5.9/gp/gpControl/setting/35/6
//        5500k: http://10.5.5.9/gp/gpControl/setting/35/2
//        6000k: http://10.5.5.9/gp/gpControl/setting/35/7
//        6500k: http://10.5.5.9/gp/gpControl/setting/35/3
//        Native: http://10.5.5.9/gp/gpControl/setting/35/4
//        Color:
//        GOPRO: http://10.5.5.9/gp/gpControl/setting/36/0
//        Flat: http://10.5.5.9/gp/gpControl/setting/36/1
//        ISO Limit:
//        800: http://10.5.5.9/gp/gpControl/setting/37/0
//        400: http://10.5.5.9/gp/gpControl/setting/37/1
//        200: http://10.5.5.9/gp/gpControl/setting/37/2
//        100: http://10.5.5.9/gp/gpControl/setting/37/3
//        ISO Min:
//        800: http://10.5.5.9/gp/gpControl/setting/76/0
//        400: http://10.5.5.9/gp/gpControl/setting/76/1
//        200: http://10.5.5.9/gp/gpControl/setting/76/2
//        100: http://10.5.5.9/gp/gpControl/setting/76/3
//        Sharpness:
//        High: http://10.5.5.9/gp/gpControl/setting/38/0
//        Med: http://10.5.5.9/gp/gpControl/setting/38/1
//        Low: http://10.5.5.9/gp/gpControl/setting/38/2
//        EV Steps:
//        Value	URL
//        +2	http://10.5.5.9/gp/gpControl/setting/39/0
//        +1.5	http://10.5.5.9/gp/gpControl/setting/39/1
//        +1	http://10.5.5.9/gp/gpControl/setting/39/2
//        +0.5	http://10.5.5.9/gp/gpControl/setting/39/3
//        0	http://10.5.5.9/gp/gpControl/setting/39/4
//        -0.5	http://10.5.5.9/gp/gpControl/setting/39/5
//        -1	http://10.5.5.9/gp/gpControl/setting/39/6
//        -1.5	http://10.5.5.9/gp/gpControl/setting/39/7
//        -2	http://10.5.5.9/gp/gpControl/setting/39/8

//        General Camera Settings:
//        Orientation:
//        Up: http://10.5.5.9/gp/gpControl/setting/52/1
//        Down: http://10.5.5.9/gp/gpControl/setting/52/2
//        Gyro based: http://10.5.5.9/gp/gpControl/setting/52/0
//        Quick Capture:
//        On: http://10.5.5.9/gp/gpControl/setting/54/1
//        Off: http://10.5.5.9/gp/gpControl/setting/54/0
//        GPS Tag:
//        On: http://10.5.5.9/gp/gpControl/setting/83/1
//        Off: http://10.5.5.9/gp/gpControl/setting/83/0
//        Voice control:
//        On: http://10.5.5.9/gp/gpControl/setting/86/1
//        Off: http://10.5.5.9/gp/gpControl/setting/86/0
//        LEDs on HERO6 Black:
//        Off: http://10.5.5.9/gp/gpControl/setting/91/0
//        On: http://10.5.5.9/gp/gpControl/setting/91/2
//        Front off: http://10.5.5.9/gp/gpControl/setting/91/1
//        Camera system language:
//        English: http://10.5.5.9/gp/gpControl/setting/84/0
//        Chinese: http://10.5.5.9/gp/gpControl/setting/84/1
//        German: http://10.5.5.9/gp/gpControl/setting/84/2
//        Italian: http://10.5.5.9/gp/gpControl/setting/84/3
//        Spanish: http://10.5.5.9/gp/gpControl/setting/84/4
//        Japanese: http://10.5.5.9/gp/gpControl/setting/84/5
//        French: http://10.5.5.9/gp/gpControl/setting/84/6
//        Voice control language:
//        English - US: http://10.5.5.9/gp/gpControl/setting/85/0
//        English - UK: http://10.5.5.9/gp/gpControl/setting/85/1
//        English - AUS: http://10.5.5.9/gp/gpControl/setting/85/2
//        German: http://10.5.5.9/gp/gpControl/setting/85/3
//        French: http://10.5.5.9/gp/gpControl/setting/85/4
//        Italian: http://10.5.5.9/gp/gpControl/setting/85/5
//        Spanish: http://10.5.5.9/gp/gpControl/setting/85/6
//        Spanish - NA: http://10.5.5.9/gp/gpControl/setting/85/7
//        Chinese: http://10.5.5.9/gp/gpControl/setting/85/8
//        Japanese: http://10.5.5.9/gp/gpControl/setting/85/9
//        Beeps:
//        Off: http://10.5.5.9/gp/gpControl/setting/87/0
//        50%: http://10.5.5.9/gp/gpControl/setting/87/50
//        XY%: http://10.5.5.9/gp/gpControl/setting/87/XY
//        Full: http://10.5.5.9/gp/gpControl/setting/87/100
//        Video Format:
//        NTSC: http://10.5.5.9/gp/gpControl/setting/57/0
//        PAL: http://10.5.5.9/gp/gpControl/setting/57/1
//        On Screen Display:
//        On: http://10.5.5.9/gp/gpControl/setting/58/1
//        Off: http://10.5.5.9/gp/gpControl/setting/58/0
//        LCD Brightness:
//        Low: http://10.5.5.9/gp/gpControl/setting/88/10
//        50%: http://10.5.5.9/gp/gpControl/setting/88/50
//        XY%: http://10.5.5.9/gp/gpControl/setting/88/XY
//        Full: http://10.5.5.9/gp/gpControl/setting/88/100
//        LCD Timeout sleep:
//        LCD Never sleep: http://10.5.5.9/gp/gpControl/setting/51/0
//        LCD 1min sleep timeout: http://10.5.5.9/gp/gpControl/setting/51/1
//        LCD 2min sleep timeout: http://10.5.5.9/gp/gpControl/setting/51/2
//        LCD 3min sleep timeout: http://10.5.5.9/gp/gpControl/setting/51/3
//        Auto Off:
//        Never: http://10.5.5.9/gp/gpControl/setting/59/0
//        1m: http://10.5.5.9/gp/gpControl/setting/59/1
//        2m: http://10.5.5.9/gp/gpControl/setting/59/2
//        3m: http://10.5.5.9/gp/gpControl/setting/59/3
//        5m: http://10.5.5.9/gp/gpControl/setting/59/4
//        Auto Lock Screen:
//        7 sec timeout: http://10.5.5.9/gp/gpControl/setting/103/5
//        off: http://10.5.5.9/gp/gpControl/setting/103/3
//        Turn on by saying "GoPro Turn On":
//        ON: http://10.5.5.9/gp/gpControl/setting/104/1
//        OFF: http://10.5.5.9/gp/gpControl/setting/104/0
//        Set date and time
//        http://10.5.5.9/gp/gpControl/command/setup/date_time?p=%11%0b%10%11%29%2c
//        The hex string at the end is the same as for HERO3, so in the example: 11 = (20)17, 0b = 11 (November), 10 = 16, 11 = 17, 29 = 41, 2c = 44. Example bash code for date string, see https://github.com/ztzhang/GoProWifiCommand/issues/3.
//
//        Streaming tweaks:
//        Stream BitRate :
//        Supports any number ( like 7000000), but limited by wifi throughput, packet loss and video glitches may appear. Correct parameter ID is 62!
//
//        250 Kbps: http://10.5.5.9/gp/gpControl/setting/62/250000
//        400 Kbps: http://10.5.5.9/gp/gpControl/setting/62/400000
//        600 Kbps: http://10.5.5.9/gp/gpControl/setting/62/600000
//        700 Kbps: http://10.5.5.9/gp/gpControl/setting/62/700000
//        800 Kbps: http://10.5.5.9/gp/gpControl/setting/62/800000
//        1 Mbps: http://10.5.5.9/gp/gpControl/setting/62/1000000
//        1.2 Mbps: http://10.5.5.9/gp/gpControl/setting/62/1200000
//        1.6 Mbps: http://10.5.5.9/gp/gpControl/setting/62/1600000
//        2 Mbps: http://10.5.5.9/gp/gpControl/setting/62/2000000
//        2.4 Mbps: http://10.5.5.9/gp/gpControl/setting/62/2400000
//        Stream Window Size:
//        Sizes with 720 height are tested on Hero 5 Black.
//
//        Default: http://10.5.5.9/gp/gpControl/setting/64/0
//        240: http://10.5.5.9/gp/gpControl/setting/64/1
//        240, 3:4: http://10.5.5.9/gp/gpControl/setting/64/2
//        240 1:2: http://10.5.5.9/gp/gpControl/setting/64/3
//        480: http://10.5.5.9/gp/gpControl/setting/64/4
//        480 3:4: http://10.5.5.9/gp/gpControl/setting/64/5
//        480 1:2: http://10.5.5.9/gp/gpControl/setting/64/6
//        720 (1280x720) : http://10.5.5.9/gp/gpControl/setting/64/7
//        720 3:4 (960x720) http://10.5.5.9/gp/gpControl/setting/64/8
//        720 1:2 (640x720) http://10.5.5.9/gp/gpControl/setting/64/9
//        WiFi AP Settings:
//        Turn WiFi OFF: http://10.5.5.9/gp/gpControl/setting/63/0
//        Switch WiFi to App/Smartphone mode: http://10.5.5.9/gp/gpControl/setting/63/1
//        Switch WiFi to GoPro RC: http://10.5.5.9/gp/gpControl/setting/63/2
//        Switch WiFi to GoPro Smart Remote RC: http://10.5.5.9/gp/gpControl/setting/63/4

    class STORAGE_DELETE_FILE(path: String) : Endpoint("gpControl/command/storage/delete?p=$path")
    object STORAGE_DELETE_LAST : Endpoint("gpControl/command/storage/delete/last")
    object MEDIA_LIST : Endpoint("gpMediaList")

//        Reformat SD Card (CAUTION!):
//        http://10.5.5.9/gp/gpControl/command/storage/delete/all

//        Tag moment in video file:
//        http://10.5.5.9/gp/gpControl/command/storage/tag_moment/playback?p=XXXGOPRO/XXXXXX.MP4&tag=Miliseconds
//
//        XXXGOPRO is the folder, XXXXXX.MP4 is the video and Miliseconds are the miliseconds offset from the start of the video.
//
//        For example:
//
//        http://10.5.5.9/gp/gpControl/command/storage/tag_moment/playback?p=103GOPRO/GOPR1359.MP4&tag=2000
//
//        will make a HiLight Tag on 2 seconds of the video GOPR1359.MP4
//
//        Extracting a clip from a video (GoPro Clips):

//        Extracting a clip from a video (GoPro Clips):
//        To start a video conversion:
//
//        http://10.5.5.9/gp/gpControl/command/transcode/request?source=DCIM/[XXXGOPRO]/GOPRXXXX.MP4&res=VIDEO_RESOLUTION&fps_divisor=FPS&in_ms=In_MS&out_ms=Out_MS
//
//        Parameters:
//
//        VIDEO_RESOLUTION:
//        1080 = 0
//        960 = 1
//        720 = 2
//        WVGA = 3
//        640p = 4
//        432x240 (live preview resolution) = 5
//        320x240 = 6
//        FPS: (Divide FPS by)
//        1/1 = 0 (Leave it as is)
//        1/2 = 1
//        1/3 = 2
//        1/4 = 3
//        1/8 = 4
//        Output:
//
//        {"status":{"id":STATUS_ID,"source":"DCIM\XXXGOPRO\GOPRXXXX.MP4","status":0,"failure_reason":0,"estimate":1,"completion":0,"output":""}}
//        If you did it right.
//
//        status values:
//
//        0 = Started
//        1 = In Progress
//        2 = Finished
//        3 = Cancelled
//        4 = Conversion failed
//        failure_reason values:
//
//        0 = No fail
//        1 = Bad file
//        2 = Bad parameters
//        3 = No space left on the device
//        4 = Camera is busy converting something else
//        To get the status of a conversion:
//
//        http://10.5.5.9/gp/gpControl/command/transcode/status?id=STATUS_ID (from the previous command)
//
//        Output:
//
//        {"status":{"id":STATUS_ID,"source":"DCIM\XXXGOPRO\GOPRXXXX.MP4","status":2,"failure_reason":0,"estimate":1,"completion":0,"output":"DCIM/XXXGOPRO/GOPRXXXX.MP4"}}
//        You can now download the output url, add http://10.5.5.9/videos/ to it.
//
//        To cancel a conversion:
//
//        http://10.5.5.9/gp/gpControl/command/transcode/cancel?id=STATUS_ID


    //        Connecting to a WiFi network:
//        Scan for available networks: http://10.5.5.9/gp/gpControl/command/wireless/ssid/scan?p=1
//        Return JSON of available networks: http://10.5.5.9/gp/gpControl/command/wireless/ssid/list
//        the JSON structure is:
//
//        {
//            "scan_id": 1,
//            "total": 5,
//            "index": 0,
//            "index_count": 5,
//            "ssid_array": [
//            {
//                "ssid": "MyHomeNetwork",
//                "auth_type": 1,
//                "bars": 0
//            },
//            ...
//            ]
//        }
    object GET_AVAILABLE_WIFI : Endpoint("gpControl/command/wireless/ssid/list")
    class SAVE_WIFI(ssid: String, authType: Int, password: String) : Endpoint("gpControl/command/wireless/ssid/save?ssid=$ssid&auth_type=$authType&pw=$password")
//        Save the network: http://10.5.5.9/gp/gpControl/command/wireless/ssid/save?ssid=WIFI_SSID&auth_type=AUTH_TYPE&pw=SSID_PASSWORD
//        WIFI_SSID = ssid
//        AUTH_TYPE = auth_type from the /list JSON, an integer (1 in the example)
//        SSID_PASSWORD = SSID password

    class CONNECT_TO_WIFI(ssid: String, authType: Int, password: String) : Endpoint("gpControl/command/wireless/ssid/select?ssid=$ssid&auth_type=$authType&pw=$password")
//        Connect to network: http://10.5.5.9/gp/gpControl/command/wireless/ssid/select?ssid=WIFI_SSID&auth_type=AUTH_TYPE&pw=SSID_PASSWORD
//        Audio Input: (does not work as of 02.02.00.00)
//        None: http://10.5.5.9/gp/gpControl/setting/95/0
//        Standard Mic: http://10.5.5.9/gp/gpControl/setting/95/1
//        Standard Mic+: http://10.5.5.9/gp/gpControl/setting/95/2
//        Powered Mic: http://10.5.5.9/gp/gpControl/setting/95/3
//        Powered Mic+: http://10.5.5.9/gp/gpControl/setting/95/4
//        Line In: http://10.5.5.9/gp/gpControl/setting/95/5
}
