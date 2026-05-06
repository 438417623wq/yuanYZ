package com.goodzh.converter

import android.app.Application
import com.goodzh.converter.data.ConversionRepository

class GoodZhApp : Application() {
    val repository by lazy {
        ConversionRepository(this)
    }
}
