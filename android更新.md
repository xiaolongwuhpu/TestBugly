## android更新
## 导航
 -  ### 热更新
>- [x]  热修复简介
>- [x] 技术原理及特点
>- [x]  热修复框架分类
>- [x]  热修复框架对比
>- [x] 热更新其他的方案
>- [x] Tinker框架解析
>
  - ### 增量更新


#### 热修复简介
热修复:热修复（也称热补丁、热修复补丁，英语：hotfix）是一种包含信息的独立的累积更新包，通常表现为一个或多个文件。这被用来解决软件产品的问题（例如一个程序错误）。 —— 维基百科

#### 技术原理及特点
- ##### 类加载方案

![Classload](https://upload-images.jianshu.io/upload_images/1437930-bb9d359f4c7e9935.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1000/format/webp)
![](../image/classload.jpg)

  类加载方案基于Dex分包方案，什么是Dex分包方案呢？这个得先从65536限制和LinearAlloc限制说起。
**65536限制**
随着应用功能越来越复杂，代码量不断地增大，引入的库也越来越多，可能会在编译时提示如下异常：

```
com.android.dex.DexIndexOverflowException: method ID not in [0, 0xffff]: 65536 
```

这说明应用中引用的方法数超过了最大数65536个。产生这一问题的原因就是系统的65536限制，65536限制的主要原因是DVM Bytecode的限制，DVM指令集的方法调用指令invoke-kind索引为16bits，最多能引用 65535个方法。
**LinearAlloc限制**
在安装时可能会提示INSTALL_FAILED_DEXOPT。产生的原因就是LinearAlloc限制，DVM中的LinearAlloc是一个固定的缓存区，当方法数过多超出了缓存区的大小时会报错。

为了解决65536限制和LinearAlloc限制，从而产生了Dex分包方案。Dex分包方案主要做的是在打包时将应用代码分成多个Dex，将应用启动时必须用到的类和这些类的直接引用类放到主Dex中，其他代码放到次Dex中。当应用启动时先加载主Dex，等到应用启动后再动态的加载次Dex，从而缓解了主Dex的65536限制和LinearAlloc限制。

Dex分包方案主要有两种，分别是Google官方方案、Dex自动拆包和动态加载方案。因为Dex分包方案不是本章的重点，这里就不再过多的介绍，我们接着来学习类加载方案。
ClassLoader的加载过程，其中一个环节就是调用DexPathList的findClass的方法，如下所示。
libcore/dalvik/src/main/java/dalvik/system/DexPathList.java
```
 public Class<?> findClass(String name, List<Throwable> suppressed) {
        for (Element element : dexElements) {//1
            Class<?> clazz = element.findClass(name, definingContext, suppressed);//2
            if (clazz != null) {
                return clazz;
            }
        }
        if (dexElementsSuppressedExceptions != null) {
            suppressed.addAll(Arrays.asList(dexElementsSuppressedExceptions));
        }
        return null;
    }
```
Element内部封装了DexFile，DexFile用于加载dex文件，因此每个dex文件对应一个Element。
多个Element组成了有序的Element数组dexElements。当要查找类时，会在注释1处遍历Element数组dexElements（相当于遍历dex文件数组），注释2处调用Element的findClass方法，其方法内部会调用DexFile的loadClassBinaryName方法查找类。如果在Element中（dex文件）找到了该类就返回，如果没有找到就接着在下一个Element中进行查找。
根据上面的查找流程，我们将有bug的类Key.class进行修改，再将Key.class打包成包含dex的补丁包Patch.jar，放在Element数组dexElements的第一个元素，这样会首先找到Patch.dex中的Key.class去替换之前存在bug的Key.class，排在数组后面的dex文件中的存在bug的Key.class根据ClassLoader的双亲委托模式就不会被加载


类加载方案需要重启App后让ClassLoader重新加载新的类，为什么需要重启呢？这是因为类是无法被卸载的，因此要想重新加载新的类就需要重启App，因此采用类加载方案的热修复框架是不能即时生效的。
虽然很多热修复框架采用了类加载方案，但具体的实现细节和步骤还是有一些区别的，比如QQ空间的超级补丁和Nuwa是按照上面说得将补丁包放在Element数组的第一个元素得到优先加载。微信Tinker将新旧apk做了diff，得到patch.dex，然后将patch.dex与手机中apk的classes.dex做合并，生成新的classes.dex，然后在运行时通过反射将classes.dex放在Element数组的第一个元素。饿了么的Amigo则是将补丁包中每个dex 对应的Element取出来，之后组成新的Element数组，在运行时通过反射用新的Element数组替换掉现有的Element 数组。

采用类加载方案的主要是以腾讯系为主，包括微信的Tinker、QQ空间的超级补丁、手机QQ的QFix、饿了么的Amigo和Nuwa等等。


--------------------- 


#### 热修复框架分类

| 类别  | 成员 | 
|-------|:---:|
| 阿里系  | AndFix、Dexposed、阿里百川、Sophix | 
| 腾讯系 | 微信的Tinker、QQ空间的超级补丁、手机QQ的QFix  | 
| 其他  | 美团的Robust、饿了么的Amigo、美丽说蘑菇街的Aceso等等   | 
--------------------- 


#### 热修复框架对比
| 特性  | AndFix | Tinker    | QQ空间  |Robust/Aceso|
|-------|:---:|:------:|:------:| :-------: |
| 即时生效  |  &#10003; |   &#10005;  |  &#10005;    |   &#10003;  |
| 方法替换  | &#10003; | &#10003;     | &#10003; |  &#10003;  |
| 类替换 |  &#10005; | &#10003;      | &#10003;   |   &#10005; |
| 类结构修改  |    &#10005; |   &#10003; |    &#10005;  |   &#10005;  |
| 资源替换  |    &#10005; |  &#10003;  |  &#10003;   |   &#10005; |
| so替换  |   &#10005;  |  &#10003;  |    &#10005;  |    &#10005; |
| 支持gralde  |   &#10005;  |  &#10003;  |    &#10005;  |    &#10005; |

--------------------- 
- #### Tinker框架解析
![tinker](https://github.com/Tencent/tinker/blob/master/assets/tinker.png)   

https://github.com/xiaolongwuhpu/TestBugly

#####  tinkerPatch的过程
tinker的加载补丁的方法：
```
TinkerInstaller.onReceiveUpgradePatch(getApplicationContext(), patchFile.getAbsolutePath());

```

就开始从onReceiveUpgradePatch进行代码的追踪。
点进去这个方法：
```
/**
 * new patch file to install, try install them with :patch process
 *
 * @param context
 * @param patchLocation
 */
public static void onReceiveUpgradePatch(Context context, String patchLocation) {
    Tinker.with(context).getPatchListener().onPatchReceived(patchLocation);
}

```

可以看到这里获取了tinker对象的实例，通过app自身的application的上下文对象获取到，而后通过patch的listener的方法进行加载，这里listener只是一个接口，分析这里需要回到第一部分installTinker的时候添加的：
```
//or you can just use DefaultLoadReporter
LoadReporter loadReporter = new SampleLoadReporter(appLike.getApplication());
//or you can just use DefaultPatchReporter
PatchReporter patchReporter = new SamplePatchReporter(appLike.getApplication());
//or you can just use DefaultPatchListener
PatchListener patchListener = new SamplePatchListener(appLike.getApplication());
```

这里主要看SamplePatchListener，追踪到DefaultPatchListener的onPatchReceived方法，这里就是开始进行patch操作的地方。
首先通过patchCheck方法进行了一系列的校验工作，然后通过TinkerPatchService.runPatchService(context, path);运行了起了一个patchService，继续查看TinkerPatchService，可以看到以下两个核心的启动方法：
```
private static void runPatchServiceByIntentService(Context context, String path) {
    TinkerLog.i(TAG, "run patch service by intent service.");
    Intent intent = new Intent(context, IntentServiceRunner.class);
    intent.putExtra(PATCH_PATH_EXTRA, path);
    intent.putExtra(RESULT_CLASS_EXTRA, resultServiceClass.getName());
    context.startService(intent);
}

@TargetApi(21)
private static boolean runPatchServiceByJobScheduler(Context context, String path) {
    TinkerLog.i(TAG, "run patch service by job scheduler.");
    final JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(
            1, new ComponentName(context, JobServiceRunner.class)
    );
    final PersistableBundle extras = new PersistableBundle();
    extras.putString(PATCH_PATH_EXTRA, path);
    extras.putString(RESULT_CLASS_EXTRA, resultServiceClass.getName());
    jobInfoBuilder.setExtras(extras);
    jobInfoBuilder.setOverrideDeadline(5);
    final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (jobScheduler == null) {
        TinkerLog.e(TAG, "jobScheduler is null.");
        return false;
    }
    return (jobScheduler.schedule(jobInfoBuilder.build()) == JobScheduler.RESULT_SUCCESS);
}

```
分别是android 21版本以上和以下两个方法，这是因为在android 5.0之后，为了对系统的耗电量和内存管理进行优化，Google官方要求对后台消耗资源的操作推荐放到JobScheduler中去执行。
IntentServiceRunner是改Services的核心，这是一个异步的service，查看他的onHandleIntent，可以看到，所有的操作都在doApplyPatch(getApplicationContext(), intent);当中：
```
@Override
protected void onHandleIntent(@Nullable Intent intent) {
    increasingPriority();
    doApplyPatch(getApplicationContext(), intent);
}

```

方法中核心是：

```
result = upgradePatchProcessor.tryPatch(context, path, patchResult);
```


而这个upgradePatchProcessor也是在初始化的时候就传入的。

```
//you can set your own upgrade patch if you need
AbstractPatch upgradePatchProcessor = new UpgradePatch();
```


我们查看UpgradePatch的tryPatch进行查看，方法很长，主要核心的部分是：

```
//we use destPatchFile instead of patchFile, because patchFile may be deleted during the patch process
if (!DexDiffPatchInternal.tryRecoverDexFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch dex failed");
    return false;
}

if (!BsDiffPatchInternal.tryRecoverLibraryFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch library failed");
    return false;
}

if (!ResDiffPatchInternal.tryRecoverResourceFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
    TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch resource failed");
    return false;
}
```


这里分别对应的是dex的patch，so文件的patch，资源文件的patch。
首先来看DexDiffPatchInternal.tryRecoverDexFiles，追踪代码可以看得出，经过一系列的操作校验之后，patchDexFile是执行patch操作的关键方法，而所有的处理交给了new DexPatchApplier(oldDexStream, patchFileStream).executeAndSaveTo(patchedDexFile)，继续追踪下去，可以看到在生成补丁文件时候的熟悉代码，那就是那一系列dex的比对操作：

```
// Secondly, run patch algorithms according to sections' dependencies.
this.stringDataSectionPatchAlg = new StringDataSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.typeIdSectionPatchAlg = new TypeIdSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.protoIdSectionPatchAlg = new ProtoIdSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.fieldIdSectionPatchAlg = new FieldIdSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.methodIdSectionPatchAlg = new MethodIdSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.classDefSectionPatchAlg = new ClassDefSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.typeListSectionPatchAlg = new TypeListSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.annotationSetRefListSectionPatchAlg = new AnnotationSetRefListSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.annotationSetSectionPatchAlg = new AnnotationSetSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.classDataSectionPatchAlg = new ClassDataSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.codeSectionPatchAlg = new CodeSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.debugInfoSectionPatchAlg = new DebugInfoItemSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.annotationSectionPatchAlg = new AnnotationSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.encodedArraySectionPatchAlg = new StaticValueSectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);
this.annotationsDirectorySectionPatchAlg = new AnnotationsDirectorySectionPatchAlgorithm(
        patchFile, oldDex, patchedDex, oldToPatchedIndexMap
);

this.stringDataSectionPatchAlg.execute();
this.typeIdSectionPatchAlg.execute();
this.typeListSectionPatchAlg.execute();
this.protoIdSectionPatchAlg.execute();
this.fieldIdSectionPatchAlg.execute();
this.methodIdSectionPatchAlg.execute();
this.annotationSectionPatchAlg.execute();
this.annotationSetSectionPatchAlg.execute();
this.annotationSetRefListSectionPatchAlg.execute();
this.annotationsDirectorySectionPatchAlg.execute();
this.debugInfoSectionPatchAlg.execute();
this.codeSectionPatchAlg.execute();
this.classDataSectionPatchAlg.execute();
this.encodedArraySectionPatchAlg.execute();
this.classDefSectionPatchAlg.execute();
```

截取其中一些片段，如下图：


![patch](https://upload-images.jianshu.io/upload_images/1801847-1c077c1bd97f6557.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/243/format/webp)

这些比对的操作其实就是在对dex中的每个table进行的。
执行完毕之后，生成经过了patch操作之后的dex文件，保存到本地目录，等待loader的时候使用。
loader的后面说，继续看其他两个patch操作。
res的patch操作是ResDiffPatchInternal.tryRecoverResourceFiles，追踪一下代码，可以看到，首先第一步就是先读取了之前生成的meta文件：

```
String resourceMeta = checker.getMetaContentMap().get(RES_META_FILE);
```


最后由extractResourceDiffInternals方法来进行补丁的合成，其实原理就是通过读取meta里面记录的每个资源对应的操作，来执行相关的增加、删除、修改的操作，最后将资源文件打包为apk文件，android自带的loader加载器其实也是支持.apk文件动态加载的。
so文件的操作就更简单了，就是通过bsPatch进行一次patch操作，然后剩下的重载之类的方法前面也讲到了，和applicationLike的原理差不多，就是通过增加一层代理操作的方式，来达到托管的效果。
patch完毕之后，tinker会在本地生成好可读取的补丁文件，便于再次启动的时候，进行加载。
#####  tinker loader的过程

application 初始化的时候，就已经传入了com.tencent.tinker.loader.TinkerLoader，而tinkerloader也是通过TinkerApplication中反射进行加载的：

```
private void loadTinker() {
    try {
        //reflect tinker loader, because loaderClass may be define by user!
        Class<?> tinkerLoadClass = Class.forName(loaderClassName, false, getClassLoader());
        Method loadMethod = tinkerLoadClass.getMethod(TINKER_LOADER_METHOD, TinkerApplication.class);
        Constructor<?> constructor = tinkerLoadClass.getConstructor();
        tinkerResultIntent = (Intent) loadMethod.invoke(constructor.newInstance(), this);
    } catch (Throwable e) {
        //has exception, put exception error code
        tinkerResultIntent = new Intent();
        ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_PATCH_UNKNOWN_EXCEPTION);
        tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, e);
    }
}
```


tinkerLoader的核心方法就是下边三个loader：

```
TinkerDexLoader
TinkerSoLoader
TinkerResourceLoader
```


其余的一些方法中的操作大部分都是校验参数合法性，文件完整性的一些操作，可以略过。
so的加载方式和原理前面已经说了，不在细说了，着重说一下dex和res的加载。
TinkerDexLoader.loadTinkerJars是加载dex的核心方法，点进去又能看到一大部分校验的判断的，核心的加载内容是SystemClassLoaderAdder.installDexes(application, classLoader, optimizeDir, legalFiles);这段代码，查看installDexes可以看到安装的操作：

```
@SuppressLint("NewApi")
public static void installDexes(Application application, PathClassLoader loader, File dexOptDir, List<File> files)
    throws Throwable {
    Log.i(TAG, "installDexes dexOptDir: " + dexOptDir.getAbsolutePath() + ", dex size:" + files.size());

    if (!files.isEmpty()) {
        files = createSortedAdditionalPathEntries(files);
        ClassLoader classLoader = loader;
        if (Build.VERSION.SDK_INT >= 24 && !checkIsProtectedApp(files)) {
            classLoader = AndroidNClassLoader.inject(loader, application);
        }
        //because in dalvik, if inner class is not the same classloader with it wrapper class.
        //it won't fail at dex2opt
        if (Build.VERSION.SDK_INT >= 23) {
            V23.install(classLoader, files, dexOptDir);
        } else if (Build.VERSION.SDK_INT >= 19) {
            V19.install(classLoader, files, dexOptDir);
        } else if (Build.VERSION.SDK_INT >= 14) {
            V14.install(classLoader, files, dexOptDir);
        } else {
            V4.install(classLoader, files, dexOptDir);
        }
        //install done
        sPatchDexCount = files.size();
        Log.i(TAG, "after loaded classloader: " + classLoader + ", dex size:" + sPatchDexCount);

        if (!checkDexInstall(classLoader)) {
            //reset patch dex
            SystemClassLoaderAdder.uninstallPatchDex(classLoader);
            throw new TinkerRuntimeException(ShareConstants.CHECK_DEX_INSTALL_FAIL);
        }
    }
}
```


这里之所以要区分版本，tinker官方也描述了一些android版本上的坑.


通过这个方法可以看出，tinker也是通过反射，获取到系统ClassLoader的dexElements数组，并把需要修改的dex文件插入到了数组当中的最前端。
这里就是tinker整个代码热更新的原理，就是把合并过后的dex文件，插入到Elements数组的前端，因为android的类加载器在加载dex的时候，会按照数组的顺序查找，如果在下标靠前的位置查找到了，就不继续向下寻找了，所以也就起到了热更新的作用。
继续看Res的加载。
TinkerResourceLoader.loadTinkerResources。
方法中的核心部分是：TinkerResourcePatcher.monkeyPatchExistingResources(application, resourceString);

```
/**
 * @param context
 * @param externalResourceFile
 * @throws Throwable
 */
public static void monkeyPatchExistingResources(Context context, String externalResourceFile) throws Throwable {
    if (externalResourceFile == null) {
        return;
    }

    final ApplicationInfo appInfo = context.getApplicationInfo();

    final Field[] packagesFields;
    if (Build.VERSION.SDK_INT < 27) {
        packagesFields = new Field[]{packagesFiled, resourcePackagesFiled};
    } else {
        packagesFields = new Field[]{packagesFiled};
    }
    for (Field field : packagesFields) {
        final Object value = field.get(currentActivityThread);

        for (Map.Entry<String, WeakReference<?>> entry
                : ((Map<String, WeakReference<?>>) value).entrySet()) {
            final Object loadedApk = entry.getValue().get();
            if (loadedApk == null) {
                continue;
            }
            final String resDirPath = (String) resDir.get(loadedApk);
            if (appInfo.sourceDir.equals(resDirPath)) {
                resDir.set(loadedApk, externalResourceFile);
            }
        }
    }

    // Create a new AssetManager instance and point it to the resources installed under
    if (((Integer) addAssetPathMethod.invoke(newAssetManager, externalResourceFile)) == 0) {
        throw new IllegalStateException("Could not create new AssetManager");
    }

    // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
    // in L, so we do it unconditionally.
    if (stringBlocksField != null && ensureStringBlocksMethod != null) {
        stringBlocksField.set(newAssetManager, null);
        ensureStringBlocksMethod.invoke(newAssetManager);
    }

    for (WeakReference<Resources> wr : references) {
        final Resources resources = wr.get();
        if (resources == null) {
            continue;
        }
        // Set the AssetManager of the Resources instance to our brand new one
        try {
            //pre-N
            assetsFiled.set(resources, newAssetManager);
        } catch (Throwable ignore) {
            // N
            final Object resourceImpl = resourcesImplFiled.get(resources);
            // for Huawei HwResourcesImpl
            final Field implAssets = findField(resourceImpl, "mAssets");
            implAssets.set(resourceImpl, newAssetManager);
        }

        clearPreloadTypedArrayIssue(resources);

        resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
    }

    // Handle issues caused by WebView on Android N.
    // Issue: On Android N, if an activity contains a webview, when screen rotates
    // our resource patch may lost effects.
    // for 5.x/6.x, we found Couldn't expand RemoteView for StatusBarNotification Exception
    if (Build.VERSION.SDK_INT >= 24) {
        try {
            if (publicSourceDirField != null) {
                publicSourceDirField.set(context.getApplicationInfo(), externalResourceFile);
            }
        } catch (Throwable ignore) {
        }
    }

    if (!checkResUpdate(context)) {
        throw new TinkerRuntimeException(ShareConstants.CHECK_RES_INSTALL_FAIL);
    }
}
```

通过反射的方法，替换掉系统的AssetManager，也就是mAssets这个变量，而新的NewAssetManager指向的resource是新的资源路径，这样在系统调用mAssets进行加载资源的时候，使用的就是热更新后的资源了。





#### 增量更新

- ##### 准备
  * **工具**   
  
  apk文件的差分、合成，可以通过 开源的二进制比较工具 bsdiff 来实现，又因为bsdiff依赖bzip2，所以我们还需要用到 bzip2
  bsdiff中，bsdiff.c 用于生成差分包，bspatch.c 用于合成文件。   
[bsdiff官网下载地址](http://www.daemonology.net/bsdiff/)   
    [bzip2-1.0.6下载地址](http://www.bzip.org/downloads.html)

  * **增量更新三部曲**

  1. 生成两个版本apk的差分包;   
  2. 在手机客户端，使用已安装的apk与这个差分包进行合成，得到新版的apk;   
  3. 校验新合成的apk文件是否完整，MD5或SHA1是否正确，如正确，则引导用户安装; 
  
   *  **环境配置**  
 创建一个工程，勾选Include C++ Support，Android Studio会在main目录创建cpp文件夹，里边有个native-lib.cpp的C++文件；在app目录还有个CMakeLists.txt文件；在module的build.gradle中标示了采用CMake构建方式，并设置CMakeLists.txt路径。
     
  
 *  **过程分析**   

这一步需要在服务器端来实现，一般来说，每当apk有新版本需要提示用户升级，都需要运营人员在后台管理端上传新apk，上传时就应该由程序生成与之前所有旧版本们与最新版的差分包。

例如： 你的apk已经发布了3个版，V1.0、V2.0、V3.0，这时候你要在后台发布V4.0，那么，当你在服务器上传最新的V4.0包时，服务器端就应该立即生成以下差分包：

V1.0 ——> V4.0的差分包；   
V2.0 ——> V4.0的差分包；   
V3.0 ——> V4.0的差分包；   

生成patch包直接略过...

 * **合成新的APK步骤**  
根据cpp/bspatch.c文件定义的JNI
创建BsPatchJNI.java，用来合成增量文件

public class BsPatchJNI {

    static {
        System.loadLibrary("bspatch");
    }

    /**
     * 将增量文件合成为新的Apk
     * @param oldApkPath 当前Apk路径
     * @param newApkPath 合成后的Apk保存路径
     * @param patchPath 增量文件路径
     * @return
     */
    public static native int patch(String oldApkPath, String newApkPath, String patchPath);
}


在MainActivity中使用：

public class MainActivity extends AppCompatActivity {

    public static final String SDCARD_PATH = Environment.getExternalStorageDirectory() + File.separator;
    public static final String PATCH_FILE = "old-to-new.patch";
    public static final String NEW_APK_FILE = "new.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_main).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //并行任务
                new ApkUpdateTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
    }

    /**
     * 合并增量文件任务
     */
    private class ApkUpdateTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            String oldApkPath = ApkUtils.getCurApkPath(MainActivity.this);
            File oldApkFile = new File(oldApkPath);
            File patchFile = new File(getPatchFilePath());
            if(oldApkFile.exists() && patchFile.exists()) {
                Log("正在合并增量文件...");
                String newApkPath = getNewApkFilePath();
                BsPatchJNI.patch(oldApkPath, newApkPath, getPatchFilePath());
//                //检验文件MD5值
//                return Signtils.checkMd5(oldApkFile, MD5);

                Log("增量文件的MD5值为：" + SignUtils.getMd5ByFile(patchFile));
                Log("新文件的MD5值为：" + SignUtils.getMd5ByFile(new File(newApkPath)));

                return true;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if(result) {
                Log("合并成功，开始安装");
                ApkUtils.installApk(MainActivity.this, getNewApkFilePath());
            } else {
                Log("合并失败");
            }
        }
    }

    private String getPatchFilePath() {
        return SDCARD_PATH + PATCH_FILE;
    }

    private String getNewApkFilePath() {
        return SDCARD_PATH + NEW_APK_FILE;
    }

    /**
     * 打印日志
     * @param log
     */
    private void Log(String log) {
        Log.e("MainActivity", log);
    }

}


创建ApkUtils.java，用来获取当前Apk路径和安装新的Apk文件

public class ApkUtils {

    /**
     * 获取当前应用的Apk路径
     * @param context 上下文
     * @return
     */
    public static String getCurApkPath(Context context) {
        context = context.getApplicationContext();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String apkPath = applicationInfo.sourceDir;
        return apkPath;
    }

    /**
     * 安装Apk
     * @param context 上下文
     * @param apkPath Apk路径
     */
    public static void installApk(Context context, String apkPath) {
        File file = new File(apkPath);
        if(file.exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            context.startActivity(intent);
        }
    }
}


创建SignUtils.java，用来校验增量文件和合成的新Apk文件MD5值是否与服务器给的值相同

public class SignUtils {

    /**
     * 判断文件的MD5值是否为指定值
     * @param file1
     * @param md5
     * @return
     */
    public static boolean checkMd5(File file1, String md5) {
        if(TextUtils.isEmpty(md5)) {
            throw new RuntimeException("md5 cannot be empty");
        }

        if(file1 != null && file1.exists()) {
            String file1Md5 = getMd5ByFile(file1);
            return file1Md5.equals(md5);
        }
        return false;
    }

    /**
     * 获取文件的MD5值
     * @param file
     * @return
     */
    public static String getMd5ByFile(File file) {
        String value = null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);

            MessageDigest digester = MessageDigest.getInstance("MD5");
            byte[] bytes = new byte[8192];
            int byteCount;
            while ((byteCount = in.read(bytes)) > 0) {
                digester.update(bytes, 0, byteCount);
            }
            value = bytes2Hex(digester.digest());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != in) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return value;
    }

    private static String bytes2Hex(byte[] src) {
        char[] res = new char[src.length * 2];
        final char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        for (int i = 0, j = 0; i < src.length; i++) {
            res[j++] = hexDigits[src[i] >>> 4 & 0x0f];
            res[j++] = hexDigits[src[i] & 0x0f];
        }

        return new String(res);
    }
}




