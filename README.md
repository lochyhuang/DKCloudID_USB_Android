# DKCloudID_USB_Android

#### 介绍
深圳市德科物联技术有限公司的USB身份证阅读器Demo, 支持DK26ME、DK26ME-ANT、DK200ZK等模块。更多产品信息请访问[德科官网](http://www.derkiot.com/)。

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
 **Step 2. 添加 implementation 'com.gitee.lochy:dkcloudid-usb-android-module:v2.0.0' 到dependency** 

```

dependencies {
    implementation 'com.gitee.lochy:dkcloudid-usb-android-module:v2.0.0'
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

    usbNfcDevice = new UsbNfcDevice(MainActivity.this);
    usbNfcDevice.setCallBack(deviceManagerCallback);
```

 **Step 5. 添加读卡回调** 

```

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        //非接寻卡回调
        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            
            final int cardTypeTemp = cardType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //readWriteCardDemo(cardTypeTemp);   //普通IC卡读写API调用示例代码
                }
            }).start();
        }

        //身份证开始请求云解析回调
        @Override
        public void onReceiveSamVIdStart(byte[] initData) {
            super.onReceiveSamVIdStart(initData);

            Log.d(TAG, "开始解析");
        }

        //身份证云解析进度回调
        @Override
        public void onReceiveSamVIdSchedule(int rate) {
            super.onReceiveSamVIdSchedule(rate);
        }

        //身份证云解析异常回调
        @Override
        public void onReceiveSamVIdException(String msg) {
            super.onReceiveSamVIdException(msg);

            //显示错误信息
            //logViewln(msg);
        }

        //身份证云解析明文结果回调
        @Override
        public void onReceiveIDCardData(IDCardData idCardData) {
            super.onReceiveIDCardData(idCardData);

            //显示身份证数据
            showIDMsg(idCardData);
        }
    };
```
