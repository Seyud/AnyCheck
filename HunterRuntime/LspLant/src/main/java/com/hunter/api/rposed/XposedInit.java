package com.hunter.api.rposed;

import static com.hunter.api.rposed.RposedHelpers.closeSilently;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;
import com.hunter.api.rposed.callbacks.RC_LoadPackage;

@SuppressLint({"PrivateApi", "unused"})
public final class XposedInit {
	private static final String TAG = RposedBridge.TAG;

	private static final String INSTANT_RUN_CLASS = "com.android.tools.fd.runtime.BootstrapApplication";

	private XposedInit() { }

	/**
	 * Load a module from an APK by calling the init(String) method for all classes defined
	 * in <code>assets/xposed_init</code>.
	 */
	public static void loadModule(String apk, ClassLoader topClassLoader) {
		Log.i(TAG, "Loading modules from " + apk);

		if (!new File(apk).exists()) {
			Log.e(TAG, "  File does not exist");
			return;
		}

		DexFile dexFile;
		try {
			dexFile = new DexFile(apk);
		} catch (IOException e) {
			Log.e(TAG, "  Cannot load module", e);
			return;
		}

		if (dexFile.loadClass(INSTANT_RUN_CLASS, topClassLoader) != null) {
			Log.e(TAG, "  Cannot load module, please disable \"Instant Run\" in Android Studio.");
			closeSilently(dexFile);
			return;
		}

		if (dexFile.loadClass(RposedBridge.class.getName(), topClassLoader) != null) {
			Log.e(TAG, "  Cannot load module:");
			Log.e(TAG, "  The Xposed API classes are compiled into the module's APK.");
			Log.e(TAG, "  This may cause strange issues and must be fixed by the module developer.");
			Log.e(TAG, "  For details, see: http://api.xposed.info/using.html");
			closeSilently(dexFile);
			return;
		}

		closeSilently(dexFile);

		ZipFile zipFile = null;
		InputStream is;
		try {
			zipFile = new ZipFile(apk);
			ZipEntry zipEntry = zipFile.getEntry("assets/xposed_init");
			if (zipEntry == null) {
				Log.e(TAG, "  assets/xposed_init not found in the APK");
				closeSilently(zipFile);
				return;
			}
			is = zipFile.getInputStream(zipEntry);
		} catch (IOException e) {
			Log.e(TAG, "  Cannot read assets/xposed_init in the APK", e);
			closeSilently(zipFile);
			return;
		}

		ClassLoader mcl = new PathClassLoader(apk, RposedBridge.BOOTCLASSLOADER);
		BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(is));

		try {
			String moduleClassName;
			while ((moduleClassName = moduleClassesReader.readLine()) != null) {
				moduleClassName = moduleClassName.trim();
				if (moduleClassName.isEmpty() || moduleClassName.startsWith("#")) {
					continue;
				}

				try {
					Log.i(TAG, "  Loading class " + moduleClassName);
					Class<?> moduleClass = mcl.loadClass(moduleClassName);

					if (!IXposedMod.class.isAssignableFrom(moduleClass)) {
						Log.e(TAG, "    This class doesn't implement any sub-interface of IXposedMod, skipping it");
						continue;
					} /*else if (IXposedHookInitPackageResources.class.isAssignableFrom(moduleClass)) {
						Log.e(TAG, "    This class requires resource-related hooks (which are disabled), skipping it.");
						continue;
					}*/

					final Object moduleInstance = moduleClass.newInstance();
					if (moduleInstance instanceof IRposedHookLoadPackage) {
						RposedBridge.hookLoadPackage(new IRposedHookLoadPackage.Wrapper((IRposedHookLoadPackage) moduleInstance));
					}

					/*if (moduleInstance instanceof IXposedHookInitPackageResources) {
						XposedBridge.hookInitPackageResources(new IXposedHookInitPackageResources.Wrapper((IXposedHookInitPackageResources) moduleInstance));
					}*/
				} catch (Throwable t) {
					Log.e(TAG, "    Failed to load class " + moduleClassName, t);
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "  Failed to load module from " + apk, e);
		} finally {
			closeSilently(is);
			closeSilently(zipFile);
		}
	}

	public static void onPackageLoad(String packageName, String processName, ApplicationInfo appInfo, boolean isFirstApp, ClassLoader classLoader) {
		RC_LoadPackage.LoadPackageParam param = new RC_LoadPackage.LoadPackageParam(RposedBridge.sLoadedPackageCallbacks);
		param.packageName = packageName;
		param.processName = processName;
		param.appInfo = appInfo;
		param.isFirstApplication = isFirstApp;
		param.classLoader = classLoader;
		RC_LoadPackage.callAll(param);
	}
}
