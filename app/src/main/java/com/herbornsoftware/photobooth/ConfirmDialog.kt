package com.herbornsoftware.photobooth

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment

class ConfirmDialog: DialogFragment() {

    companion object {
        private const val TEXT_ID_KEY = "text_id"
        fun newInstance(textId: Int): ConfirmDialog {
            val frag = ConfirmDialog()
            val args = Bundle()
            args.putInt(TEXT_ID_KEY, textId)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.confirm_dialog, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.apply {
            setTitle("TEEST")
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setStyle(STYLE_NO_FRAME, R.style.Theme_PhotoBooth_Dialog)
        }
    }

}