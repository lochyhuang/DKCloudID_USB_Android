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

import com.dk.usbNfc.Card.DESFire;
import com.dk.usbNfc.Card.SamVIdCard;
import com.dk.usbNfc.Card.Topaz;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.dk.usbNfc.DeviceManager.ComByteManager.ISO14443_P3;
import static com.dk.usbNfc.DeviceManager.ComByteManager.ISO14443_P4;
import static com.dk.usbNfc.DeviceManager.DeviceManager.CARD_TYPE_125K;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private MyTTS myTTS;
    static long time_start = 0;
    static long time_end = 0;
    IDCard idCard = null;

    private static UsbNfcDevice usbNfcDevice = null;
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
        }

        logViewln(null);
    }

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        @Override
        public void onReceiveConnectionStatus(boolean blnIsConnection) {
            if (blnIsConnection) {
                Log.i(TAG,"USB连接成功！");
                logViewln(null);
                logViewln("USB连接成功！");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            logViewln("USB设备已连接！");
                            byte versionsByts = usbNfcDevice.getDeviceVersions();
                            logViewln(String.format("设备版本：%02x", versionsByts));

                            try {
                                usbNfcDevice.closeRf();
                            } catch (DeviceNoResponseException e) {
                                e.printStackTrace();
                            }
                        } catch (DeviceNoResponseException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            else {
                Log.i(TAG,"USB连接断开！");
                logViewln(null);
                logViewln("USB连接断开！");
            }
        }

        @Override
        //寻到卡片回调
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus || cardType == UsbNfcDevice.CARD_TYPE_NO_DEFINE) {
                return;
            }

            Log.d(TAG, "Activity接收到激活卡片回调：UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS));

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

        //身份证开始请求云解析回调
        @Override
        public void onReceiveSamVIdStart(byte[] initData) {
            super.onReceiveSamVIdStart(initData);

            Log.d(TAG, "开始解析");
            logViewln(null);
            logViewln("正在读卡，请勿移动身份证!");
            myTTS.speak("正在读卡，请勿移动身份证");

            time_start = System.currentTimeMillis();
        }

        //身份证云解析进度回调
        @Override
        public void onReceiveSamVIdSchedule(int rate) {
            super.onReceiveSamVIdSchedule(rate);
            showReadWriteDialog("正在读取身份证信息,请不要移动身份证", rate);
            if (rate == 100) {
                time_end = System.currentTimeMillis();

                /**
                 * 这里已经完成读卡，可以拿开身份证了，在此提示用户读取成功或者打开蜂鸣器提示可以拿开身份证了
                 */
                myTTS.speak("读取成功");
            }
        }

        //身份证云解析异常回调
        @Override
        public void onReceiveSamVIdException(String msg) {
            super.onReceiveSamVIdException(msg);

            //显示错误信息
            logViewln(msg);

            //读卡结束关闭进度条显示
            hidDialog();

            //重复读
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        usbNfcDevice.closeRf();
//                    } catch (DeviceNoResponseException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }).start();
        }

        //身份证云解析明文结果回调
        @Override
        public void onReceiveIDCardData(IDCardData idCardData) {
            super.onReceiveIDCardData(idCardData);

            //显示身份证数据
            showIDCardData(idCardData);

            //重复读
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        usbNfcDevice.closeRf();
//                    } catch (DeviceNoResponseException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }).start();
        }
    };

    //读写卡Demo
    private synchronized boolean readWriteCardDemo(int cardType) {
        switch (cardType) {
            case DeviceManager.CARD_TYPE_ISO4443_A:   //寻到A CPU卡
                final CpuCard cpuCard = (CpuCard) usbNfcDevice.getCard();
                if (cpuCard != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    logViewln("寻到CPU卡->UID:" + cpuCard.uidToString() + "");
                    try{
                        //选择深圳通主文件
                        byte[] bytApduRtnData = cpuCard.transceive(SZTCard.getSelectMainFileCmdByte());
                        if (bytApduRtnData.length <= 2) {
                            Log.d(TAG, "不是深圳通卡，当成银行卡处理！");
                            //选择储蓄卡交易文件
                            String cpuCardType;
                            bytApduRtnData = cpuCard.transceive(FinancialCard.getSelectDepositCardPayFileCmdBytes());
                            if (bytApduRtnData.length <= 2) {
                                Log.d(TAG, "不是储蓄卡，当成借记卡处理！");
                                //选择借记卡交易文件
                                bytApduRtnData = cpuCard.transceive(FinancialCard.getSelectDebitCardPayFileCmdBytes());
                                if (bytApduRtnData.length <= 2) {
                                    logViewln("未知CPU卡！");
                                    return false;
                                }
                                else {
                                    cpuCardType = "储蓄卡";
                                }
                            }
                            else {
                                cpuCardType = "借记卡";
                            }

                            bytApduRtnData = cpuCard.transceive(FinancialCard.getCardNumberCmdBytes());
                            //提取银行卡卡号
                            String cardNumberString = FinancialCard.extractCardNumberFromeRturnBytes(bytApduRtnData);
                            if (cardNumberString == null) {
                                logViewln("未知CPU卡！");

                                return false;
                            }
                            logViewln("储蓄卡卡号：" + cardNumberString);


                            //读交易记录
                            Log.d(TAG, "发送APDU指令-读10条交易记录");
                            for (int i = 1; i <= 10; i++) {
                                bytApduRtnData = cpuCard.transceive(FinancialCard.getTradingRecordCmdBytes((byte) i));
                                logViewln(FinancialCard.extractTradingRecordFromeRturnBytes(bytApduRtnData));

                            }
                        }
                        else {  //深圳通处理流程
                            bytApduRtnData = cpuCard.transceive(SZTCard.getBalanceCmdByte());
                            if (SZTCard.getBalance(bytApduRtnData) == null) {
                                logViewln("未知CPU卡！");
                                Log.d(TAG, "未知CPU卡！");
                                return false;
                            }
                            else {
                                logViewln("深圳通余额：" + SZTCard.getBalance(bytApduRtnData));
                                Log.d(TAG, "余额：" + SZTCard.getBalance(bytApduRtnData));
                                //读交易记录
                                Log.d(TAG, "发送APDU指令-读10条交易记录");
                                for (int i = 1; i <= 10; i++) {
                                    bytApduRtnData = cpuCard.transceive(SZTCard.getTradeCmdByte((byte) i));
                                    logViewln(SZTCard.getTrade(bytApduRtnData));
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
                    logViewln("寻到feliCa ->UID:" + feliCa.uidToString());
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
                    logViewln("寻到Ultralight卡 ->UID:" + ntag21x.uidToString());
                }
                break;
            case DeviceManager.CARD_TYPE_MIFARE:   //寻到Mifare卡
                final Mifare mifare = (Mifare) usbNfcDevice.getCard();
                if (mifare != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    logViewln("寻到Mifare卡->UID:" + mifare.uidToString());
                }
                break;
            case DeviceManager.CARD_TYPE_ISO15693: //寻到15693卡
                final Iso15693Card iso15693Card = (Iso15693Card) usbNfcDevice.getCard();
                if (iso15693Card != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    logViewln("寻到15693卡->UID:" + iso15693Card.uidToString());
                    logViewln("读块0数据\r\n");

                    try {
                        //读写单个块Demo
                        logViewln("写数据01020304到块4");
                        boolean isSuc = iso15693Card.write((byte)4, new byte[] {0x01, 0x02, 0x03, 0x04});
                        if (isSuc) {
                            logViewln("写数据成功！");
                        }
                        else {
                            logViewln("写数据失败！");
                        }
                        logViewln("读块4数据");
                        byte[] bytes = iso15693Card.read((byte) 4);
                        logViewln("块4数据：" + StringTool.byteHexToSting(bytes));


                        //读写多个块Demo
                        logViewln("写数据0102030405060708到块5、6");
                        isSuc = iso15693Card.writeMultiple((byte)5, (byte)2, new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
                        if (isSuc) {
                            logViewln("写数据成功！");
                        }
                        else {
                            logViewln("写数据失败！");
                        }
                        logViewln("读块5、6数据");

                        bytes = iso15693Card.ReadMultiple((byte) 5, (byte)2);
                        logViewln("块5、6数据：" + StringTool.byteHexToSting(bytes));
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_DESFire:   //寻到A CPU卡
                final DESFire desFire = (DESFire) usbNfcDevice.getCard();
                if (desFire != null) {
                    msgBuffer.delete(0, msgBuffer.length());
                    logViewln("寻到DESFire卡->UID:" + desFire.uidToString() + "");
                    try {
                        //发送获取随机数APDU命令：0084000008
                        byte[] cmd = {0x00, (byte)0x84, 0x00, 0x00, 0x08};
                        logViewln("发送获取随机数APDU命令：0084000008");
                        byte[] rsp = desFire.transceive(cmd);
                        logViewln("返回：" + StringTool.byteHexToSting(rsp));
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_T1T:
                final Topaz topaz = (Topaz) usbNfcDevice.getCard();
                if (topaz != null) {
                    logViewln("寻到T1T卡->UID:" + topaz.uidToString() + "");
                }
                break;
        }
        return true;
    }

    //固件升级
    private void startOTA() {
        msgBuffer.delete(0, msgBuffer.length());
        logViewln("正在升级固件...");


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
                                        logViewln("升级文件未找到！");
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
                                        logViewln("升级成功！");
                                    }
                                    else {
                                        msgBuffer.delete(0, msgBuffer.length());
                                        logViewln("升级失败！");
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
                logViewln(null);
            }
        });

        //固件升级按键
        otaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (usbNfcDevice.usbHidManager.isClose()) {
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

        //打开自动寻卡开关
        openAutoSearchCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (usbNfcDevice.usbHidManager.isClose()) {
                    msgText.setText("USB设备未连接！");
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //打开/关闭自动寻卡，100ms间隔，寻M1/UL卡
                            boolean isSuc = usbNfcDevice.startAutoSearchCard((byte) 20, ISO14443_P4);
                            if (isSuc) {
                                logViewln(null);
                                logViewln("自动寻卡已打开！");
                            }
                            else {
                                logViewln(null);
                                logViewln("自动寻卡已关闭！");
                            }
                        } catch (DeviceNoResponseException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });

        //关闭自动寻卡开关
        closeAutoSearchCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (usbNfcDevice.usbHidManager.isClose()) {
                    msgText.setText("USB设备未连接！");
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //打开/关闭自动寻卡，100ms间隔，寻M1/UL卡
                            boolean isSuc = usbNfcDevice.stoptAutoSearchCard();
                            if (isSuc) {
                                logViewln(null);
                                logViewln("自动寻卡已关闭！");
                            }
                            else {
                                logViewln(null);
                                logViewln("自动寻卡已打开！");
                            }
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
                msgText.setText("解析成功，读卡用时:" + (time_end - time_start) + "ms\r\n" + theIDCardData.toString());

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

    //进度条显示
    private void showReadWriteDialog(String msg, int rate) {
        final int theRate = rate;
        final String theMsg = msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((theRate == 0) || (theRate == 100)) {
                    readWriteDialog.dismiss();
                    readWriteDialog.setProgress(0);
                } else {
                    readWriteDialog.setMessage(theMsg);
                    readWriteDialog.setProgress(theRate);
                    if (!readWriteDialog.isShowing()) {
                        readWriteDialog.show();
                    }
                }
            }
        });
    }

    //隐藏进度条
    private void hidDialog() {
        //关闭进度条显示
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

    private void logViewln(String string) {
        final String msg = string;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (msg == null) {
                    msgText.setText("");
                    return;
                }

                if (msgText.length() > 1000) {
                    msgText.setText("");
                }
                msgText.append(msg + "\r\n");
                int offset = msgText.getLineCount() * msgText.getLineHeight();
                if(offset > msgText.getHeight()){
                    msgText.scrollTo(0,offset - msgText.getHeight());
                }
            }
        });
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
        Log.d(TAG, "onDestroy");

        if (readWriteDialog != null) {
            readWriteDialog.dismiss();
        }

        //销毁
        usbNfcDevice.destroy();
    }
}
