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
 **Step 2. 添加 implementation 'com.gitee.lochy:dkcloudid-usb-android-module:v2.0.3' 到dependency** 

```

dependencies {
        implementation 'com.gitee.lochy:dkcloudid-usb-android-module:v2.0.3'
}
```

 **Step 3. 在AndroidManifest.xml中添加网络权限和USB访问权限** 
 
 ```

    <uses-feature android:name="android.hardware.usb.UsbDevice" />
    <uses-feature android:name="android.hardware.usb.UsbManager" />

    <uses-permission android:name="android.permission.INTERNET" />
```
 
 
 **Step 4. 初始化UsbNfcDevice初始化** 

```

	//usb_nfc设备初始化
	if (usbNfcDevice == null) {
		usbNfcDevice = new UsbNfcDevice(MainActivity.this);
		usbNfcDevice.setCallBack(deviceManagerCallback);
	}
```

 **Step 5. 添加读卡回调和读卡代码** 

```

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
				    //普通IC卡读写示例
                    //readWriteCardDemo(cardTypeTemp);
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
                //myTTS.speak("读取成功");
            }
        }

        //身份证云解析异常回调
        @Override
        public void onReceiveSamVIdException(String msg) {
            super.onReceiveSamVIdException(msg);

            //显示错误信息
            logViewln(msg);
        }

        //身份证云解析明文结果回调
        @Override
        public void onReceiveIDCardData(IDCardData idCardData) {
            super.onReceiveIDCardData(idCardData);

            //显示身份证数据
            showIDCardData(idCardData);
        }
    };
```
