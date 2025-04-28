package com.dts.posprintusb

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.posprinter.printdemo.R
import net.posprinter.POSConnect
import net.posprinter.POSConst
import net.posprinter.POSPrinter
import java.io.File
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    lateinit var printer:  POSPrinter
    var lines = ArrayList<String>()
    lateinit var bmp : Bitmap

    var usbaddress = ""
    var linemode = 0
    var macro_param = ""
    var line = ""

    val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    val forceCLaim = true

    var mDeviceList: HashMap<String, UsbDevice>? = null
    var mDeviceIterator: Iterator<UsbDevice>? = null

    var mUsbManager: UsbManager? = null
    var mDevice: UsbDevice? = null
    var mConnection: UsbDeviceConnection? = null
    var mInterface: UsbInterface? = null
    var mEndPoint: UsbEndpoint? = null
    var mPermissionIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            grantPermissions()

        } catch (e:Exception) {
            msgbox(object : Any() {}.javaClass.enclosingMethod.name+". "+e.message)
        }
    }


    //region Events

    fun doExit(view: View) {
        closeSession()
    }

    //endregion

    //region Main

    fun startApplication() {
        try {

            if (!isAllFilesAccessGranted()) {
                grandAllFilesAccess()
                return
            }

            val mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            mDeviceList = mUsbManager?.getDeviceList()
            val mDeviceIterator = mDeviceList?.values

            mPermissionIntent =
                PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(mUsbReceiver, filter)

            var usbDevice = ""

            for (itm in mDeviceList?.values!!) {
                val usbDevice1 = itm
                val interfaceCount = usbDevice1.interfaceCount
                mDevice = usbDevice1
            }

            try {
                mUsbManager!!.requestPermission(mDevice, mPermissionIntent)
            } catch (e: java.lang.Exception) {
                msgclose("No se puede conectar a la impresora." );return
            }

            usbaddress=getUsb()
            toast("5")
            if (usbaddress.isEmpty()) {
                msgclose("¡No está conectada ninguna impresora USB!");return
            }

            /*
            if (connectUSB()) {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed( { processPrint() }, 500)
            }
            */

        } catch (e: Exception) {
            msgbox(object : Any() {}.javaClass.enclosingMethod.name+" . "+e.message)
        }
    }

    fun processPrint() {

        try {
            val file = File(Environment.getExternalStorageDirectory().toString() + "/print.txt")
            val lines: List<String> = file.readLines()

            for (itm in lines) {
                line=itm
                processLine()

                when (linemode) {
                     0 -> { printText(line!!) }
                    10 -> { printImage(macro_param) }
                }
            }

            cut()
            finishPrint()
        } catch (e: Exception) {
            msgclose(e.message!!)
        }
    }

    fun processLine() {
        var pp=0

        linemode=0
        if (line.isEmpty()) return

        try {
            pp=line?.indexOf("@@")!!
            if (pp!!<0) return

            pp=line?.indexOf("@@pic")!!
            if (pp==0) {
                macro_param=line.substring(6)
                linemode=10
            }

        } catch (e: Exception) {
            linemode=0
        }
    }

    fun finishPrint() {
        try {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed( { closeSession() }, 3000)
        } catch (e: Exception) {
            msgclose(e.message!!)
        }
    }

    //endregion

    //region Permission USB

    val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device =
                        intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true)) {
                        if (connectUSB()) {
                            val handler = Handler(Looper.getMainLooper())
                            handler.postDelayed( { processPrint() }, 500)
                        }
                    } else {
                        toast("PERMISO IMPRIMIR DENEGADO")
                    }
                }
            }
        }
    }

    //endregion

    //region Print

    fun printText(str: String) {
        printer.printString(str+"\n")
    }

    fun printTextDouble(str: String) {
        printer.printText(str+"\n", POSConst.ALIGNMENT_CENTER,
            POSConst.FNT_BOLD , POSConst.TXT_2WIDTH or POSConst.TXT_2HEIGHT)
    }

    fun printTextDoubleLeft(str: String) {
        printer.printText(str+"\n", POSConst.ALIGNMENT_LEFT,
            POSConst.FNT_BOLD , POSConst.TXT_2WIDTH or POSConst.TXT_2HEIGHT)
    }

    fun printImage(fname: String) {
        printImageBase(fname,384,POSConst.ALIGNMENT_CENTER)
    }

    fun printImage(fname: String,width: Int) {
        printImageBase(fname,width,POSConst.ALIGNMENT_CENTER)
    }

    fun printImageLeft(fname: String,width: Int) {
        printImageBase(fname,width,POSConst.ALIGNMENT_LEFT)
    }

    fun printImageRight(fname: String,width: Int) {
        printImageBase(fname,width,POSConst.ALIGNMENT_RIGHT)
    }

    fun printImageBase(fname: String,width: Int,alignment: Int) {
        try {
            val filename = Environment.getExternalStorageDirectory().toString() + "/"+ fname
            bmp= BitmapFactory.decodeFile(filename)
            printer.printBitmap(bmp, alignment, width)
        } catch (e: Exception) {
            toastlong(object : Any() {}.javaClass.enclosingMethod.name+" . "+e.message)
        }
    }

    private fun openCashBox() {
        try {
            printer.openCashBox(POSConst.PIN_TWO)
        } catch (e: Exception) {}
    }
    private fun openCashBoxEx() {
        try {
            printer.openCashBox(POSConst.PIN_FIVE)
        } catch (e: Exception) {}
    }

    private fun cut() {
        printer.feedLine()
        printer.feedLine()
        printer.cutHalfAndFeed(1)
        openCashBox()
    }

    //endregion

    //region Conexion USB

    fun connectUSB(): Boolean {
        try {
            App.get().connectUSB(usbaddress)
            printer = POSPrinter(App.get().curConnect)
            return true
        } catch (e: Exception) {
            msgclose("No se puede conectar a USB.\n "+e.message);return false
        }
    }

    fun disconnectUSB() {
        try {
            App.get().curConnect?.close()
        } catch (e: Exception) {
            msgclose("No se puede desconectar la USB.\n "+e.message)
        }
    }

    fun getUsb(): String {
        try {
            val usbNames = POSConnect.getUsbDevices(this)
            var ret = ""
            if (usbNames.isNotEmpty()) ret = usbNames[0]
            return ret
        } catch (e: Exception) {
            msgbox(object : Any() {}.javaClass.enclosingMethod.name+" . "+e.message)
            return ""
        }
    }

    //endregion

    //region Dialogs

    fun msgbox(msg: String) {
        try {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Impresion USB")
            dialog.setMessage(msg)
            dialog.setCancelable(false)
            dialog.setNeutralButton("OK") { dialog, which -> }
            dialog.show()
        } catch (ex: java.lang.Exception) {
            toast(ex?.message!!)
        }
    }

    fun msgclose(msg: String) {
        try {
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Impresion USB")
            dialog.setMessage(msg)
            dialog.setCancelable(false)
            dialog.setNeutralButton("OK") { dialog, which -> closeSession() }
            dialog.show()
        } catch (ex: java.lang.Exception) {
            toast(ex?.message!!)
        }
    }

    //endregion

    //region Aux

    fun isAllFilesAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun closeSession() {
        try {
            disconnectUSB()

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed( {
                exitProcess(0)
                //finish()
                                 }, 300)

        } catch (e: java.lang.Exception) {
            toast(object : Any() {}.javaClass.enclosingMethod.name + " . " + e.message)
        }
    }

    fun toast(msg: String) {
        val toast = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    fun toastlong(msg: String) {
        val toast = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    //endregion

    //region Permission

    private fun grantPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= 20) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    startApplication()
                } else {
                    ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),1)
                }
            }
        } catch (e: java.lang.Exception) {
            toastlong(object : Any() {}.javaClass.enclosingMethod.name + " . " + e.message)
        }
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        try {
            if (Build.VERSION.SDK_INT >= 20) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    startApplication()
                } else super.finish()
            }
        } catch (e: java.lang.Exception) {
            toastlong(object : Any() {}.javaClass.enclosingMethod.name + " . " + e.message)
        }
    }

    fun grandAllFilesAccess() {
        try {
            val uri = Uri.parse("package:com.dts.posprintusb")
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            startActivity(intent)
        } catch (ex: java.lang.Exception) {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            startActivity(intent)
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed( { finish() }, 500)

    }

    //endregion

    //region Activity Events


    //endregion


}