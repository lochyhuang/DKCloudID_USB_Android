package com.dk.usbNfc.UsbHidManager;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.dk.usbNfc.Card.Card;
import com.dk.usbNfc.Exception.CardNoResponseException;
import com.dk.usbNfc.Tool.StringTool;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Administrator on 2017/11/8.
 */

public class UsbHidManager  {
    private static final String TAG = "UsbHidManager";
    private static final int FrameMaxLen = 64;
    private static final int VendorId = 1155;
    private static final int ProductId = 22315;
    public static final String ACTION_USB_PERMISSION  = "com.android.example.USB_PERMISSION";

    public Context mContext;

    public UsbManager manager; // USB管理器
    public UsbDevice mUsbDevice; // 找到的USB设备
    public UsbInterface mInterface;
    private PendingIntent mPermissionIntent;
    public UsbDeviceConnection mDeviceConnection;
    private UsbEndpoint epOut;
    private UsbEndpoint epIn;
    private boolean isUsbOpen = false;

    public boolean stopRvcDataFlag = false;
    private volatile Semaphore mSemaphore;

    private byte[] dataTemp;

    public onReceiveDataListener mOnReceiveDataListener;

    public UsbHidManager(Context context) {
        super();

        if (isUsbOpen) {
            return;
        }

        mContext = context;

        stopRvcDataFlag = false;

        isUsbOpen = init();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopRvcDataFlag) {
                    synchronized (this) {
                        getData();
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (mDeviceConnection != null) {
                    isUsbOpen = false;
                    mDeviceConnection.close();
                }
            }
        }).start();
    }

    public interface onReceiveDataListener {
        public void OnReceiverData(byte[] data);
    }

    public void setOnReceiveDataListener(onReceiveDataListener l) {
        this.mOnReceiveDataListener = l;
    }

    public boolean init() {
        // 获取USB设备
        manager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            Log.i(TAG, "无USB设备！");
            return false;
        } else {
            Log.i(TAG, "usb设备：" + String.valueOf(manager.toString()));
        }
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Log.i(TAG, "usb设备：" + String.valueOf(deviceList.size()));
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();

            //msgTest.setText(msgTest.getText() + "\r\nVendorId:" + device.getVendorId() + " ProductId:" + device.getProductId());
            // 在这里添加处理设备的代码
            if ( (device.getVendorId() == VendorId) && (device.getProductId() == ProductId) ) {
                mUsbDevice = device;
                Log.i(TAG, "找到设备");
            }
        }

        return findIntfAndEpt();
    }

    //用户权限处理
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.i(TAG,"获得权限！");
                        }
                    } else {
                        Log.i(TAG,"用户不允许USB访问设备！");
                    }

                    if (mSemaphore != null) {
                        mSemaphore.release();
                    }
                }
            }
        }
    };

    // 寻找接口和分配结点
    public boolean findIntfAndEpt() {
        if (mUsbDevice == null) {
            Log.i(TAG, "没有找到设备");
            return false;
        }

        UsbInterface intf = mUsbDevice.getInterface(0);
        Log.d(TAG, " " + intf);
        mInterface = intf;

        if (mInterface != null) {
            final UsbDeviceConnection[] connection = {null};
            // 判断是否有权限
            if (manager.hasPermission(mUsbDevice)) {
                // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
                connection[0] = manager.openDevice(mUsbDevice);
                if (connection[0] == null) {
                    return false;
                }

                if (connection[0].claimInterface(mInterface, true)) {
                    Log.i(TAG, "找到接口，有权限！");
                    mDeviceConnection = connection[0];
                    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
                    getEndpoint(mDeviceConnection, mInterface);
                } else {
                    connection[0].close();
                    return false;
                }
            } else {
                final Semaphore semaphore = new Semaphore(0);
                final boolean isOpen[] = {false};

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "没有权限");
                        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                        mContext.registerReceiver(usbReceiver, filter);
                        manager.requestPermission(mUsbDevice, mPermissionIntent);

                        mSemaphore = new Semaphore(0);
                        try {
                            mSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        mContext.unregisterReceiver(usbReceiver);

                        if (manager.hasPermission(mUsbDevice)) {
                            // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
                            connection[0] = manager.openDevice(mUsbDevice);
                            if (connection[0] == null) {
                                isOpen[0] = false;
                                semaphore.release();
                                return;
                            }

                            if (connection[0].claimInterface(mInterface, true)) {
                                Log.i(TAG, "找到接口");
                                mDeviceConnection = connection[0];
                                // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
                                getEndpoint(mDeviceConnection, mInterface);
                                isOpen[0] = true;
                            } else {
                                connection[0].close();
                                isOpen[0] = false;
                            }
                            semaphore.release();
                        }
                    }
                }).start();

                try {
                    semaphore.tryAcquire(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return isOpen[0];
            }
        } else {
            Log.i(TAG, "没有找到接口");
            return false;
        }

        return true;
    }

    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
    public void getEndpoint(UsbDeviceConnection connection, UsbInterface intf) {
        if (intf.getEndpoint(1) != null) {
            epOut = intf.getEndpoint(1);
        }
        if (intf.getEndpoint(0) != null) {
            epIn = intf.getEndpoint(0);
        }
    }

    //发送数据
    public boolean sendDataLen(byte[] data) {
        int ret = -100;

        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mDeviceConnection == null) {
            return false;
        }

        if (data.length <= (FrameMaxLen - 1)) {
            //byte[] sendTempBytes = new byte[data.length + 1];
            byte[] sendTempBytes = new byte[FrameMaxLen];
            sendTempBytes[0] = (byte) data.length;
            System.arraycopy(data, 0, sendTempBytes, 1, data.length);

            ret = transfer(epOut, sendTempBytes,
                    sendTempBytes.length, Card.CAR_NO_RESPONSE_TIME_MS);

            Log.i(TAG, "发送数据：" + StringTool.byteHexToSting(sendTempBytes));

            if (ret < 0) {
                return false;
            }
        } else {
            dataTemp = data;
            byte[] bytesTemp = new byte[FrameMaxLen];
            bytesTemp[0] = FrameMaxLen - 1;
            System.arraycopy(dataTemp, 0, bytesTemp, 1, FrameMaxLen - 1);

            byte[] newDataTemp = new byte[dataTemp.length - (FrameMaxLen - 1)];
            System.arraycopy(dataTemp, FrameMaxLen - 1, newDataTemp, 0, newDataTemp.length);
            dataTemp = newDataTemp;

            transfer(epOut, bytesTemp, bytesTemp.length, Card.CAR_NO_RESPONSE_TIME_MS);

            Log.i(TAG, "发送数据：" + StringTool.byteHexToSting(bytesTemp));

//            int i;
//            for (i=0; i<= (data.length / (FrameMaxLen - 1) - 1); i++) {
//                byte[] bytesTemp = new byte[FrameMaxLen];
//                bytesTemp[0] = FrameMaxLen - 1;
//                System.arraycopy(data, i * (FrameMaxLen - 1), bytesTemp, 1, FrameMaxLen - 1);
//                ret = mDeviceConnection.bulkTransfer(epOut, bytesTemp, bytesTemp.length, Card.CAR_NO_RESPONSE_TIME_MS);
//
//                Log.i(TAG, "发送数据：" + StringTool.byteHexToSting(bytesTemp));
//
//                if (ret < 0) {
//                    return false;
//                }
//            }
//            int len = data.length % (FrameMaxLen - 1);
//            if (len > 0) {
//                byte[] bytes = new byte[len + 1];
//                bytes[0] = (byte)len;
//                System.arraycopy(data, data.length - len, bytes, 1, len);
//                ret = mDeviceConnection.bulkTransfer(epOut, bytes, bytes.length, Card.CAR_NO_RESPONSE_TIME_MS);
//
//                Log.i(TAG, "发送数据：" + StringTool.byteHexToSting(bytes));
//
//                if (ret < 0) {
//                    return false;
//                }
//            }
        }

        return true;
    }

    //发送剩余的数据
    private void sendMordData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (dataTemp != null && dataTemp.length > 0) {
                    if (dataTemp.length <= (FrameMaxLen - 1)) {
                        //byte[] sendTempBytes = new byte[dataTemp.length + 1];
                        byte[] sendTempBytes = new byte[FrameMaxLen];
                        sendTempBytes[0] = (byte) dataTemp.length;
                        System.arraycopy(dataTemp, 0, sendTempBytes, 1, dataTemp.length);

                        transfer(epOut, sendTempBytes,
                                sendTempBytes.length, Card.CAR_NO_RESPONSE_TIME_MS);

                        Log.i(TAG, "发送数据：" + StringTool.byteHexToSting(sendTempBytes));
                    } else {
                        byte[] bytesTemp = new byte[FrameMaxLen];
                        bytesTemp[0] = FrameMaxLen - 1;
                        System.arraycopy(dataTemp, 0, bytesTemp, 1, FrameMaxLen - 1);

                        byte[] newDataTemp = new byte[dataTemp.length - (FrameMaxLen - 1)];
                        System.arraycopy(dataTemp, FrameMaxLen - 1, newDataTemp, 0, newDataTemp.length);
                        dataTemp = newDataTemp;

                        transfer(epOut, bytesTemp, bytesTemp.length, Card.CAR_NO_RESPONSE_TIME_MS);

                        Log.i(TAG, "发送数据：" + StringTool.byteHexToSting(bytesTemp));
                    }
                }
            }
        }).start();
    }

    private int transfer(UsbEndpoint endpoint,byte[] buffer, int length, int timeout) {
        //synchronized(this) {
            if (mDeviceConnection != null) {
                int ret = mDeviceConnection.bulkTransfer(endpoint, buffer, length, timeout);
                return ret;
            }
            else {
                return 0;
            }
        //}
    }

    //接收数据
    public byte[] getData() {
        synchronized(this) {
            if (mDeviceConnection == null) {
                return null;
            }

            int ret = -100;
            byte[] receiveBytes = new byte[FrameMaxLen];

            ret = mDeviceConnection.bulkTransfer(epIn, receiveBytes,
                    receiveBytes.length, Card.CAR_NO_RESPONSE_TIME_MS);

            if (ret != FrameMaxLen) {
                return null;
            }

            if ((receiveBytes[0] & 0xff) > (FrameMaxLen - 1)) {
                Log.i(TAG, "接收到ACK:FF");
                if (receiveBytes[0] == (byte) 0xff) {
                    sendMordData();
                }
                return null;
            }

            byte[] frameBytes = new byte[receiveBytes[0]];
            System.arraycopy(receiveBytes, 1, frameBytes, 0, frameBytes.length);

            Log.i(TAG, "接收到数据：" + StringTool.byteHexToSting(frameBytes));

            //接收数据成功，发送回调
            if (mOnReceiveDataListener != null) {
                mOnReceiveDataListener.OnReceiverData(frameBytes);
            }

            return frameBytes;
        }
    }

    //接收后续帧时需发送ACK
    public void sendAck() {
        synchronized(this) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    byte[] sendTempBytes = new byte[FrameMaxLen];
                    sendTempBytes[0] = (byte) 0xFF;

                    mDeviceConnection.bulkTransfer(epOut, sendTempBytes,
                            sendTempBytes.length, Card.CAR_NO_RESPONSE_TIME_MS);

                    Log.i(TAG, "发送ACK：" + StringTool.byteHexToSting(sendTempBytes));
                }
            }).start();
        }
    }

//    //接收后续帧时需发送ACK
//    public void sendAck() {
//        synchronized(this) {
//            final int[] nack_cnt = {0};
//            final boolean[] getDataFlag = new boolean[1];
//
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    do {
//                        semaphore = new Semaphore(0);
//                        byte[] sendTempBytes = new byte[1];
//                        sendTempBytes[0] = (byte) 0xFF;
//                        mDeviceConnection.bulkTransfer(epOut, sendTempBytes,
//                                sendTempBytes.length, Card.CAR_NO_RESPONSE_TIME_MS);
//
//                        Log.i(TAG, "发送ACK：" + StringTool.byteHexToSting(sendTempBytes));
//                        try {
//                            semaphore.tryAcquire(20, TimeUnit.MILLISECONDS);
//                            getDataFlag[0] = true;
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                            getDataFlag[0] = false;
//                        }
//                    } while ((nack_cnt[0]++ < 5) && !getDataFlag[0]);
//                    semaphore = null;
//                }
//            }).start();
//        }
//    }

    public void close() {
        stopRvcDataFlag = true;
        if (mDeviceConnection != null) {
            isUsbOpen = false;
            mDeviceConnection.close();
        }
    }
}
