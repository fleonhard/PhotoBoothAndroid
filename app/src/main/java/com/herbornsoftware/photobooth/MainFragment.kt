package com.herbornsoftware.photobooth

import android.Manifest
import android.content.*
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.herbornsoftware.photobooth.ViewState.*
import com.herbornsoftware.photobooth.core.GoPro
import kotlinx.android.synthetic.main.confirm_dialog.view.*
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.android.synthetic.main.seekbar_thumb.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.io.BufferedInputStream as BufferedInputStream1

data class WifiCredentials(val ssid: String, val password: String)

object Database {

    private const val GOPRO_SSID = "GOPRO_SSID"
    private const val GOPRO_PASSWORD = "GOPRO_PASSWORD"
    private const val WLAN_SSID = "WLAN_SSID"
    private const val WLAN_PASSWORD = "WLAN_PASSWORD"
    private const val PHOTO_COUNTDOWN = "PHOTO_COUNTDOWN"
    private const val PHOTO_ITERATIONS = "PHOTO_ITERATIONS"
    private const val PHOTO_CURRENT_COUNTDOWN = "PHOTO_CURRENT_COUNTDOWN"

    fun getGoProCredentials(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).let {
            WifiCredentials(
                it.getString(GOPRO_SSID, null) ?: "GoPro H 7",
                it.getString(GOPRO_PASSWORD, null) ?: "VDj-y=z-kMh"
            )
        }

    fun getWLANCredentials(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).let {
            WifiCredentials(
                it.getString(WLAN_SSID, null) ?: "Pretty fly for a Wifi",
                it.getString(WLAN_PASSWORD, null) ?: "Eva07092013"
            )
        }

    fun getPhotoIterations(context: Context) =
        (PreferenceManager.getDefaultSharedPreferences(context).getString(PHOTO_ITERATIONS, null) ?: "1").toInt()

    fun getPhotoMinCountdown(context: Context) =
        (PreferenceManager.getDefaultSharedPreferences(context).getString(PHOTO_COUNTDOWN, null) ?: "3").toInt()

    fun saveCurrentCountdown(context: Context, value: Int) =
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PHOTO_CURRENT_COUNTDOWN, value).apply()

    fun getCurrentCountdown(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getInt(PHOTO_CURRENT_COUNTDOWN,3)
}

typealias WifiListener = (state: WifiListenerState) -> Unit

sealed class WifiListenerState
data class WifiConnected(val ssid: String) : WifiListenerState()

object WifiConnectionManager {

    private val TAG = WifiConnectionManager::class.java.simpleName

    fun connect(context: Context, credentials: WifiCredentials, wifiListener: WifiListener) {
        Log.d(TAG, "Requested: ${credentials.ssid}")
        Log.d(TAG, "Current: ${getCurrentWifiSSID(context)}")
        if (credentials.ssid != getCurrentWifiSSID(context)) {
            val wifiConfig = createWifiConfig(credentials)
            val wifiManager = getWifiManager(context)
            wifiManager.disconnect()
            listen(context) {
                Log.d(TAG, "Requested: ${credentials.ssid}")
                Log.d(TAG, "Current: ${getCurrentWifiSSID(context)}")
                if (credentials.ssid == getCurrentWifiSSID(context)) {
                    wifiListener(WifiConnected(credentials.ssid))
                    true
                } else {
                    Handler().postDelayed({
                        connect(context, credentials, wifiListener)
                    }, 2000L)
                    true
                }
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                wifiManager.configuredNetworks.any()
                val netId = wifiManager.addNetwork(wifiManager.configuredNetworks.find { it.SSID == wifiConfig.SSID } ?: wifiConfig)
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
            }
        } else {
            wifiListener(WifiConnected(credentials.ssid))
        }
    }

    private fun createWifiConfig(credentials: WifiCredentials): WifiConfiguration {
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = String.format("\"%s\"", credentials.ssid)
        wifiConfig.preSharedKey = String.format("\"%s\"", credentials.password)
        return wifiConfig
    }

    private fun getCurrentWifiSSID(context: Context): String? {
        val wifiManager = getWifiManager(context)
        return if (wifiManager.isWifiEnabled) {
            wifiManager.connectionInfo?.ssid?.let { ssid ->
                return if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else ssid
            }
        } else ""
    }

    private fun getWifiManager(context: Context) =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private fun listen(context: Context, callback: () -> Boolean) {
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkInfo = cm.activeNetworkInfo
                    if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected) {
                        if (callback()) {
                            context.unregisterReceiver(this)
                        }
                    }
                }
            }
        }, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

}

sealed class CountDownState {
    class Tick(val time: Long) : CountDownState()
    object Finish : CountDownState()
}

class CountDown(
    millis: Long,
    interval: Long,
    private val listener: (state: CountDownState) -> Unit
) : CountDownTimer(millis, interval) {
    override fun onTick(millisUntilFinished: Long) {
        listener(CountDownState.Tick(millisUntilFinished))
    }

    override fun onFinish() {
        listener(CountDownState.Finish)
    }
}

enum class ViewState(val start: Boolean, val info: Boolean, val wifi: Boolean, val download: Boolean, val upload: Boolean, val countdown: Boolean, val progress: Boolean, val send: Boolean) {
    WIFI_CONNECT(false, false, true, false, false, false, true, false),
    IMAGE_UPLOAD(false, false, false, false, false, false, true, false),
    TAKE_PICTURES(false, false, false, false, false, true, true, false),
    READY(true, true, false, false, false, false, false, false),
    FINISHED(false, true, false, false, false, false, false, true),
    DOWNLOAD(false, false, false, true, false, false, true, false),
    UPLOAD(false, false, false, false, true, false, true, false)
}

class MainFragment : Fragment() {

    private val imagePreviewAdapter = ImagePreviewAdapter()
    private var toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var thumbView: View

    companion object {
        private val TAG = MainFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.main_fragment, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        thumbView = layoutInflater.inflate(R.layout.seekbar_thumb, null, false)

        countdownSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var minCountdown = 3
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                seekBar.thumb = getThumb(progress + minCountdown)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                minCountdown = Database.getPhotoMinCountdown(requireContext())
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Database.saveCurrentCountdown(requireContext(), seekBar.progress + minCountdown)
            }
        })

        infoBtn.setOnClickListener {
            showInfoDialog(getString(R.string.main_info, maxPhotos()), getString(R.string.main_title),false)
        }
        startBtn.setOnClickListener {
            startAction()
        }
        settingsBtn.setOnClickListener { goToSettings(it) }
        sendBtn.setOnClickListener { finish() }
        imageList.adapter = imagePreviewAdapter
        imageList.layoutManager = LinearLayoutManager(context)
        imagePreviewAdapter.onRemoveClicked = { removeImage(it) }
    }

    override fun onResume() {
        super.onResume()

        val minCountdown = Database.getPhotoMinCountdown(requireContext())
        val currentCountdown = Database.getCurrentCountdown(requireContext()).coerceAtLeast(Database.getPhotoMinCountdown(requireContext()))

        countdownSeekbar.max = 9 - minCountdown
        countdownSeekbar.thumb = getThumb(currentCountdown)
        countdownSeekbar.thumbOffset = 0
        countdownSeekbar.progress = currentCountdown - minCountdown
    }

    fun getThumb(progress: Int): Drawable? {
        (thumbView.tvProgress as TextView).text = "$progress"
        thumbView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bitmap = Bitmap.createBitmap(thumbView.measuredWidth, thumbView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        thumbView.layout(0, 0, thumbView.measuredWidth, thumbView.measuredHeight)
        thumbView.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    private fun startAction() {
        setViewState(WIFI_CONNECT)
        WifiConnectionManager.connect(requireContext(), Database.getGoProCredentials(requireContext())) { state ->
            setViewState(TAKE_PICTURES)
            takePictures(countdownTime() * 1000L, photosRemaining())
        }
    }

    fun countdownTime() = Database.getCurrentCountdown(requireContext())

    fun maxPhotos() = Database.getPhotoIterations(requireContext())

    fun photosRemaining() = maxPhotos() - imagePreviewAdapter.itemCount

    fun isMaxImages() = photosRemaining() <= 0

    private fun showInfoDialog(text: String, title: String, confirm: Boolean = false, callback: ((confirm: Boolean) -> Unit) = { }) {

//        val editNameDialogFragment = ConfirmDialog.newInstance(infoId)
//        editNameDialogFragment.show(childFragmentManager, "fragment_edit_name")

        val view = layoutInflater.inflate(R.layout.confirm_dialog, null)
        view.dialog_text.text = text
        view.dialog_title.text = title

        val builder = AlertDialog.Builder(requireActivity(), R.style.Theme_PhotoBooth_Dialog)
//            .setTitle(getString(R.string.info))
//            .setMessage(infoId)
            .setView(view)
        if(confirm) {
            builder.setPositiveButton(R.string.confirm) { dialog, id -> callback(true) }
            builder.setNegativeButton(R.string.cancel) { dialog, id -> callback(false) }
        } else {
            builder.setNegativeButton(R.string.close) { dialog, id -> callback(true) }
        }
        builder.create().show()
    }

    private fun finish() {

        showInfoDialog(getString(R.string.finish_info), getString(R.string.finish_title), true) {
            if (it) {
                clearView()
            }
        }

//        setViewState(DOWNLOAD)
//        DownloadImageTask(requireContext()) { optionalFileNames ->
//            optionalFileNames?.let { fileNames ->
//                setViewState(WIFI_CONNECT)
//                WifiConnectionManager.connect(requireContext(), Database.getWLANCredentials(requireContext())) {
//                    setViewState(UPLOAD)

//                    val imagePath = File(requireContext().cacheDir, "images")
//                    val uris = fileNames.map { fileName ->
//                        FileProvider.getUriForFile(requireContext(), "com.herbornsoftware.photobooth.fileprovider", File(imagePath, fileName))
//                    }


//                    val fileName = fileNames[0]
//                    val wrapper = ContextWrapper(context)
//                    val imagePath = File(wrapper.getDir("Images", MODE_PRIVATE), "images")
//                    val contentUri: Uri = FileProvider.getUriForFile(requireContext(), "com.herbornsoftware.photobooth.fileprovider", newFile)

//                    val shareIntent = Intent(Intent.ACTION_SEND)
//                    shareIntent.type = "image/jpeg"
//                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // temp permission for receiving app to read this file
//                    shareIntent.setDataAndType(contentUri, requireContext().contentResolver.getType(contentUri))
//                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
//                    startActivity(Intent.createChooser(shareIntent, "Choose an app"))
//                    val shareIntent: Intent = Intent().apply {
////                        action = Intent.ACTION_SEND
//                        action = Intent.ACTION_SEND_MULTIPLE
////                        putExtra(Intent.EXTRA_STREAM, contentUri)
//                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Uri>(*uris.toTypedArray()))
//                        type = "image/jpeg"
//                    }
//                    startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))


//                }
//            }?:let{
//                 TODO Error
//            }
//        }.execute(*imagePreviewAdapter.images.map { URL(it.url) }.toTypedArray())
    }

    private fun addImage(image: GoPro.GoProFile, index: Int = imagePreviewAdapter.itemCount) {
        imageList.visibility = View.VISIBLE
        imagePreviewAdapter.addImage(index, image)
        imageList.smoothScrollToPosition(index)
        if (isMaxImages()) {
            setViewState(FINISHED)
        }
    }

    private fun removeImage(image: GoPro.GoProFile) {
        showInfoDialog(getString(R.string.delete_info), getString(R.string.remove_title),true) {
            if (it) {
                GoPro.deleteFile(requireActivity(), image, handleError("Delete File")) {
                    val index = imagePreviewAdapter.deleteImage(image)
//        Snackbar.make(wrapper, "Bild wurde gelöscht.", Snackbar.LENGTH_LONG).setAction("Rückgängig") { addImage(image, index) }.show()
                    if (!isMaxImages()) {
                        setViewState(READY)
                    }

                    if(imagePreviewAdapter.itemCount == 0) {
                        imageList.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun clearView() {
        imageList.visibility = View.INVISIBLE
        imagePreviewAdapter.clear()
        setViewState(READY)
    }

    private fun setViewState(state: ViewState) {
        startBtn.visibility = if (state.start) View.VISIBLE else View.INVISIBLE
        if (state.wifi) {
            progressIcon.visibility = View.VISIBLE
            progressIcon.setImageDrawable(requireContext().resources.getDrawable(R.drawable.ic_baseline_wifi_24))
        } else if (state.upload) {
            progressIcon.visibility = View.VISIBLE
            progressIcon.setImageDrawable(requireContext().resources.getDrawable(R.drawable.ic_baseline_cloud_upload_24))
        } else if (state.download) {
            progressIcon.visibility = View.VISIBLE
            progressIcon.setImageDrawable(requireContext().resources.getDrawable(R.drawable.ic_baseline_cloud_download_24))
        } else {
            progressIcon.visibility = View.INVISIBLE
        }
        infoBtn.visibility = if (state.info) View.VISIBLE else View.INVISIBLE
        timer.visibility = if (state.countdown) View.VISIBLE else View.INVISIBLE
        progressContainer.visibility = if (state.progress) View.VISIBLE else View.INVISIBLE
        sendBtn.visibility = if (state.send) View.VISIBLE else View.INVISIBLE
    }

    private fun takePictures(delay: Long, count: Int, callback: () -> Unit = {}) {
        if (count <= 0) {
            callback()
            return
        }
        var oldTime = 0L
        CountDown(delay, 10) { state ->
            when (state) {
                is CountDownState.Tick -> {
//                    setProgress((delay - state.time).toInt())
                    val time = state.time / 1000 + 1
                    timer.text = time.toString()
                    if (time != oldTime) {
                        toneGenerator.startTone(ToneGenerator.TONE_SUP_PIP,150)
                        oldTime = time
                    }
                }
                is CountDownState.Finish -> {
                    timer.text = getString(R.string.go)
                    GoPro.startRecord(requireActivity(), handleError(getString(R.string.take_picture_failed))) { Log.d(TAG, "Photo") }
                    wait(2) {
                        GoPro.getLastMedia(requireActivity(), handleError("Loading Media List")) { image ->
                            imageList.smoothScrollToPosition(0)
                            setViewState(TAKE_PICTURES)
                            addImage(image)
                            takePictures(delay, count - 1, callback)
                        }
                    }
                }
            }
        }.start()
    }

    private fun wait(sec: Int, callback: () -> Unit) {
        Handler().postDelayed(callback, sec * 1000L)
    }

    private fun handleError(message: String): (String?) -> Unit = { err ->
        Log.e(TAG, "Error: $err")
        sendSnack("$message -> $err")
    }

    private fun sendSnack(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun goToSettings(it: View) {
        it.findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
    }


}

class DownloadImageTask(private val context: Context, private val callback: (List<String>?) -> Unit) : AsyncTask<URL, Void, List<String>>() {
    override fun onPreExecute() {}
    override fun doInBackground(vararg urls: URL): List<String>? {
        return urls.mapNotNull { download(it) }
    }

    private fun download(url: URL): String? {
        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            val inputStream: InputStream = connection.inputStream
            val bufferedInputStream = BufferedInputStream1(inputStream)
            saveImageToInternalStorage(BitmapFactory.decodeStream(bufferedInputStream))
//            BitmapFactory.decodeStream(bufferedInputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    //    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri? {
//        val wrapper = ContextWrapper(context)
//
//        val file = File(wrapper.getDir("Images", MODE_PRIVATE), UUID.randomUUID().toString() + ".jpg")
//
//        try {
//            val stream: OutputStream = FileOutputStream(file)
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
//            stream.flush()
//            stream.close()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//        return Uri.parse(file.absolutePath)
//    }
    private fun saveImageToInternalStorage(bitmap: Bitmap): String? {
        return try {
//            val cachePath = File(context.cacheDir, "images")
            val wrapper = ContextWrapper(context)
            val fileName = UUID.randomUUID().toString() + ".jpg"
            val cachePath = File(wrapper.getDir("Images", MODE_PRIVATE), fileName)
            cachePath.mkdirs() // don't forget to make the directory
            val stream = FileOutputStream("$cachePath/$fileName.png")
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
            fileName
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onPostExecute(result: List<String>?) {
        callback(result)
    }
}