package com.dts.posprintusb

import android.app.Application
import android.widget.Toast
import com.jeremyliao.liveeventbus.LiveEventBus
import com.posprinter.printdemo.utils.Constant
import net.posprinter.IDeviceConnection
import net.posprinter.IPOSListener
import net.posprinter.POSConnect


class App : Application() {

    var curConnect: IDeviceConnection? = null

    private val connectListener = IPOSListener { code, msg ->

        when (code) {
            POSConnect.CONNECT_SUCCESS -> {
                toast("USB conectado.")
                LiveEventBus.get<Boolean>(Constant.EVENT_CONNECT_STATUS).post(true)
            }
            POSConnect.CONNECT_FAIL -> {
                toast("Falla de conexión a USB.")
                LiveEventBus.get<Boolean>(Constant.EVENT_CONNECT_STATUS).post(false)
            }
            POSConnect.CONNECT_INTERRUPT -> {
                toast("Conexión a USB interrupida.")
                LiveEventBus.get<Boolean>(Constant.EVENT_CONNECT_STATUS).post(false)
            }
            POSConnect.SEND_FAIL -> {
                toast("Falla de envío a USB.")
            }
            POSConnect.USB_DETACHED -> {
                toast("USB desconnectado.")
            }
            POSConnect.USB_ATTACHED -> {
                toast("USB insertado.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        POSConnect.init(this)
    }

    fun connectUSB(pathName: String) {
        curConnect?.close()
        curConnect = POSConnect.createDevice(POSConnect.DEVICE_TYPE_USB)
        curConnect!!.connect(pathName, connectListener)
    }

    private fun toast(str: String) {
        Toast.makeText(App.get(), str, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private lateinit var app: App

        fun get(): App {
            return app
        }
    }

}