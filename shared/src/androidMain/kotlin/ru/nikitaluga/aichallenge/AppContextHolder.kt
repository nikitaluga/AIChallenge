package ru.nikitaluga.aichallenge

import android.content.Context

object AppContextHolder {
    lateinit var context: Context
        private set

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
}
