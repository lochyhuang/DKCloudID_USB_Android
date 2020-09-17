package com.huang.lochy.usbhiddemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.dk.usbNfc.Card.SamVIdCard;
import com.dk.usbNfc.UsbHidManager.UsbHidManager;
import com.dk.usbNfc.UsbNfcDeviceService;
import com.dk.usbNfc.DeviceManager.UsbNfcDevice;
import com.dk.usbNfc.DeviceManager.ComByteManager;
import com.dk.usbNfc.DeviceManager.DeviceManager;
import com.dk.usbNfc.DeviceManager.DeviceManagerCallback;
import com.dk.usbNfc.Exception.CardNoResponseException;
import com.dk.usbNfc.Exception.DeviceNoResponseException;
import com.dk.usbNfc.Tool.StringTool;
import com.dk.usbNfc.Card.CpuCard;
import com.dk.usbNfc.Card.FeliCa;
import com.dk.usbNfc.Card.Iso14443bCard;
import com.dk.usbNfc.Card.Iso15693Card;
import com.dk.usbNfc.Card.Mifare;
import com.dk.usbNfc.Card.Ntag21x;
import com.dk.usbNfc.UsbHidManager.*;

import org.xmlpull.v1.XmlPullParser;

import java.security.InvalidKeyException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;

import static com.dk.usbNfc.DeviceManager.DeviceManager.CARD_TYPE_125K;
import static com.dk.usbNfc.DeviceManager.DeviceManager.CARD_TYPE_ISO4443_B;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    UsbNfcDeviceService mUsbNfcDeviceService;
    private UsbNfcDevice usbNfcDevice;
    private EditText msgText = null;
    private ProgressDialog readWriteDialog = null;
    private AlertDialog.Builder alertDialog = null;

    private StringBuffer msgBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        msgBuffer = new StringBuffer();

        msgText = (EditText)findViewById(R.id.msgText);
        Button clearButton = (Button) findViewById(R.id.clearButton);
        Button openAutoSearchCard = (Button)findViewById(R.id.openAutoSearchCard);
        Button closeAutoSearchCard = (Button)findViewById(R.id.closeAutoSearchCard);

        readWriteDialog = new ProgressDialog(MainActivity.this);
        readWriteDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // 设置ProgressDialog 标题
        readWriteDialog.setTitle("请稍等");
        // 设置ProgressDialog 提示信息
        readWriteDialog.setMessage("正在读写数据……");
        readWriteDialog.setMax(100);

        clearButton.setOnClickListener(new claerButtonListener());
        openAutoSearchCard.setOnClickListener(new OpenAutoSearchCardButtonListener());
        closeAutoSearchCard.setOnClickListener(new CloseAutoSearchCardButtonListener());

        //usb_nfc设备初始化
        usbNfcDevice = new UsbNfcDevice(MainActivity.this);
        usbNfcDevice.setCallBack(deviceManagerCallback);

        //USB权限广播
        IntentFilter filter = new IntentFilter(UsbHidManager.ACTION_USB_PERMISSION);
        //USB状态广播
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (usbNfcDevice.usbHidManager.mDeviceConnection != null) {
                        msgBuffer.append("\r\n").append("USB设备已连接！");
                        handler.sendEmptyMessage(0);

                        msgBuffer.append("\r\n").append("正在打开自动寻卡...");
                        handler.sendEmptyMessage(0);

                        if (startAutoSearchCard()) {
                            msgBuffer.append("\r\n").append("自动寻卡已打开！");
                            handler.sendEmptyMessage(0);
                        }

                        byte versionsByts = usbNfcDevice.getDeviceVersions();
                        msgBuffer.append(String.format("\r\n设备版本：%02x", versionsByts));
                        handler.sendEmptyMessage(0);
                    }
                    else if (usbNfcDevice.usbHidManager.mUsbDevice != null) {
                        msgBuffer.append("\r\n").append("没有权限！\r\n");
                        handler.sendEmptyMessage(0);
                    }
                    else {
                        msgBuffer.append("\r\n").append("未找到USB设备！\r\n");
                        handler.sendEmptyMessage(0);
                    }
                } catch (DeviceNoResponseException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        msgBuffer.append("USB_HID_NFC Demo v2.1.0 20180507");
        handler.sendEmptyMessage(0);
    }

    @Override
    public void onBackPressed() {

    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            return false;
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            return super.onKeyDown(keyCode, event); // 这里拦截了，就不会走到onBackPressed方法了
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (readWriteDialog != null) {
            readWriteDialog.dismiss();
        }

        //关闭
        usbNfcDevice.usbHidManager.close();
        unregisterReceiver(usbReceiver);
    }

    //用户USB权限及USB状态广播
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbHidManager.ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.i(TAG,"获得权限！");
                            msgBuffer.append("获得权限！\r\n");
                            handler.sendEmptyMessage(0);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    while (usbNfcDevice.usbHidManager.mDeviceConnection != null);
                                        try {
                                            msgBuffer.append("\r\n").append("USB设备已连接！");
                                            handler.sendEmptyMessage(0);

                                            msgBuffer.append("\r\n").append("正在打开自动寻卡...");
                                            handler.sendEmptyMessage(0);

                                            if (startAutoSearchCard()) {
                                                msgBuffer.append("\r\n").append("自动寻卡已打开！");
                                                handler.sendEmptyMessage(0);
                                            }

                                            byte versionsByts = usbNfcDevice.getDeviceVersions();
                                            msgBuffer.append(String.format("\r\n设备版本：%02x", versionsByts));
                                            handler.sendEmptyMessage(0);
                                    } catch (DeviceNoResponseException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();

                        }
                    } else {
                        Log.i(TAG,"用户不允许USB访问设备！");
                        msgBuffer.append("用户不允许USB访问设备！\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }
            }

            //USB连接上手机时会发送广播android.hardware.usb.action.USB_STATE"及UsbManager.ACTION_USB_DEVICE_ATTACHED
            if (action.equals("android.hardware.usb.action.USB_STATE") | action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {//判断其中一个就可以了
                Log.i(TAG, "USB已经连接！");
                msgBuffer.append("USB已经连接！\r\n");
                handler.sendEmptyMessage(0);
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {//USB被拔出
                Log.i(TAG,"USB连接断开！");
                msgBuffer.append("USB连接断开！\r\n");
                handler.sendEmptyMessage(0);
                finish();
            }
        }
    };

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {

        @Override
        public void onReceiveInitCiphy(boolean blnIsInitSuc) {
            super.onReceiveInitCiphy(blnIsInitSuc);
        }

        @Override
        public void onReceiveDeviceAuth(byte[] authData) {
            super.onReceiveDeviceAuth(authData);
        }

        @Override
        //寻到卡片回调
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus || cardType == UsbNfcDevice.CARD_TYPE_NO_DEFINE) {
                return;
            }

            System.out.println("Activity接收到激活卡片回调：UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS));

            if (cardType == CARD_TYPE_125K) {
                if (msgBuffer.length() > 200) {
                    msgBuffer.delete(0, msgBuffer.length());
                }
                msgBuffer.append("\r\n寻到125K ID卡 UID -> " + StringTool.byteHexToSting(bytCardSn));
                handler.sendEmptyMessage(0);
                return;
            }

            final int cardTypeTemp = cardType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean isReadWriteCardSuc;
                    try {
//                        if (usbNfcDevice.isAutoSearchCard()) {
                            //如果是自动寻卡的，寻到卡后，先关闭自动寻卡
                            usbNfcDevice.stoptAutoSearchCard();
                            isReadWriteCardSuc = readWriteCardDemo(cardTypeTemp);

                            //读卡结束，重新打开自动寻卡
                            startAutoSearchCard();
//                        }
//                        else {
//                            isReadWriteCardSuc = readWriteCardDemo(cardTypeTemp);
//
//                            //如果不是自动寻卡，读卡结束,关闭天线
//                            usbNfcDevice.closeRf();
//                        }

                        //打开蜂鸣器提示读卡完成
                        if (isReadWriteCardSuc) {
                            usbNfcDevice.openBeep(50, 50, 3);  //读写卡成功快响3声
                        }
                        else {
                            //usbNfcDevice.openBeep(100, 100, 2); //读写卡失败慢响2声
                            //读卡失败进行重读
                            usbNfcDevice.closeRf();
                        }
                    } catch (DeviceNoResponseException e) {
                        e.printStackTrace();
                        //读卡结束，重新打开自动寻卡
                        try {
                            startAutoSearchCard();
                        } catch (DeviceNoResponseException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }).start();
        }

        @Override
        public void onReceiveRfmSentApduCmd(byte[] bytApduRtnData) {
            super.onReceiveRfmSentApduCmd(bytApduRtnData);

            //System.out.println("Activity接收到APDU回调：" + StringTool.byteHexToSting(bytApduRtnData) String.format("%02x", b));
            String lenString = String.format("%04X", 1000);
        }

        @Override
        public void onReceiveRfmClose(boolean blnIsCloseSuc) {
            super.onReceiveRfmClose(blnIsCloseSuc);
        }

        @Override
        //按键返回回调
        public void onReceiveButtonEnter(byte keyValue) {
            if (keyValue == DeviceManager.BUTTON_VALUE_SHORT_ENTER) { //按键短按
                System.out.println("Activity接收到按键短按回调");
                msgBuffer.append("按键短按\r\n");
                handler.sendEmptyMessage(0);
            }
            else if (keyValue == DeviceManager.BUTTON_VALUE_LONG_ENTER) { //按键长按
                System.out.println("Activity接收到按键长按回调");
                msgBuffer.append("按键长按\r\n");
                handler.sendEmptyMessage(0);
            }
        }
    };

//    //读卡按键监听
//    private class SendButtonListener implements View.OnClickListener {
//        @Override
//        public void onClick(View v) {
//            //寻卡一次
//            usbNfcDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, new DeviceManager.onReceiveRfnSearchCardListener() {
//                @Override
//                public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
//                    //在此进行验证密码、读写操作
//
//                }
//            });
//        }
//    }

    //清空显示按键监听
    private class claerButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//
//                }
//            }).start();

            msgBuffer.delete(0, msgBuffer.length());
            handler.sendEmptyMessage(0);

//            byte[] sendBytes = new byte[]{
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
//                    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99};
//            StringBuffer stringBuffer = new StringBuffer();
//            for (int i=0; i<sendBytes.length; i++) {
//                stringBuffer.append(String.format("%02x", sendBytes[i]));
//            }
//
//            final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
//            Date curDate =  new Date(System.currentTimeMillis());
//            msgBuffer.append(String.format("发送时间：" + formatter.format(curDate) + "\r\n"));
//            msgBuffer.append(String.format("发送数据长度：%d\r\n", sendBytes.length));
//            msgBuffer.append("发送的数据：\r\n" + stringBuffer);
//            usbNfcDevice.requestPalTestChannel(sendBytes,
//                    new DeviceManager.onReceivePalTestChannelListener() {
//                        @Override
//                        public void onReceivePalTestChannel(byte[] returnData) {
//                            Date curDate =  new Date(System.currentTimeMillis());
//                            msgBuffer.append(String.format("\r\n发送完成！\r\n"));
//                            StringBuffer stringBuffer = new StringBuffer();
//                            for (int i=0; i<returnData.length; i++) {
//                                stringBuffer.append(String.format("%02x", returnData[i]));
//                            }
//                            System.out.println(stringBuffer);
//                            msgBuffer.append("开始接收数据：\r\n" + stringBuffer);
//                            msgBuffer.append(String.format("\r\n接收数据长度：%d\r\n", returnData.length));
//                            msgBuffer.append(String.format("结束时间：" + formatter.format(curDate) + "\r\n"));
//                            handler.sendEmptyMessage(0);
//                        }
//                    });
        }
    }

    //打开自动寻卡按键监听
    private class OpenAutoSearchCardButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (usbNfcDevice.usbHidManager.mDeviceConnection == null) {
                msgText.setText("USB设备未连接！");
                return;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //打开/关闭自动寻卡，100ms间隔，寻M1/UL卡
                        boolean isSuc = usbNfcDevice.startAutoSearchCard((byte) 10, ComByteManager.ISO14443_P4);
                        if (isSuc) {
                            msgBuffer.delete(0, msgBuffer.length());
                            msgBuffer.append("自动寻卡已打开！\r\n");
                            handler.sendEmptyMessage(0);
                        }
                        else {
                            msgBuffer.delete(0, msgBuffer.length());
                            msgBuffer.append("自动寻卡已关闭！\r\n");
                            handler.sendEmptyMessage(0);
                        }
                    } catch (DeviceNoResponseException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    //关闭自动寻卡按键监听
    private class CloseAutoSearchCardButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (usbNfcDevice.usbHidManager.mDeviceConnection == null) {
                msgText.setText("USB设备未连接！");
                return;
            }
            //打开/关闭自动寻卡，100ms间隔，寻M1/UL卡、CPU卡
            usbNfcDevice.requestRfmAutoSearchCard(false, (byte) 20, ComByteManager.ISO14443_P4, new DeviceManager.onReceiveAutoSearchCardListener() {
                @Override
                public void onReceiveAutoSearchCard(boolean isSuc) {
                    if (isSuc) {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("自动寻卡已打开！\r\n");
                        handler.sendEmptyMessage(0);
                    }
                    else {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("自动寻卡已关闭！\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }
            });
        }
    }

    //开始自动寻卡
    private boolean startAutoSearchCard() throws DeviceNoResponseException {
        //打开自动寻卡，300ms间隔，寻M1/UL卡
        boolean isSuc = false;
        int falseCnt = 0;
        do {
            isSuc = usbNfcDevice.startAutoSearchCard((byte) 30, ComByteManager.ISO14443_P4);
        }while (!isSuc && (falseCnt++ < 10));
        if (!isSuc){
            //msgBuffer.delete(0, msgBuffer.length());
            msgBuffer.append("不支持自动寻卡！\r\n");
            handler.sendEmptyMessage(0);
        }
        return isSuc;
    }

    //读写卡Demo
    private boolean readWriteCardDemo(int cardType) {
        switch (cardType) {
//            case CARD_TYPE_ISO4443_B:  //寻到 B cpu卡
//                final Iso14443bCard iso14443bCard = (Iso14443bCard) usbNfcDevice.getCard();
//                if (iso14443bCard != null) {
//                    msgBuffer.delete(0, msgBuffer.length());
//                    msgBuffer.append("寻到ISO14443-B卡->UID:(身份证发送0036000008指令获取UID)\r\n");
//                    handler.sendEmptyMessage(0);
//                    //获取身份证UID的指令流
//                    final byte[][] sfzCmdBytes = {
//                            {0x00, (byte) 0x84, 0x00, 0x00, 0x08},
//                            {0x00, 0x36, 0x00, 0x00, 0x08},
//                    };
//                    System.out.println("发送指令流");
//                    for (byte[] aBytes : sfzCmdBytes) {
//                        try {
//                            msgBuffer.append("发送：").append(StringTool.byteHexToSting(aBytes)).append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            byte returnBytes[] = iso14443bCard.transceive(aBytes);
//                            msgBuffer.append("返回：").append(StringTool.byteHexToSting(returnBytes)).append("\r\n");
//                            handler.sendEmptyMessage(0);
//                        } catch (CardNoResponseException e) {
//                            e.printStackTrace();
//                            return false;
//                        }
//                    }
//                }
//                break;

            case DeviceManager.CARD_TYPE_ISO4443_B:  //寻到 B cpu卡
                final Iso14443bCard iso14443bCard = (Iso14443bCard) usbNfcDevice.getCard();
                if (iso14443bCard != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到ISO14443-B卡->UID:(身份证发送0036000008指令获取UID)\r\n");
                    handler.sendEmptyMessage(0);

                    SamVIdCard samVIdCard = new SamVIdCard(usbNfcDevice);

                    try {
                        byte[] nfcReturnBytes = samVIdCard.getSamVInitData();

                        DKCloudID dkCloudID = new DKCloudID();
                        System.out.println("向服务器发送数据：" + StringTool.byteHexToSting(nfcReturnBytes));
                        byte[] cloudReturnByte = dkCloudID.dkCloudTcpDataExchange(nfcReturnBytes);
                        System.out.println("接收到服务器数据：" + StringTool.byteHexToSting(cloudReturnByte));
                        msgBuffer.append("正在解析:1%");
                        handler.sendEmptyMessage(0);
                        int schedule = 1;

                        if ( (cloudReturnByte != null) && (cloudReturnByte.length >= 2)
                                && ((cloudReturnByte[0] == 0x03) || (cloudReturnByte[0] == 0x04)) ) {
                            showReadWriteDialog("正在读取身份证信息,请不要移动身份证", 1);
                        }

                        while (true) {
                            if ( (cloudReturnByte == null) || (cloudReturnByte.length < 2)
                                    || ((cloudReturnByte[0] != 0x03) && (cloudReturnByte[0] != 0x04)) ) {

                                msgBuffer.delete(0, msgBuffer.length());
                                if ( cloudReturnByte == null ) {
                                    msgBuffer.append("服务器返回数据为空").append("\r\n");
                                }
                                else if (cloudReturnByte[0] == 0x05) {
                                    msgBuffer.append("解析失败, 请重新读卡").append("\r\n");
                                }
                                else if (cloudReturnByte[0] == 0x06) {
                                    msgBuffer.append("该设备未授权, 请联系www.derkiot.com获取授权").append("\r\n");
                                }
                                else if (cloudReturnByte[0] == 0x07) {
                                    msgBuffer.append("该设备已被禁用, 请联系www.derkiot.com").append("\r\n");
                                }
                                else if (cloudReturnByte[0] == 0x08) {
                                    msgBuffer.append("该账号已被禁用, 请联系www.derkiot.com").append("\r\n");
                                }
                                else if (cloudReturnByte[0] == 0x09) {
                                    msgBuffer.append("余额不足, 请联系www.derkiot.com充值").append("\r\n");
                                }
                                else {
                                    msgBuffer.append("未知错误").append("\r\n");
                                }
                                handler.sendEmptyMessage(0);
                                dkCloudID.Close();
                                return false;
                            }
                            else if (cloudReturnByte.length > 300) {
                                byte[] decrypted = null;
                                if ( cloudReturnByte[0] == 0x04 ) {
                                    decrypted = new byte[cloudReturnByte.length - 3];
                                    System.arraycopy(cloudReturnByte, 3, decrypted, 0, decrypted.length);
                                }

                                if (decrypted != null) {
                                    final IDCardData idCardData = new IDCardData(decrypted);
                                    System.out.println("解析成功：" + idCardData.toString());

                                    msgBuffer.delete(0, msgBuffer.length());
                                    msgBuffer.append("解析成功：" + idCardData.toString() + "\r\n");
                                    handler.sendEmptyMessage(0);

                                    //显示照片和指纹
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            //获取指纹数据
                                            String fingerprintString = "";
                                            if (idCardData.fingerprintBytes != null && idCardData.fingerprintBytes.length > 0) {
                                                fingerprintString = "\r\n指纹数据：\r\n" + StringTool.byteHexToSting(idCardData.fingerprintBytes);
                                            }

                                            SpannableString ss = new SpannableString(msgText.getText().toString()+"[smile]");
                                            //得到要显示图片的资源
                                            Drawable d = new BitmapDrawable(idCardData.PhotoBmp); //Drawable.createFromPath("mnt/sdcard/photo.bmp");
                                            //设置高度
                                            d.setBounds(0, 0, d.getIntrinsicWidth() * 10, d.getIntrinsicHeight() * 10);
                                            //跨度底部应与周围文本的基线对齐
                                            ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BASELINE);
                                            //附加图片
                                            ss.setSpan(span, msgText.getText().length(),msgText.getText().length()+"[smile]".length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                                            msgText.setText(ss);

                                            //显示指纹数据
                                            msgText.append(fingerprintString);
                                        }
                                    });
                                }
                                else {
                                    dkCloudID.Close();
                                    return false;
                                }
                                break;
                            }

                            nfcReturnBytes = samVIdCard.transceive(cloudReturnByte);
                            if (nfcReturnBytes.length == 2) {
                                msgBuffer.delete(0, msgBuffer.length());
                                msgBuffer.append("解析出错：").append(String.format("%d", ((nfcReturnBytes[0] & 0xff) << 8) | (nfcReturnBytes[1] & 0xff) )).append("\r\n");
                                handler.sendEmptyMessage(0);
                                dkCloudID.Close();
                                return false;
                            }

                            System.out.println("向服务器发送数据：" + StringTool.byteHexToSting(nfcReturnBytes));
                            cloudReturnByte = dkCloudID.dkCloudTcpDataExchange(nfcReturnBytes);
                            System.out.println("接收到服务器数据：" + StringTool.byteHexToSting(cloudReturnByte));
                            msgBuffer.delete(0, msgBuffer.length());

                            //进度显示
                            if (schedule > 4) {
                                msgBuffer.append(String.format("正在解析%%%d", (int)((++schedule) * 100 / 45.0)));
                                handler.sendEmptyMessage(0);
                                showReadWriteDialog("正在读取身份证信息,请不要移动身份证", (int)(schedule * 100 / 45.0));
                            }
                            else {
                                msgBuffer.append(String.format("正在解析%%%d", (int) ((++schedule) * 100 / 4.0)));
                                handler.sendEmptyMessage(0);
                                showReadWriteDialog("正在读取身份证信息,请不要移动身份证", (int) (schedule * 100 / 4.0));
                            }
                        }
                        dkCloudID.Close();
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("解析出错：").append(e.getMessage()).append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                    finally {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                readWriteDialog.dismiss();
                            }
                        });
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_ISO4443_A:   //寻到A CPU卡
                final CpuCard cpuCard = (CpuCard) usbNfcDevice.getCard();
                if (cpuCard != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到CPU卡->UID:").append(cpuCard.uidToString()).append("\r\n");
                    handler.sendEmptyMessage(0);
                    try{
                        //选择深圳通主文件
                        byte[] bytApduRtnData = cpuCard.transceive(SZTCard.getSelectMainFileCmdByte());
                        if (bytApduRtnData.length <= 2) {
                            System.out.println("不是深圳通卡，当成银行卡处理！");
                            //选择储蓄卡交易文件
                            String cpuCardType;
                            bytApduRtnData = cpuCard.transceive(FinancialCard.getSelectDepositCardPayFileCmdBytes());
                            if (bytApduRtnData.length <= 2) {
                                System.out.println("不是储蓄卡，当成借记卡处理！");
                                //选择借记卡交易文件
                                bytApduRtnData = cpuCard.transceive(FinancialCard.getSelectDebitCardPayFileCmdBytes());
                                if (bytApduRtnData.length <= 2) {
                                    msgBuffer.append("未知CPU卡！");
                                    handler.sendEmptyMessage(0);
                                    return false;
                                }
                                else {
                                    cpuCardType = "储蓄卡";
                                }
                            }
                            else {
                                cpuCardType = "借记卡";
                            }

                            //读交易记录
                            System.out.println("发送APDU指令-读10条交易记录");
                            for (int i = 1; i <= 10; i++) {
                                bytApduRtnData = cpuCard.transceive(FinancialCard.getTradingRecordCmdBytes((byte) i));
                                msgBuffer.append(FinancialCard.extractTradingRecordFromeRturnBytes(bytApduRtnData));
                                handler.sendEmptyMessage(0);
                            }
                        }
                        else {  //深圳通处理流程
                            bytApduRtnData = cpuCard.transceive(SZTCard.getBalanceCmdByte());
                            if (SZTCard.getBalance(bytApduRtnData) == null) {
                                msgBuffer.append("未知CPU卡！");
                                handler.sendEmptyMessage(0);
                                System.out.println("未知CPU卡！");
                                return false;
                            }
                            else {
                                msgBuffer.append("深圳通余额：").append(SZTCard.getBalance(bytApduRtnData));
                                handler.sendEmptyMessage(0);
                                System.out.println("余额：" + SZTCard.getBalance(bytApduRtnData));
                                //读交易记录
                                System.out.println("发送APDU指令-读10条交易记录");
                                for (int i = 1; i <= 10; i++) {
                                    bytApduRtnData = cpuCard.transceive(SZTCard.getTradeCmdByte((byte) i));
                                    msgBuffer.append("\r\n").append(SZTCard.getTrade(bytApduRtnData));
                                    handler.sendEmptyMessage(0);
                                }
                            }
                        }
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_FELICA:  //寻到FeliCa
                FeliCa feliCa = (FeliCa) usbNfcDevice.getCard();
                if (feliCa != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("读取服务008b中数据块0000的数据：\r\n");
                    handler.sendEmptyMessage(0);
                    byte[] pServiceList = {(byte) 0x8b, 0x00};
                    byte[] pBlockList = {0x00, 0x00, 0x00};
                    try {
                        byte[] pBlockData = feliCa.read((byte) 1, pServiceList, (byte) 1, pBlockList);
                        msgBuffer.append(StringTool.byteHexToSting(pBlockData)).append("\r\n");
                        handler.sendEmptyMessage(0);
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_ULTRALIGHT: //寻到Ultralight卡
                String writeText = System.currentTimeMillis() + "专业非接触式智能卡读写器方案商！";
                if (msgText.getText().toString().length() > 0) {
                    writeText = msgText.getText().toString();
                }

                msgBuffer.delete(0, msgBuffer.length());

                final Ntag21x ntag21x = (Ntag21x) usbNfcDevice.getCard();
                if (ntag21x != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到Ultralight卡 ->UID:").append(ntag21x.uidToString()).append("\r\n");
                    handler.sendEmptyMessage(0);
                    try {
                        //读写单个块Demo
                        msgBuffer.append("开始读取块0数据：\r\n");
                        handler.sendEmptyMessage(0);
                        byte[] readTempBytes = ntag21x.read((byte) 0);
                        msgBuffer.append("返回：").append(StringTool.byteHexToSting(readTempBytes)).append("\r\n");
                        handler.sendEmptyMessage(0);

//                        //读写文本Demo，不带进度回调
//                        msgBuffer.append("开始写入文本：\r\n");
//                        handler.sendEmptyMessage(0);
//                        boolean isSuc = ntag21x.NdefTextWrite(writeText);
//                        if (isSuc) {
//                            msgBuffer.append("写数据成功！").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            msgBuffer.append("开始读取文本").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            String text = ntag21x.NdefTextRead();
//                            msgBuffer.append("读取成功：\r\n").append(text).append("\r\n");
//                            handler.sendEmptyMessage(0);
//                        }
//                        else {
//                            msgBuffer.append("写文本失败！").append("\r\n");
//                            showReadWriteDialog("正在读取数据", 0);
//                        }

                        //读写文本Demo，带进度回调
//                        msgBuffer.append("开始写入文本：\r\n");
//                        showReadWriteDialog("正在写入文本", 1);
//                        showReadWriteDialog("正在写入文本", 1);
//                        boolean isSuc = ntag21x.NdefTextWriteWithScheduleCallback(writeText, new Ntag21x.onReceiveScheduleListener() {
//                            @Override
//                            public void onReceiveSchedule(int rate) {
//                                //显示进度，写入过程会不断回调到此
//                                showReadWriteDialog("正在写入文本", rate);
//                            }
//                        });
//
//                        if (isSuc) {
//                            msgBuffer.append("写数据成功！").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            msgBuffer.append("开始读取文本").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            String text = ntag21x.NdefTextReadWithScheduleCallback(new Ntag21x.onReceiveScheduleListener() {
//                                @Override
//                                public void onReceiveSchedule(int rate) {
//                                    showReadWriteDialog("正在读取数据", rate);
//                                }
//                            });
//                            msgBuffer.append("读取成功：\r\n").append(text).append("\r\n");
//                            handler.sendEmptyMessage(0);
//                        }
//                        else {
//                            msgBuffer.append("写文本失败！").append("\r\n");
//                            showReadWriteDialog("正在读取数据", 0);
//                        }

//                        //任意长度读写Demo,不带进度回调方式
//                        byte[] writeBytes = new byte[888];
//                        Arrays.fill(writeBytes, (byte) 0xAA);
//                        msgBuffer.append("开始写888个字节数据：0x30").append("\r\n");
//                        handler.sendEmptyMessage(0);
//                        boolean isSuc = ntag21x.longWrite((byte) 4, writeBytes);
//                        if (isSuc) {
//                            msgBuffer.append("写数据成功！").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            msgBuffer.append("开始读888个字节数据").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                            readTempBytes = ntag21x.longRead((byte) 4, (byte) (888 / 4));
//                            msgBuffer.append("读取成功：\r\n").append(StringTool.byteHexToSting(readTempBytes)).append("\r\n");
//                            handler.sendEmptyMessage(0);
//                        }
//                        else {
//                            msgBuffer.append("写数据失败！").append("\r\n");
//                            handler.sendEmptyMessage(0);
//                        }

                        //任意长度读写Demo，带进度回调
                        showReadWriteDialog("正在写入数据", 1);
                        showReadWriteDialog("正在写入数据", 1);
                        byte[] writeBytes = new byte[888];
                        Arrays.fill(writeBytes, (byte) 0x30);
                        msgBuffer.append("开始写888个字节数据：0x30").append("\r\n");
                        handler.sendEmptyMessage(0);
                        boolean isSuc = ntag21x.longWriteWithScheduleCallback((byte) 4, writeBytes, new Ntag21x.onReceiveScheduleListener() {
                            @Override
                            public void onReceiveSchedule(int rate) {
                                showReadWriteDialog("正在写入数据", rate);
                            }
                        });
                        if (isSuc) {
                            msgBuffer.append("写数据成功！").append("\r\n");
                            handler.sendEmptyMessage(0);
                            msgBuffer.append("开始读888个字节数据").append("\r\n");
                            handler.sendEmptyMessage(0);
                            readTempBytes = ntag21x.longReadWithScheduleCallback((byte) 4, (byte) (888 / 4), new Ntag21x.onReceiveScheduleListener() {
                                @Override
                                public void onReceiveSchedule(int rate) {
                                    showReadWriteDialog("正在读取数据", rate);
                                }
                            });
                            msgBuffer.append("读取成功：\r\n").append(StringTool.byteHexToSting(readTempBytes)).append("\r\n");
                            showReadWriteDialog("正在读取数据", 100);
                        }
                        else {
                            msgBuffer.append("写数据失败！").append("\r\n");
                            showReadWriteDialog("正在读取数据", 0);
                        }

//                        msgBuffer.append("开始读100个字节数据").append("\r\n");
//                        handler.sendEmptyMessage(0);
//                        readTempBytes = ntag21x.longReadWithScheduleCallback((byte) 4, (byte) (100 / 4), new Ntag21x.onReceiveScheduleListener() {
//                            @Override
//                            public void onReceiveSchedule(int rate) {
//                                showReadWriteDialog("正在读取数据", rate);
//                            }
//                        });
//                        msgBuffer.append("读取成功：\r\n").append(StringTool.byteHexToSting(readTempBytes)).append("\r\n");
//                        showReadWriteDialog("正在读取数据", 100);

                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        msgBuffer.append(e.getMessage()).append("\r\n");
                        showReadWriteDialog("正在写入数据", 0);
                        return false;
                    }
                }
                break;
//            case DeviceManager.CARD_TYPE_MIFARE:   //寻到Mifare卡
//                final Mifare mifare = (Mifare) usbNfcDevice.getCard();
//                if (mifare != null) {
//                    msgBuffer.delete(0, msgBuffer.length());
//                    msgBuffer.append("寻到Mifare卡->UID:").append(mifare.uidToString()).append("\r\n");
//                    handler.sendEmptyMessage(0);
//                    byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
//
//                    //读所有扇区
//                    for (int i=0;i<16;i++) {
//                        int blockindex=4*i;
//                        boolean isReadSuc = false;
//                        boolean anth = false;
//                        int readCnt = 0;
//                        do {
//                            try {
//                                anth = mifare.authenticate((byte) (blockindex & 0xFF), Mifare.MIFARE_KEY_TYPE_A, key);
//                            } catch (CardNoResponseException e) {
//                                e.printStackTrace();
//                            }
//                        }while (!anth && (readCnt++ < 2));
//                        if (anth) {
//                            isReadSuc = false;
//                            readCnt = 0;
//                            do {
//                                try {
//                                    byte[] readDataBytes = mifare.read((byte) (blockindex & 0xFF));
//                                    msgBuffer.append("块" + blockindex + "数据:").append(StringTool.byteHexToSting(readDataBytes)).append("\r\n");
//                                    isReadSuc = true;
//                                } catch (CardNoResponseException e) {
//                                    e.printStackTrace();
//                                }
//                            }while (!isReadSuc && (readCnt++ < 2));
//
//                            isReadSuc = false;
//                            readCnt = 0;
//                            do {
//                                try {
//                                    byte[] readDataBytes1 = mifare.read((byte) ((++blockindex) & 0xFF));
//                                    msgBuffer.append("块"+blockindex+"数据:").append(StringTool.byteHexToSting(readDataBytes1)).append("\r\n");
//                                    isReadSuc = true;
//                                }catch (CardNoResponseException e) {
//                                    e.printStackTrace();
//                                }
//                            }while (!isReadSuc && (readCnt++ < 2));
//
//                            isReadSuc = false;
//                            readCnt = 0;
//                            do {
//                                try {
//                                    byte[]readDataBytes2 = mifare.read((byte) ((++blockindex) & 0xFF));
//                                    msgBuffer.append("块"+blockindex+"数据:").append(StringTool.byteHexToSting(readDataBytes2)).append("\r\n");
//                                    isReadSuc = true;
//                                }catch (CardNoResponseException e) {
//                                    e.printStackTrace();
//                                }
//                            }while (!isReadSuc && (readCnt++ < 2));
//
//                            isReadSuc = false;
//                            readCnt = 0;
//                            do {
//                                try {
//                                    byte[]readDataBytes3 = mifare.read((byte) ((++blockindex) & 0xFF));
//                                    msgBuffer.append("块"+blockindex+"数据:").append(StringTool.byteHexToSting(readDataBytes3)).append("\r\n");
//                                    isReadSuc = true;
//                                }catch (CardNoResponseException e) {
//                                    e.printStackTrace();
//                                }
//                            }while (!isReadSuc && (readCnt++ < 2));
//                        } else {
//                            msgBuffer.append("验证密码失败\r\n");
//                            handler.sendEmptyMessage(0);
//                            return false;
//                        }
//                    }
//                    handler.sendEmptyMessage(0);
//                }
//                break;

            case DeviceManager.CARD_TYPE_MIFARE:   //寻到Mifare卡
                final Mifare mifare = (Mifare) usbNfcDevice.getCard();
                if (mifare != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到Mifare卡->UID:").append(mifare.uidToString()).append("\r\n");
                    msgBuffer.append("开始验证第1块密码\r\n");
                    handler.sendEmptyMessage(0);
                    byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
                    try {
                        boolean anth = mifare.authenticate((byte) 1, Mifare.MIFARE_KEY_TYPE_A, key);
                        if (anth) {
//                            byte[] readDataBytes = mifare.read((byte) 0x10);
//                            msgBuffer.append("块16数据:").append(StringTool.byteHexToSting(readDataBytes)).append("\r\n");
//                            handler.sendEmptyMessage(0);
//
//                            readDataBytes = mifare.read((byte) 0x11);
//                            msgBuffer.append("块17数据:").append(StringTool.byteHexToSting(readDataBytes)).append("\r\n");
//                            handler.sendEmptyMessage(0);

                            msgBuffer.append("验证密码成功\r\n");
                            msgBuffer.append("写00112233445566778899001122334455到块1\r\n");
                            handler.sendEmptyMessage(0);
                            boolean isSuc = mifare.write((byte)1, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
                            if (isSuc) {
                                msgBuffer.append("写成功！\r\n");
                                msgBuffer.append("读块1数据\r\n");
                                handler.sendEmptyMessage(0);
                                byte[] readDataBytes = mifare.read((byte) 1);
                                msgBuffer.append("块1数据:").append(StringTool.byteHexToSting(readDataBytes)).append("\r\n");
                                handler.sendEmptyMessage(0);
                            } else {
                                msgBuffer.append("写失败！\r\n");
                                handler.sendEmptyMessage(0);
                                return false;
                            }
                        }
                        else {
                            msgBuffer.append("验证密码失败\r\n");
                            handler.sendEmptyMessage(0);
                            return false;
                        }
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_ISO15693: //寻到15693卡
                final Iso15693Card iso15693Card = (Iso15693Card) usbNfcDevice.getCard();
                if (iso15693Card != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到15693卡->UID:").append(iso15693Card.uidToString()).append("\r\n");
                    msgBuffer.append("读块0数据\r\n");
                    handler.sendEmptyMessage(0);
                    try {
                        //读写单个块Demo
                        msgBuffer.append("写数据01020304到块4").append("\r\n");
                        handler.sendEmptyMessage(0);
                        boolean isSuc = iso15693Card.write((byte)4, new byte[] {0x01, 0x02, 0x03, 0x04});
                        if (isSuc) {
                            msgBuffer.append("写数据成功！").append("\r\n");
                            handler.sendEmptyMessage(0);
                        }
                        else {
                            msgBuffer.append("写数据失败！").append("\r\n");
                            handler.sendEmptyMessage(0);
                        }
                        msgBuffer.append("读块4数据").append("\r\n");
                        handler.sendEmptyMessage(0);
                        byte[] bytes = iso15693Card.read((byte) 4);
                        msgBuffer.append("块4数据：").append(StringTool.byteHexToSting(bytes)).append("\r\n");
                        handler.sendEmptyMessage(0);

                        //读写多个块Demo
                        msgBuffer.append("写数据0102030405060708到块5、6").append("\r\n");
                        handler.sendEmptyMessage(0);
                        isSuc = iso15693Card.writeMultiple((byte)5, (byte)2, new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
                        if (isSuc) {
                            msgBuffer.append("写数据成功！").append("\r\n");
                            handler.sendEmptyMessage(0);
                        }
                        else {
                            msgBuffer.append("写数据失败！").append("\r\n");
                            handler.sendEmptyMessage(0);
                        }
                        msgBuffer.append("读块5、6数据").append("\r\n");
                        handler.sendEmptyMessage(0);
                        bytes = iso15693Card.ReadMultiple((byte) 5, (byte)2);
                        msgBuffer.append("块5、6数据：").append(StringTool.byteHexToSting(bytes)).append("\r\n");
                        handler.sendEmptyMessage(0);
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                break;
        }
        return true;
    }

    //发送读写进度条显示Handler
    private void showReadWriteDialog(String msg, int rate) {
        Message message = new Message();
        message.what = 4;
        message.arg1 = rate;
        message.obj = msg;
        handler.sendMessage(message);
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            msgText.setText(msgBuffer);

            switch (msg.what) {
                case 1:
                    break;
                case 2:
                    break;
                case 3:
                    break;

                case 4:   //读写进度条
                    if ((msg.arg1 == 0) || (msg.arg1 == 100)) {
                        readWriteDialog.dismiss();
                        readWriteDialog.setProgress(0);
                    } else {
                        readWriteDialog.setMessage((String) msg.obj);
                        readWriteDialog.setProgress(msg.arg1);
                        if (!readWriteDialog.isShowing()) {
                            readWriteDialog.show();
                        }
                    }
                    break;
                case 7:
                    break;
            }
        }
    };
}
