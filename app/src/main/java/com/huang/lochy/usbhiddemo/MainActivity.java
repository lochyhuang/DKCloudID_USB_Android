package com.huang.lochy.usbhiddemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
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
import com.dk.usbNfc.DKCloudID.DKCloudID;
import com.dk.usbNfc.DKCloudID.IDCard;
import com.dk.usbNfc.DKCloudID.IDCardData;
import com.dk.usbNfc.Exception.DKCloudIDException;
import com.dk.usbNfc.OTA.DialogUtils;
import com.dk.usbNfc.OTA.Ymodem;
import com.dk.usbNfc.UsbHidManager.UsbHidManager;
import com.dk.usbNfc.DeviceManager.UsbNfcDevice;
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

import java.io.File;

import static com.dk.usbNfc.DeviceManager.DeviceManager.CARD_TYPE_125K;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private MyTTS myTTS;
    static long time_start = 0;
    static long time_end = 0;
    IDCard idCard = null;

    private UsbNfcDevice usbNfcDevice = null;
    private EditText msgText = null;
    private ProgressDialog readWriteDialog = null;
    private AlertDialog.Builder alertDialog = null;

    private StringBuffer msgBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //语音初始化
        myTTS = new MyTTS(this);

        //UI初始化
        initUI();

        //usb_nfc设备初始化
        if (usbNfcDevice == null) {
            usbNfcDevice = new UsbNfcDevice(MainActivity.this);
            usbNfcDevice.setCallBack(deviceManagerCallback);

            //USB权限广播
            IntentFilter filter = new IntentFilter(UsbHidManager.ACTION_USB_PERMISSION);
            //USB状态广播
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            registerReceiver(usbReceiver, filter);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (usbNfcDevice.usbHidManager.mDeviceConnection != null) {
                        msgBuffer.append("\r\n").append("USB设备已连接！");
                        handler.sendEmptyMessage(0);

                        byte versionsByts = usbNfcDevice.getDeviceVersions();
                        msgBuffer.append(String.format("\r\n设备版本：%02x", versionsByts));
                        handler.sendEmptyMessage(0);

                        //OTA模式
                        if ((versionsByts & 0xff) < (0x40 & 0xFF)) {
                            startOTA();
                        }
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

        msgBuffer.append("USB_HID_NFC Demo v3.0.0 20210408");
        handler.sendEmptyMessage(0);
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

                //打开USB
                usbNfcDevice.usbHidManager.close();
                usbNfcDevice.usbHidManager.open();
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {//USB被拔出
                Log.i(TAG,"USB连接断开！");
                msgBuffer.append("USB连接断开！\r\n");
                handler.sendEmptyMessage(0);

                finish();
                //关闭USB
            }
        }
    };

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        @Override
        //寻到卡片回调
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus || cardType == UsbNfcDevice.CARD_TYPE_NO_DEFINE) {
                return;
            }

            System.out.println("Activity接收到激活卡片回调：UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS));

            final int cardTypeTemp = cardType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean isReadWriteCardSuc;
                    try {
                        isReadWriteCardSuc = readWriteCardDemo(cardTypeTemp);

                        //打开蜂鸣器提示读卡完成
                        if (isReadWriteCardSuc) {
                            usbNfcDevice.openBeep(50, 50, 3);  //读写卡成功快响3声
                        }
                        else {
                            //usbNfcDevice.openBeep(100, 100, 2); //读写卡失败慢响2声
                            //读卡失败，关闭一次天线让读卡器自动进行重读
                            usbNfcDevice.closeRf();
                        }
                    } catch (DeviceNoResponseException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    };

    //读写卡Demo
    private synchronized boolean readWriteCardDemo(int cardType) {
        switch (cardType) {
            case DeviceManager.CARD_TYPE_ISO4443_B:  //寻到 B cpu卡、身份证
                final Iso14443bCard iso14443bCard = (Iso14443bCard) usbNfcDevice.getCard();
                if (iso14443bCard != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到身份证，正在解析，请勿移动身份证！").append("\r\n");
                    handler.sendEmptyMessage(0);
                    myTTS.speak("正在读卡，请勿移动身份证");

                    SamVIdCard samVIdCard = new SamVIdCard(usbNfcDevice);
                    idCard = new IDCard(samVIdCard);

                    time_start = System.currentTimeMillis();
                    int cnt = 0;
                    do {
                        try {
                            /**
                             * 获取身份证数据，带进度回调，如果不需要进度回调可以去掉进度回调参数或者传入null
                             * 注意：此方法为同步阻塞方式，需要一定时间才能返回身份证数据，期间身份证不能离开读卡器！
                             */
                            IDCardData idCardData = idCard.getIDCardData(new IDCard.onReceiveScheduleListener() {
                                @Override
                                public void onReceiveSchedule(int rate) {  //读取进度回调
                                    showReadWriteDialog("正在读取身份证信息,请不要移动身份证", rate);
                                    if (rate == 100) {
                                        time_end = System.currentTimeMillis();
                                        /**
                                         * 这里已经完成读卡，可以开身份证了，在此提示用户读取成功或者打开蜂鸣器提示可以拿开身份证了
                                         */

                                        myTTS.speak("读取成功");
                                    }
                                }
                            });

                            /**
                             * 显示身份证数据
                             */
                            showIDCardData(idCardData);
                            //返回读取成功
                            return true;
                        } catch (DKCloudIDException e) {   //服务器返回异常，重复5次解析
                            e.printStackTrace();

                            //显示错误信息
                            msgBuffer.delete(0, msgBuffer.length());
                            msgBuffer.append(e.getMessage()).append("\r\n");
                            handler.sendEmptyMessage(0);
                        }
                        catch (CardNoResponseException e) {    //卡片读取异常，直接退出，需要重新读卡
                            e.printStackTrace();

                            //显示错误信息
                            msgBuffer.delete(0, msgBuffer.length());
                            msgBuffer.append(e.getMessage()).append("\r\n");
                            handler.sendEmptyMessage(0);
                            //返回读取失败
                            myTTS.speak("请不要移动身份证");
                            return false;
                        } finally {
                            //读卡结束关闭进度条显示
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (readWriteDialog.isShowing()) {
                                        readWriteDialog.dismiss();
                                    }
                                    readWriteDialog.setProgress(0);
                                }
                            });
                        }
                    }while ( cnt++ < 5 );  //如果服务器返回异常则重复读5次直到成功

                    myTTS.speak("读取失败，请重新刷卡");
                }
                break;
            case DeviceManager.CARD_TYPE_ISO4443_A:   //寻到A CPU卡
                final CpuCard cpuCard = (CpuCard) usbNfcDevice.getCard();
                if (cpuCard != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到CPU卡->UID:").append(cpuCard.uidToString()).append("\r\n");
                    handler.sendEmptyMessage(0);
                }
                break;
            case DeviceManager.CARD_TYPE_FELICA:  //寻到FeliCa
                FeliCa feliCa = (FeliCa) usbNfcDevice.getCard();
                if (feliCa != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到feliCa ->UID:").append(feliCa.uidToString()).append("\r\n");
                    handler.sendEmptyMessage(0);
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
                }
                break;
            case DeviceManager.CARD_TYPE_MIFARE:   //寻到Mifare卡
                final Mifare mifare = (Mifare) usbNfcDevice.getCard();
                if (mifare != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到Mifare卡->UID:").append(mifare.uidToString()).append("\r\n");
                    handler.sendEmptyMessage(0);
                }
                break;
            case DeviceManager.CARD_TYPE_ISO15693: //寻到15693卡
                final Iso15693Card iso15693Card = (Iso15693Card) usbNfcDevice.getCard();
                if (iso15693Card != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("寻到15693卡->UID:").append(iso15693Card.uidToString()).append("\r\n");
                    handler.sendEmptyMessage(0);
                }
                break;
        }
        return true;
    }

    //固件升级
    private void startOTA() {
        msgBuffer.delete(0, msgBuffer.length());
        msgBuffer.append("正在升级固件...");
        handler.sendEmptyMessage(0);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DialogUtils.select_file(MainActivity.this, new DialogUtils.DialogSelection() {
                    @Override
                    public void onSelectedFilePaths(String[] files) {
                        if (files.length == 1) {
                            final String filePath = files[0];

                            /**
                             * 升级说明：升级需要将DK26MEEncrypt.bin文件放到手机根目录，
                             * 第一次点击升级按键APP会重启，如果APP退出去后没有自动重启，需要手动重新打开APP
                             * APP重启后如果显示的固件版本是03，这时模块处于升级模式，需要再次点击升级按键完成升级
                             * APP显示升级完成后，需要等待几秒钟模块灯灭掉后即升级完成
                             */
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    Ymodem ymodem = new Ymodem(usbNfcDevice);

                                    final File file = new File(filePath);
                                    if (!file.exists()) {
                                        msgBuffer.delete(0, msgBuffer.length());
                                        msgBuffer.append("升级文件未找到！");
                                        handler.sendEmptyMessage(0);
                                        return;
                                    }

                                    boolean isSuc = ymodem.YmodemUploadFile(file, new Ymodem.onReceiveScheduleListener() {
                                        @Override
                                        public void onReceiveSchedule(int rate) {
                                            showReadWriteDialog("正在升级", rate);
                                        }
                                    });

                                    if (isSuc) {
                                        msgBuffer.delete(0, msgBuffer.length());
                                        msgBuffer.append("升级成功！");
                                        handler.sendEmptyMessage(0);
                                    }
                                    else {
                                        msgBuffer.delete(0, msgBuffer.length());
                                        msgBuffer.append("升级失败！");
                                        handler.sendEmptyMessage(0);
                                    }
                                }
                            }).start();
                        }
                    }
                });
            }
        });
    }

    //UI初始化
    private void initUI() {
        msgBuffer = new StringBuffer();

        msgText = (EditText)findViewById(R.id.msgText);
        Button clearButton = (Button) findViewById(R.id.clearButton);
        Button openAutoSearchCard = (Button)findViewById(R.id.openAutoSearchCard);
        Button closeAutoSearchCard = (Button)findViewById(R.id.closeAutoSearchCard);
        Button otaButton = (Button)findViewById(R.id.ota_button);

        readWriteDialog = new ProgressDialog(MainActivity.this);
        readWriteDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // 设置ProgressDialog 标题
        readWriteDialog.setTitle("请稍等");
        // 设置ProgressDialog 提示信息
        readWriteDialog.setMessage("正在读写数据……");
        readWriteDialog.setMax(100);

        //清空显示按键
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                msgBuffer.delete(0, msgBuffer.length());
                handler.sendEmptyMessage(0);
            }
        });

        //固件升级按键
        otaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (usbNfcDevice.usbHidManager.mDeviceConnection == null) {
                    msgText.setText("USB设备未连接！");
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //查询固件版本是否处于OTA模式下
                        try {
                            int version = usbNfcDevice.getDeviceVersions();
                            if ( version >= 0x40 ) {
                                //进入升级模式
                                try {
                                    usbNfcDevice.OTACmd(new byte[]{0x00}, 1000);
                                } catch (DeviceNoResponseException e) {
                                    //e.printStackTrace();
                                }

                                Log.d(TAG, "正在进入升级模式");
                            }

                            startOTA();
                        } catch (DeviceNoResponseException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });
    }

    //显示身份证数据
    private void showIDCardData(IDCardData idCardData) {
        final IDCardData theIDCardData = idCardData;

        //显示照片和指纹
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgText.setText("解析成功，读卡用时:" + (time_end - time_start) + "ms\r\n" + theIDCardData.toString() + "\r\n");

                //获取指纹数据
                String fingerprintString = "";
                if (theIDCardData.fingerprintBytes != null && theIDCardData.fingerprintBytes.length > 0) {
                    fingerprintString = "\r\n指纹数据：\r\n" + StringTool.byteHexToSting(theIDCardData.fingerprintBytes);
                }

                SpannableString ss = new SpannableString(msgText.getText().toString()+"[smile]");
                //得到要显示图片的资源
                Drawable d = new BitmapDrawable(theIDCardData.PhotoBmp); //Drawable.createFromPath("mnt/sdcard/photo.bmp");
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

        //关闭USB
        usbNfcDevice.usbHidManager.close();
        usbNfcDevice = null;
        unregisterReceiver(usbReceiver);

        //关闭服务器连接
        DKCloudID.Close();
    }
}
