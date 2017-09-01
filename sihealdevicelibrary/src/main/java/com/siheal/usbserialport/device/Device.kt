package com.siheal.usbserialport.device

import android.content.Context
import android.os.SystemClock
import com.hd.serialport.config.MeasureStatus
import com.hd.serialport.method.DeviceMeasureController
import com.hd.serialport.usb_driver.UsbSerialPort
import com.hd.serialport.utils.L
import com.siheal.usbserialport.R
import com.siheal.usbserialport.config.AIOComponent
import com.siheal.usbserialport.listener.ReceiveResultListener
import com.siheal.usbserialport.method.AIODeviceMeasure
import com.siheal.usbserialport.parser.DataPackageEntity
import com.siheal.usbserialport.parser.Parser
import com.siheal.usbserialport.result.ParserResult
import java.io.OutputStream
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by hd on 2017/8/28 .
 *
 */
@Suppress("LeakingThis")
abstract class Device(val context: Context, val aioDeviceType: Int, val parser: Parser, val listener: ReceiveResultListener) {

    val dataQueue = LinkedBlockingQueue<DataPackageEntity>()

    val outputStreamList = LinkedList<OutputStream>()

    val usbSerialPortList = LinkedList<UsbSerialPort>()

    var status = MeasureStatus.PREPARE

    protected var t: List<Any>? = null

    protected var aioComponent: AIOComponent? = null

    protected abstract fun measure()

    /**
     * provide conditions might need
     */
    fun addCondition(t: List<Any>?) {
        this.t = t
        L.d("from the external conditions:" + t?.size + "=" + t?.toString())
    }

    /**
     * provide aio component
     */
    fun addAIOComponent(aioComponent: AIOComponent?) {
        this.aioComponent=aioComponent
    }

    /**
     * write initialization device instruction
     */
    fun initializationInstruct() = aioComponent?.getInitializationInstructInstruct(aioDeviceType)

    /**
     * write release(shut down device) instruction
     */
    fun releaseInstruct() = aioComponent?.getReleaseInstruct(aioDeviceType)

    fun startMeasure() {
        if (status == MeasureStatus.PREPARE) {
            status = MeasureStatus.RUNNING
            parser.parser(this)
            measure()
            DeviceMeasureController.write(initializationInstruct())
        }
    }

    fun stopMeasure() {
        L.d("device stop:" + status)
        if (status == MeasureStatus.RUNNING) {
            status = MeasureStatus.STOPPING
            DeviceMeasureController.write(releaseInstruct())
            SystemClock.sleep(10)
            DeviceMeasureController.stop()
            dataQueue.clear()
            outputStreamList.clear()
            usbSerialPortList.clear()
        }
    }

    fun write(byteArray: ByteArray?, delay: Long = 0) {
        try {
            outputStreamList.filter { status == MeasureStatus.RUNNING }.forEach { it.write(byteArray) }
            usbSerialPortList.filter { status == MeasureStatus.RUNNING }.forEach { it.write(byteArray, 1000) }
            L.d("device write :" + Arrays.toString(byteArray))
            SystemClock.sleep(delay)
        } catch (ignored: Exception) {
            L.d("device write error :" + Arrays.toString(byteArray))
        }
    }

    fun write(data: List<ByteArray>?, delay: Long = 0) {
        if (data != null && data.isNotEmpty()) data.filter { status == MeasureStatus.RUNNING }.forEach { write(it, delay) }
    }

    fun error(msg: String?) {
        listener.error(if (msg == null || msg.isEmpty()) context.resources.getString(R.string.measure_error) else msg)
        AIODeviceMeasure.stopMeasure()
    }

    fun complete(parserResult: ParserResult, stop: Boolean) {
        listener.receive(parserResult)
        if (stop)AIODeviceMeasure.stopMeasure()
    }

}