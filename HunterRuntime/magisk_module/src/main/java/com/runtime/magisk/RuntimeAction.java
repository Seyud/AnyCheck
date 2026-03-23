package com.runtime.magisk;

import static com.runtime.magisk.MainStub.TARGET_BINDER_SERVICE_DESCRIPTOR;
import static com.runtime.magisk.MainStub.TARGET_BINDER_SERVICE_NAME;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.runtime.magisk.server.BinderServiceProxy;
import com.runtime.magisk.utils.CLog;

import mirror.android.os.ServiceManager;

/**
 * @author Zhenxi on 2023/11/26
 */
public class RuntimeAction {
    /**
     * 客户端通过<剪切板服务binder>
     * 和服务端通讯拿到我们驻留在服务端的binder
     */
    public static IBinder getBinderFrom(IBinder clipboard_service, String descriptor) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            boolean success = clipboard_service.transact(BinderServiceProxy.GET_BINDER_TRANSACTION, data, reply, 0);
            reply.readException();
            if (!success) {
                // Unknown transaction => remote service doesn't handle it, ignore this process.
                return null;
            }
            return reply.readStrongBinder();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * 返回我们驻留在服务端的Binder
     * 也就是RuntimeManagerService
     */
    public static IBinder getAppProcessIpcBinder(IBinder service){
        if(service != null){
            return service;
        }
        // We only expect that for pre oreo.
        IBinder clipboard;
        try {
            //尝试获取剪切板服务,判断是否存在
            clipboard = ServiceManager.getService.callStatic(TARGET_BINDER_SERVICE_NAME);
        } catch (Exception e) {
            CLog.e("Couldn't find the clipboard service", e);
            return null;
        }

        if (clipboard == null) {
            // Isolated process or google gril service process is not allowed to access clipboard service
            CLog.i("Clipboard service is unavailable in current process, skipping");
            return null;
        }

        try {
            //获取服务端binder实例,通过ipc获取
            service = getBinderFrom(clipboard, TARGET_BINDER_SERVICE_DESCRIPTOR);
            if (service == null) {
                CLog.i("runtime get Clipboard service error");
                return null;
            }
            return service;
        } catch (Exception e) {
            if (e.getClass() == RuntimeException.class
                    && "Unknown transaction code".equals(e.getMessage())) {
                // Unknown transaction => remote service doesn't handle it, ignore this process.
                return null;
            }
            CLog.e("Couldn't check whether the current process is needs to hook", e);
        }
        return null;
    }
}
