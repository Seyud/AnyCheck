package com.runtime.magisk.server;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SharedMemory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.runtime.magisk.utils.CLog;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Proxy for binder service.
 * Note: For ServiceManager.addService, service must be a Binder object
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/ServiceManager.java;l=179?q=ServiceManager
 * <p>
 * See function ibinderForJavaObject (in android_util_Binder.cpp)
 */
public class BinderServiceProxy extends Binder {

    /**
     * 客户端进程拿服务端进程RuntimeManagerService的TRANSACTION
     */
    public static final int GET_BINDER_TRANSACTION = ('_' << 24) | ('D' << 16) | ('M' << 8) | 'S';
    /**
     * 客户端获取dex的TRANSACTION
     */
    public static final int DEX_TRANSACTION_CODE = 1310096052;
    /**
     * 原始剪切板服务的Binder实例
     */
    private final Binder base;
    private final String descriptor;
    /**
     * 这个是我们自己服务的IBinder
     */
    private final IBinder service;
    private final CallerVerifier verifier;


    public BinderServiceProxy(Binder base, String descriptor, IBinder service, CallerVerifier verifier) {
        this.base = base;
        this.descriptor = descriptor;
        this.service = service;
        this.verifier = verifier;
    }

    @Nullable
    @Override
    public String getInterfaceDescriptor() {
        return base.getInterfaceDescriptor();
    }

    @Override
    public boolean pingBinder() {
        return base.pingBinder();
    }

    @Override
    public boolean isBinderAlive() {
        return base.isBinderAlive();
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return base.queryLocalInterface(descriptor);
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) {
        base.dump(fd, args);
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) {
        base.dumpAsync(fd, args);
    }


    public static void unzip(String zipFilePath, String destDirPath) throws IOException {
        Path destDir = Paths.get(destDirPath);
        if (Files.exists(destDir)) {
            try (Stream<Path> walk = Files.walk(destDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        Files.createDirectories(destDir);
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                Path filePath = destDir.resolve(entry.getName());
                //CLog.e("unzip filePath "+filePath);
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipIn, filePath, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.createDirectories(filePath);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    @Override
    protected boolean onTransact(int code, @NonNull Parcel data,
                                 @Nullable Parcel reply, int flags) throws RemoteException {
        //方便native调用
        if (code == GET_BINDER_TRANSACTION) {
            //CLog.i("RuntimeManagerService receive GET_BINDER_TRANSACTION ");
            assert reply != null;
            data.enforceInterface(descriptor);
            reply.writeNoException();
            reply.writeStrongBinder(service);
            //CLog.i("RuntimeManagerService  GET_BINDER_TRANSACTION return");
            return true;
        } else if (code == DEX_TRANSACTION_CODE) {
            assert reply != null;
            //CLog.i("RuntimeManagerService receive DEX_TRANSACTION_CODE ");
            try {
                //unzip jar
                unzip(RuntimeManagerService.RUNTIME_JAR_PATH,
                        RuntimeManagerService.RUNTIME_TEMP_DEX_PATH);
                //get dex
                File[] files = new File(RuntimeManagerService.RUNTIME_TEMP_DEX_PATH).listFiles();
                if (files == null || files.length == 0) {
                    CLog.i("DEX_TRANSACTION_CODE files size == 0 ");
                    return false;
                }
                @SuppressWarnings("all")
                List<File> dexFiles = (files == null) ? Collections.emptyList() :
                        Stream.of(files).filter(file -> file.getName().endsWith(".dex")).collect(Collectors.toList());
                if (dexFiles.size() == 0) {
                    CLog.i("DEX_TRANSACTION_CODE dexFiles.size()==0 ");
                    return false;
                }
                //CLog.i("DEX_TRANSACTION_CODE dex info " + dexFiles);
                ArrayList<SharedMemory> shm = RuntimeManagerService.getPreloadDex(dexFiles);
                if (shm == null || shm.isEmpty()) return false;
                reply.writeNoException();
                //dex size
                reply.writeInt(shm.size());
                for (int i = 0; i < shm.size(); i++) {
                    // assume that write only a fd
                    SharedMemory sharedMemory = shm.get(i);
                    sharedMemory.writeToParcel(reply, 0);
                    reply.writeLong(sharedMemory.getSize());
                }
                //clean temp dir

                FileUtils.deleteDirectory(
                        new File(RuntimeManagerService.RUNTIME_TEMP_DEX_PATH)
                );
            } catch (Throwable e) {
                CLog.e("DEX_TRANSACTION_CODE handler error  " + e, e);
            }
            //CLog.i("RuntimeManagerService receive DEX_TRANSACTION_CODE return");
            return true;
        }
        return base.transact(code, data, reply, flags);
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
        base.linkToDeath(recipient, flags);
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        return base.unlinkToDeath(recipient, flags);
    }

    @FunctionalInterface
    public interface CallerVerifier {
        boolean canAccessService();
    }
}
