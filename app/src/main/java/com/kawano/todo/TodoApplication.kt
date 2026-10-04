package com.kawano.todo

import android.app.Application
import com.kawano.todo.di.AppContainer
import com.kawano.todo.di.DefaultAppContainer

class TodoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
