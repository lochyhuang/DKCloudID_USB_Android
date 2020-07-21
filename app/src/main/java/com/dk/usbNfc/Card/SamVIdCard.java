package com.dk.usbNfc.Card;

import com.dk.usbNfc.DeviceManager.DeviceManager;
import com.dk.usbNfc.Exception.CardNoResponseException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SamVIdCard  extends Card{
    DeviceManager mDeviceManager;

    public SamVIdCard(DeviceManager deviceManager) {
        super(deviceManager);
        mDeviceManager = deviceManager;
    }

    /**
     * 获取设备保存的序列号，设备默认的序列号是FFFFFFFFFFFFFFFF，同步阻塞方式，注意：不能在主线程里运行
     * @return         返回的序列号，8字节
     * @throws CardNoResponseException
     *                  操作无响应时会抛出异常
     */
    public byte[] getSamVInitData() throws CardNoResponseException {
        synchronized(this) {
            final byte[][] returnBytes = new byte[1][1];
            final boolean[] isCmdRunSucFlag = {false};

            final Semaphore semaphore = new Semaphore(0);
            returnBytes[0] = null;

            mDeviceManager.requestSamVInitData(new DeviceManager.onReceiveGetSamVInitDataListener() {
                @Override
                public void onReceiveGetSamVInitData(boolean isCmdRunSuc, byte[] initData) {
                    if (isCmdRunSuc) {
                        returnBytes[0] = initData;
                        isCmdRunSucFlag[0] = true;
                    } else {
                        returnBytes[0] = null;
                        isCmdRunSucFlag[0] = false;
                    }
                    semaphore.release();
                }
            });

            try {
                semaphore.tryAcquire(CAR_NO_RESPONSE_TIME_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new CardNoResponseException("设备无响应");
            }

            if (!isCmdRunSucFlag[0]) {
                throw new CardNoResponseException("获取解析初始化数据失败");
            }
            return returnBytes[0];
        }
    }

//    /**
//     * 获取设备保存的序列号，设备默认的序列号是FFFFFFFFFFFFFFFF，同步阻塞方式，注意：不能在主线程里运行
//     * @return         返回的序列号，8字节
//     * @throws CardNoResponseException
//     *                  操作无响应时会抛出异常
//     */
//    public byte[] getSamVAesKeyData() throws CardNoResponseException {
//        synchronized(this) {
//            final byte[][] returnBytes = new byte[1][1];
//            final boolean[] isCmdRunSucFlag = {false};
//
//            final Semaphore semaphore = new Semaphore(0);
//            returnBytes[0] = null;
//
//            mDeviceManager.requestSamVGetAESKey(new DeviceManager.onReceiveGetSamVAESKeyListener() {
//                @Override
//                public void onReceiveGetSamVAESKey(boolean isCmdRunSuc, byte[] initData) {
//                    if (isCmdRunSuc) {
//                        returnBytes[0] = initData;
//                        isCmdRunSucFlag[0] = true;
//                    } else {
//                        returnBytes[0] = null;
//                        isCmdRunSucFlag[0] = false;
//                    }
//                    semaphore.release();
//                }
//            });
//
//            try {
//                semaphore.tryAcquire(CAR_NO_RESPONSE_TIME_MS, TimeUnit.MILLISECONDS);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//                throw new CardNoResponseException("设备无响应");
//            }
//
//            if (!isCmdRunSucFlag[0]) {
//                throw new CardNoResponseException("获取解析AES KEY失败");
//            }
//            return returnBytes[0];
//        }
//    }

    /**
     * cpu卡指令传输，同步阻塞方式，注意：不能在蓝牙初始化的线程里运行
     * @param data     发送的数据
     * @return         返回的数据
     * @throws CardNoResponseException
     *                  操作无响应时会抛出异常
     */
    public byte[] transceive(byte[] data) throws CardNoResponseException {
        synchronized(this) {
            if (data == null || data.length == 0) {
                throw new CardNoResponseException("数据不能为null");
            }

            final byte[][] returnBytes = new byte[1][1];
            final boolean[] isCmdRunSucFlag = {false};

            final Semaphore semaphore = new Semaphore(0);
            returnBytes[0] = null;

            mDeviceManager.requestSamVDataExchange(data, new DeviceManager.onReceiveGetSamVApduListener() {
                @Override
                public void onReceiveGetSamVApdu(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                    if (isCmdRunSuc) {
                        returnBytes[0] = bytApduRtnData;
                        isCmdRunSucFlag[0] = true;
                    } else {
                        returnBytes[0] = null;
                        isCmdRunSucFlag[0] = false;
                    }
                    semaphore.release();
                }
            });

            try {
                semaphore.tryAcquire(CAR_NO_RESPONSE_TIME_MS * 5, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new CardNoResponseException(CAR_NO_RESPONSE);
            }
            if (!isCmdRunSucFlag[0]) {
                throw new CardNoResponseException(CAR_RUN_CMD_FAIL);
            }
            return returnBytes[0];
        }
    }
}
