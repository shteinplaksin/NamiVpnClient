package io.github.hhwkart.nami.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}