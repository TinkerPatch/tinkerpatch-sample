/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Shengjie Sim Sun
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package tinker.sample.android.app;


import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.multidex.MultiDex;
import android.util.Log;

import com.tencent.tinker.anno.DefaultLifeCycle;
import com.tencent.tinker.lib.listener.DefaultPatchListener;
import com.tencent.tinker.lib.patch.UpgradePatch;
import com.tencent.tinker.lib.reporter.DefaultLoadReporter;
import com.tencent.tinker.lib.reporter.DefaultPatchReporter;
import com.tencent.tinker.lib.service.PatchResult;
import com.tencent.tinker.loader.app.ApplicationLifeCycle;
import com.tencent.tinker.loader.app.DefaultApplicationLike;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tinkerpatch.sdk.TinkerPatch;
import com.tinkerpatch.sdk.server.callback.ConfigRequestCallback;
import com.tinkerpatch.sdk.server.callback.RollbackCallBack;
import com.tinkerpatch.sdk.server.callback.TinkerPatchRequestCallback;
import com.tinkerpatch.sdk.tinker.callback.ResultCallBack;
import com.tinkerpatch.sdk.tinker.service.TinkerServerResultService;

import java.util.HashMap;

import tinker.sample.android.BuildConfig;

/**
 * because you can not use any other class in your application, we need to
 * move your implement of Application to {@link ApplicationLifeCycle}
 * As Application, all its direct reference class should be in the main dex.
 *
 * We use tinker-android-anno to make sure all your classes can be patched.
 *
 * application: if it is start with '.', we will add SampleApplicationLifeCycle's package name
 *
 * flags:
 * TINKER_ENABLE_ALL: support dex, lib and resource
 * TINKER_DEX_MASK: just support dex
 * TINKER_NATIVE_LIBRARY_MASK: just support lib
 * TINKER_RESOURCE_MASK: just support resource
 *
 * loaderClass: define the tinker loader class, we can just use the default TinkerLoader
 *
 * loadVerifyFlag: whether check files' md5 on the load time, defualt it is false.
 *
 * Created by zhangshaowen on 16/3/17.
 */
@SuppressWarnings("unused")
@DefaultLifeCycle(application = "tinker.sample.android.app.SampleApplication",
                  flags = ShareConstants.TINKER_ENABLE_ALL,
                  loadVerifyFlag = false)
public class SampleApplicationLike extends DefaultApplicationLike {
    private static final String TAG = "Tinker.SampleAppLike";

    public SampleApplicationLike(Application application,
                                 int tinkerFlags,
                                 boolean tinkerLoadVerifyFlag,
                                 long applicationStartElapsedTime,
                                 long applicationStartMillisTime,
                                 Intent tinkerResultIntent
    ) {
        super(
            application,
            tinkerFlags,
            tinkerLoadVerifyFlag,
            applicationStartElapsedTime,
            applicationStartMillisTime,
            tinkerResultIntent
        );
    }

    /**
     * install multiDex before install tinker
     * so we don't need to put the tinker lib classes in the main dex
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onBaseContextAttached(Context base) {
        super.onBaseContextAttached(base);
        //you must install multiDex whatever tinker is installed!
        MultiDex.install(base);
    }

    /**
     * 我们需要确保至少对主进程跟patch进程初始化 TinkerPatch
     */
    @Override
    public void onCreate() {
        super.onCreate();
        initTinker();
    }

    private void initTinker() {
        if (BuildConfig.TINKER_ENABLE) {
            //开始检查是否有补丁，这里配置的是每隔访问3小时服务器是否有更新。
            TinkerPatch.init(this)
                .reflectPatchLibrary()
                .setPatchRollbackOnScreenOff(true)
                .setPatchRestartOnSrceenOff(true)
                .setFetchPatchIntervalByHours(3);

            // 获取当前的补丁版本
            Log.d(TAG, "current patch version is " + TinkerPatch.with().getPatchVersion());

            //每隔3个小时去访问后台时候有更新,通过handler实现轮训的效果
            TinkerPatch.with().fetchPatchUpdateAndPollWithInterval();
        }
    }

    /**
     * 在这里给出TinkerPatch的所有接口解释
     * 更详细的解释请参考:http://tinkerpatch.com/Docs/api
     */
    private void useSample() {
        TinkerPatch.init(this)
            //是否自动反射Library路径,无须手动加载补丁中的So文件
            //注意,调用在反射接口之后才能生效,你也可以使用Tinker的方式加载Library
            .reflectPatchLibrary()
            //向后台获取是否有补丁包更新,默认的访问间隔为3个小时
            //若参数为true,即每次调用都会真正的访问后台配置
            .fetchPatchUpdate(false)
            //设置访问后台补丁包更新配置的时间间隔,默认为3个小时
            .setFetchPatchIntervalByHours(3)
            //向后台获得动态配置,默认的访问间隔为3个小时
            //若参数为true,即每次调用都会真正的访问后台配置
            .fetchDynamicConfig(new ConfigRequestCallback() {
                @Override
                public void onSuccess(HashMap<String, String> hashMap) {

                }

                @Override
                public void onFail(Exception e) {

                }
            }, false)
            //设置访问后台动态配置的时间间隔,默认为3个小时
            .setFetchDynamicConfigIntervalByHours(3)
            //设置当前渠道号,对于某些渠道我们可能会想屏蔽补丁功能
            //设置渠道后,我们就可以使用后台的条件控制渠道更新
            .setAppChannel("default")
            //屏蔽部分渠道的补丁功能
            .addIgnoreAppChannel("googleplay")
            //设置tinkerpatch平台的条件下发参数
            .setPatchCondition("test", "1")
            //设置补丁合成成功后,锁屏重启程序
            //默认是等应用自然重启
            .setPatchRestartOnSrceenOff(true)
            //我们可以通过ResultCallBack设置对合成后的回调
            //例如弹框什么
            .setPatchResultCallback(new ResultCallBack() {
                @Override
                public void onPatchResult(PatchResult patchResult) {
                    Log.i(TAG, "onPatchResult callback here");
                }
            })
            //设置收到后台回退要求时,锁屏清除补丁
            //默认是等主进程重启时自动清除
            .setPatchRollbackOnScreenOff(true)
            //我们可以通过RollbackCallBack设置对回退时的回调
            .setPatchRollBackCallback(new RollbackCallBack() {
                @Override
                public void onPatchRollback() {
                    Log.i(TAG, "onPatchRollback callback here");
                }
            });
    }

    /**
     * 自定义Tinker类的高级用法,一般不推荐使用
     * 更详细的解释请参考:http://tinkerpatch.com/Docs/api
     */
    private void complexSample() {
        TinkerPatch.Builder builder = new TinkerPatch.Builder(this);
        //修改tinker的构造函数,自定义类
        builder.listener(new DefaultPatchListener(getApplication()))
                .loadReporter(new DefaultLoadReporter(getApplication()))
                .patchReporter(new DefaultPatchReporter(getApplication()))
                .resultServiceClass(TinkerServerResultService.class)
                .upgradePatch(new UpgradePatch())
                .patchRequestCallback(new TinkerPatchRequestCallback());

        TinkerPatch.init(builder.build());
    }
}
