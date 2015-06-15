package com.yamibo.main.yamibolib.locationservice.impl;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import com.baidu.location.BDLocation;
import com.baidu.location.LocationClientOption;
import com.yamibo.main.yamibolib.Utils.Log;
import com.yamibo.main.yamibolib.locationservice.LocationListener;
import com.yamibo.main.yamibolib.locationservice.LocationService;
import com.yamibo.main.yamibolib.locationservice.model.City;
import com.yamibo.main.yamibolib.locationservice.model.Location;
import com.yamibo.main.yamibolib.model.GPSCoordinate;

import java.util.ArrayList;
import java.util.List;

import static com.yamibo.main.yamibolib.locationservice.impl.util.debugLog;

/**
 * 这个类与上层用户交谈，检查环境变量(system settings)，读取更新间隔等参数，管理切换定位服务，定义常量和计算函数。
 * Created by wangxiaoyan on 15/5/25.<br>
 * Clover: 将这个类实例化，对定位服务进行启动刷新,读取定位数据。<br>
 * 注意百度定位服务模式须在在主线程里使用。
 * <p/>
 * 基本使用方法 ：<br>
 * 在activity中，运行<br>
 * DefaultLocationService apiLocationService=new DefaultLocationService(getApplicationContext()),或单有参数的形式<br>
 * apiLocationService.start();<br>
 * apiLocationService.stop();<br>
 * <p/>
 *默认是单次更新请求。刷新监听请调用 refresh();<br>
 * <p/>
 * 可以将任何拥有LocationLisener接口的实例listener添加到队列:<br>
 * addListener(listener), removeListener(listener);
 * <i>封装：帮助类BDLocationService和AndroidLocationService会建立一个相应的API定位监听器。</i>
 * <p/>
 * 以下方法更改已注册的所有监听器的参数，并将作为下一次的监听器参数: <br>
 * resetServiceOption(update_interval,provider);<br>
 *TODO user:若想使用GPS和网络混合provider并选择最优结果，推荐建立两个DefaultLocationService实例，分别监听网络和GPS并比较结果的精度：<br>
 *  <i>注解：百度定位模式下只能设置统一的监听参数。因此对AndroidAPI也作此简化处理。<br>
 *单监听器模式下，AndroidAPI默认的Best模式会变成监听GPS，导致长时间无为之结果。<br>
 *百度的单监听器在混合模式下，会将GPS和网络的结果混在一起返回，缺少文档说明。<br>
 *</i>
 */
public class DefaultLocationService implements LocationService {



    private Context mContext;

    //TODO reset the following to private
    /**
     * 用于实例化 百度 BDLocationClient 或 Android locationManager
     */
    public APILocationService apiLocationService = null;
    /**
     * 监听器队列
     *
     */
    public List<LocationListener> activeListeners = new ArrayList<>();
    /**
     * 当更新时间小于1000ms时，为单次更新
     */
    public int updateInterval =2000;
    /**
     * 默认的serviceMode为百度定位（适用中国）或AndroidAPI定位（适用中国之外）
     */
    public int serviceMode=BAIDU_MODE;
    //public int serviceMode=ANDROID_API_MODE;
    /**
     * 是否允许程序根据定位结果自动选择定位服务
     */
    public boolean isAutoSwitchService =false;

    /**
     * 任意定位服务取得的上次程序定位的结果
     * TODO user:  add methods storing and reading lastKnownLocation
     */
    protected Location lastKnownLocation = null;




    /**
     * 默认选择GPS and/or Network进行定位
     */
    private int providerChoice=PROVIDER_NETWORK;

    //No Use
//    private boolean isStarted=false;
//    private boolean isLocationDemand=false;


    private boolean isLocationReceived=false;



    /**
     * 自动更新启动时的默认更新间隔
     */
    public static final int DEFAULT_UPDATE_INTERVAL=10*60*1000;//default requestLocation time 10min


    public static final int BAIDU_MODE=0;
    public static final int ANDROID_API_MODE=1;
    /**
     * 同时用GPS和Network
     */
    public static final int PROVIDER_BEST=0;
    /**
     *   只用Network
     */
    public static final int PROVIDER_NETWORK=1;
    /**
     * 只用GPS
     */
    public static final int PROVIDER_GPS=2;






    /**
     * to be read by the textView for shown to mobile activity
     */
    public String debugMessage = null;

    //Baidu service
    // client and listener are in the BDLocationApplication's member field
    private BDLocationApplication mBDLocationApplication = null;
    private BDLocation mBDlocationResult = null;


    /**
     * DEBUG_CODE, change the boolean flag to enable/disable Log.i message started with "DEBUG_"
     */
    private static final boolean IS_DEBUG_ENABLED = true;

    private List<LocationListener> mListeners = new ArrayList<>();


    /**
     * Clover:
     * locationClient and Listener instantiated
     * link onReceived callback
     * listener not registered! service not started! use start();
     *
     * @param context
     */
    /* TODO previous
    public DefaultLocationService(Context context) {
        mContext = context;
        mBDLocationApplication = new BDLocationApplication(mContext);
        mBDLocationApplication.targetService = this;
    }*/
    /**
     * Clover:
     * locationClient and Listener instantiated
     * link onReceived callback
     * listener not registered! service not started! use start();
     *
     * Creat manager for BAIDU location by default
     * @param context
     */
    public DefaultLocationService(Context context) {
        mContext = context;
        //TODO user: read stored lastKnownLocation

        if(lastKnownLocation!=null&&isAutoSwitchService){
            if(lastKnownLocation.getRegion()==Location.IN_CN)
                serviceMode=BAIDU_MODE;
            else
                serviceMode=ANDROID_API_MODE;
        }

        switch (serviceMode) {
            case BAIDU_MODE:
                apiLocationService = new BDLocationService(mContext,updateInterval,providerChoice,this);
                debugLog("Baidu location mode selected.");
                break;
            case ANDROID_API_MODE:
                apiLocationService =new AndroidLocationService(mContext,updateInterval,providerChoice,this);
                debugLog("Android API location mode selected");
                break;
            default:
                debugLog("Unknown location mode selected!");
                return;
        }
    }

    /**
     * 获取当前服务状态(交由具体API实例判断）
     * @return STATUS_LOCATED 表示当前定位已经完成。并可以持续获取可用的位置（定位服务可用）<br>
     *     <p/>
     *     STATUS_FAIL  表示当前状态为定位失败<br>
     *         <p/>
     *         STATUS_TRYING 表示当前定位服务在start()或refresh()正在尝试获取最新的位置<br>
     */
    @Override
    public int status() {
        return apiLocationService.status();
    }

    /* TODO remove previous
    @Override
    public int status() {
        int mStatus;
        if (mBDLocationApplication == null)
            return LocationService.STATUS_FAIL;
        if (mBDLocationApplication.isLocationReceived)
            mStatus = LocationService.STATUS_LOCATED;
        else {
            if (mBDLocationApplication.isLocationDemand)
                mStatus = LocationService.STATUS_TRYING;
            else
                mStatus = LocationService.STATUS_FAIL;
        }
        return mStatus;
    }
*/
    /**
     * 当有可用位置时返回true（仅包括最近一次取得的，和之前存储的）<br>
     * 这个位置不一定是最新的，请用requestLocation()来更新，
     * status()来确定当前实例有没有取得新位置。
     * @return
     */
    @Override
    public boolean hasLocation() {
        if(lastKnownLocation!=null)
            return true;
        else
            return false;
    }

    /*TODO remove previous
    @Override
    public boolean hasLocation() {
        if (mBDlocationResult != null)
            return true;
        return false;
    }
    */

    /**
     * 最近一次的位置结果（包括实例读取的存储结果，可能过时）<br>
     * 要确定结果是最新的，请确定status()为真（若需更新结果，可调用refresh()）。
     * @return
     */
    @Override
    public Location location() {
        return lastKnownLocation;
    }


    /**
     *
     * @return GPS模式：芯片坐标<br>
     *     网络查询：Android坐标，或百度纠偏坐标（国外很可能出错！，请自动或手动切换至AndroidAPI模式）
     *     <p>
     *         provider信息未写入，因为lastKnownLocation未存储此信息。
     *     </p>
     */
    @Override
    public GPSCoordinate realCoordinate() {
        return new GPSCoordinate(lastKnownLocation.latitude(),lastKnownLocation.longitude(),
                lastKnownLocation.accuracy(),lastKnownLocation.getTime(),"");
    }


    /**
     *
     * @return 国内GPS模式：芯片坐标经过百度纠偏计算<br>
     *     国内网络查询：百度纠偏或Android坐标经过百度纠偏计算<br>
     *         国外：realCoordinate
     *     <p>
     *         provider信息未写入，因为lastKnownLocation未存储此信息。
     *     </p>
     */
    @Override
    public GPSCoordinate offsetCoordinate() {
        return new GPSCoordinate(lastKnownLocation.offsetLatitude(),lastKnownLocation.offsetLongitude(),
                lastKnownLocation.accuracy(),lastKnownLocation.getTime(),"");
    }

    @Override
    public String address() {
        return lastKnownLocation.address();
    }

    @Override
    public City city() {
        return lastKnownLocation.city();
    }

    /*TODO remove previous
    @Override
    //TODO
    public Location location() {
        return null;
    }

    @Override
    //TODO
    public GPSCoordinate realCoordinate() {
        return null;
    }

    @Override
    //TODO
    public GPSCoordinate offsetCoordinate() {
        return null;
    }

    @Override
    public String address() {
        if (hasLocation())
            return (mBDlocationResult.getAddrStr());
        return null;
    }

    @Override
    //TODO
    public City city() {
        return null;
    }
*/

    /**
     * Clover:
     *
     * register listener, init option, start service, requestLocation
     */
    /* TODO previous @Override
    public boolean start() {
        if (mBDLocationApplication == null) {
            return false;
        }
        if (isLocationEnabled(mContext)) {
            mBDLocationApplication.addListener();
            mBDLocationApplication.initLocation();
            mBDLocationApplication.start();
            mBDLocationApplication.requestLocation();

            debugLog("location service starts");
            debugShow("location service starts");
            return true;
        } else {
            return false;
        }
    }
    */
    @Override
    /**
     *
     * 若具体API尚未开始进行定位工作（未调用过start()或者调用stop()之后），
     * 会创建一个新的监听器并开始定位。
     *
     */
    public boolean start() {
        if(apiLocationService ==null||!isLocationEnabled(mContext))
            return false;
        if(activeListeners.isEmpty()){
            LocationListener listener=new LocationListener() {
                @Override
                public void onLocationChanged(LocationService sender) {
                    debugLog("A listener auto generated while service start() " +
                            "because the activeListeners arrays is empty");
                }
            };
            addListener(listener);
        }
        else {
            debugLog("Use existeing listeners");
        }

        return apiLocationService.start();
    }

    /**
     *
     * 删除所有监听器，停止定位功能<br>
     * 可多次调用
     */
    @Override
    public void stop() {
        apiLocationService.stop();
        activeListeners.clear();
    }

    /**
     * Clover:
     * unregister listener and stop client
     * in Baidu service sample, listener is not removed when client stops?
     */
    /*TODO remove previous
    @Override
    public void stop() {
        if (mBDLocationApplication == null)
            return;

        //reset flags
        mBDLocationApplication.resetFlag();
        mBDLocationApplication.removeListener();
        mBDLocationApplication.stop();

        debugLog("location service stops");
    }
*/

    /**
     * Clover
     * requestLocation (asynchronous)
     * return true if location demand has been sent
     */
    /*    @Override
    public boolean refresh() {
        if (mBDLocationApplication == null)
            return false;
        mBDLocationApplication.requestLocation();
        return mBDLocationApplication.isLocationDemand;
    }
    */
    /**
     *  让所有已知监听器发送异步刷新当前位置的请求。可多次调用<br>
     * 如果当前系统定位开关未打开，会直接返回false<br>
     * 注意：百度的返回值由它的定位服务统一提供<br>
     *     AndroidAPI 至少一个listener获取位置时返回值为true
     */
    @Override
    public boolean refresh() {
        if(!isLocationEnabled(mContext))
            return false;
        resetReceivedFlag();
        debugLog("goto apiLocationService.refresh()");
        return apiLocationService.refresh();
    }
    /**
     * 重置发送/接到 位置信息的flags
     */
    private void resetReceivedFlag() {
        isLocationReceived=false;
    }



    /**
     *
     * @param updateInterval 百度/Android API设置发起自动更新定位请求的间隔时间(ms)<br>
     *                        <1000 不会自动发送新请求。需要手动发送。
     * @param providerChoice PROVIDER_BEST 返回GPS和网络定位中最好的结果
     *              , PROVIDER_NETWORK 只使用网络和基站
     *              ,  PROVIDER_GPS 只用GPS模式<br>
     *让定位服务的所有已知监听器以新的参数连接并运行。
     */
    public void resetServiceOption(int updateInterval, int providerChoice){
        apiLocationService.resetServiceOption(updateInterval, providerChoice);
    }


    /**
     * @param timeMS 设置发起定位请求的间隔时间为>=1000 (ms) 时为循环更新
     *               default value -1 means no automatic update.
     *               to TEST: 热切换
     */
    /*TODO remove previous
    public void newUpdateTime(int timeMS) {
        mBDLocationApplication.setSpan(timeMS);
        mBDLocationApplication.initLocation();
    }
    public void newAddressAppearance(boolean isNeedAddress) {
        mBDLocationApplication.setIsNeedAddress(isNeedAddress);
        mBDLocationApplication.initLocation();
    }


    public void newLocationMode(LocationClientOption.LocationMode input) {
        mBDLocationApplication.setLocationMode(input);
        mBDLocationApplication.initLocation();
    }

    public void newCoordMode(String input) {
        mBDLocationApplication.setCoordMode(input);
    }
    */

    /**
     * NEED to be changed: LocationListener is not a parameter for BD listener servive;
     * not used here
     * maybe overload with no parameter?
     */
        /*@Override
    public void addListener(LocationListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);

        }
    }
*/
    /**
     * @param listener 任何有Location listener interface的监听器<br>
     *     <p/>
     *同一个监听器不会被重复添加<br>
     * <i>封装：在APILocationService里面增加一个相应的位置监听器</i>
     */
    @Override
    public void addListener(LocationListener listener) {
        if(activeListeners.contains(listener)){
            debugLog("listener is already active and known by the service!");
            return;
        }
        debugLog("new LocationListener is added to activeListeners array of size " + activeListeners.size() + "\n");
        activeListeners.add(listener);
        apiLocationService.addListener(listener);
    }

    /**
     * NEED to be changed: LocationListener is not a parameter for BD listener servive;
     * not used here
     * maybe overload with no parameter?
     */
    /*TODO remove previous
    @Override
    public void removeListener(LocationListener listener) {
        mListeners.remove(listener);
    }
    */

    /**
     * 删除监听器
     * @param listener
     * <i>封装：在APILocationService里删除相对应的位置监听器</i>
     */
    @Override
    public void removeListener(LocationListener listener)
    {
        if(listener != null && activeListeners.contains(listener)) {
            apiLocationService.removeListener(listener);
            activeListeners.remove(listener);
        }
        else
            debugLog("listener is null or not contained as activeListeners");
    }

/*todo remove previous
    @Override
        public void selectCoordinate(int type, GPSCoordinate coord) {

    }
*/

    //TODO QUESTION: 这个方法用来做什么的？默认坐标是怎么回事？另外用户传来的GPS里并未指定address和City（使用默认的上海）<br>
    // 目前仅当用户明确指定坐标时，将其当成offsetCoordinate并更新lastKnownResult
    @Override
    public void selectCoordinate(int type, GPSCoordinate coord) {
        switch (type){
            case 0:
            case 1:
            case -1:
                debugLog("selectedLocation not stored");
                return;
            case 0xFF01:
                lastKnownLocation=new Location(coord.latitude(),coord.longitude(),
                        coord.latitude(),coord.longitude(),"selected address",City.DEFAULT);
        }


        return;
    }

    /**
     * 判断系统的定位服务设置是否开启
     * @param context
     * @return
     */
    public static boolean isLocationEnabled(Context context) {
        if (context == null)
            return false;

        int locationMode = 0;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }


    }


    /**
     * 当任何一个监听器收到位置信息时，运行监听器队列中的每一个监听器。<p/>
     * 实现方法注解：AndroidAPI情形：当一个监听器设置为处于单次更新模式时，用unregister来停止它的更新功能，仍将其保留在监听器列表中。
     * 下次refresh时它会被重新注册并尝试获取位置信息。
     *
     */
    void onReceiveLocation(Location LocationResult) {
        debugLog("client started? " + apiLocationService.isClientStarted());
        this.lastKnownLocation = LocationResult;
        debugLog("hasLocation"+hasLocation());
        isLocationReceived=true;
        debugLog("LocationService updated location from one listener");

        for (LocationListener listener : activeListeners) {
            listener.onLocationChanged(this);
        }
        debugLog("all activeListeners perform their own actions");

        if(isAutoSwitchService) {
            debugLog("switch service!");
            if (lastKnownLocation.getRegion() == Location.IN_CN && serviceMode != BAIDU_MODE)
                switchServiceMode(BAIDU_MODE);
            if (lastKnownLocation.getRegion() == Location.NOT_IN_CN && serviceMode==BAIDU_MODE)
                switchServiceMode(ANDROID_API_MODE);
        }
        debugLog("client started? " + apiLocationService.isClientStarted());
    }

    /**
     *
     * @param newServiceMode
     * 在百度/AndroidAPI服务间切换。切换后所有flags和已运行的监视器将消失。保留最后一次获取的位置。
     */
    void switchServiceMode(int newServiceMode) {
        if(newServiceMode==serviceMode){
            debugLog("Same location service, no need to switch");
            return;
        }
        debugLog("restart service with the new service mode");
        serviceMode=newServiceMode;
        restart();
    }

    private void restart() {
        stop();
        resetReceivedFlag();
        start();
    }

    /**
     * this message can be shown in the debut_text field on mobile by click the debug_button
     *
     * @param Message
     */
    /*todo remove previous
    private void debugShow(String Message) {
        if (IS_DEBUG_ENABLED)
            debugMessage = Message;
    }

    private void debugLog(String Message) {
        if (IS_DEBUG_ENABLED)
            Log.i("DefaultLocationSerivce", "DEBUG_" + Message);
    }
*/
    /**
     * to be called by BDLocationListener's onReceive
     * when received, update mBDlocationResult
     */
    public void onReceiveBDLocation(BDLocation locationResult) {
        if (mBDLocationApplication == null)
            return;
        this.mBDlocationResult = locationResult;
        // TODO
        // 初始化这里LocationServier所有的变量,包括locaion,city,等等等等
        for (LocationListener listener : mListeners) {
            listener.onLocationChanged(this);
        }

        debugLog("LocationService receive location from BDLocation");
//        debugShow(BDLocationApplication.toStringOutput(mBDlocationResult));
    }

}
