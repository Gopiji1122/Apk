package com.gopiji1122.aurapdfeditor

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AuraPdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
