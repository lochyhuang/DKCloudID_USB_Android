# DKCloudID_USB_Android

#### 介绍
深圳市德科物联技术有限公司的USB身份证阅读器Demo, 支持DK26ME、DK26ME-ANT、DK200ZK等模块。产品信息请访问[德科官网](http://www.derkiot.com/)。

### 如何集成到项目中
 **Step 1. Add the JitPack repository to your build file**
 
打开根build.gradle文件，将maven { url 'https://jitpack.io' }添加到repositories的末尾

```
allprojects {
	repositories {
	...
	maven { url 'https://jitpack.io' }
	}
}
```
 **Step 2. 添加 implementation 'com.gitee.lochy:dkcloudid-usb-android-module:v1.0.2' 到dependency** 

```

dependencies {
        implementation 'com.gitee.lochy:dkcloudid-usb-android-module:v1.0.2'
}
```

 **Step 3. 在AndroidManifest.xml中添加网络权限和USB访问权限
 
 ```

    <uses-feature android:name="android.hardware.usb.UsbDevice" />
    <uses-feature android:name="android.hardware.usb.UsbManager" />

    <uses-permission android:name="android.permission.INTERNET" />
```
 
 
 **Step 4. 初始化UsbNfcDevice初始化

```

	usbNfcDevice = new UsbNfcDevice(MainActivity.this);
	usbNfcDevice.setCallBack(deviceManagerCallback);
```

 **Step 5. 添加读卡回调和读卡代码

```

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
```
