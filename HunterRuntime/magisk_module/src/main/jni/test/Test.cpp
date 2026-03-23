//
// Created by Zhenxi on 2022/6/6.
//

#include <cstdio>
#include <unistd.h>
#include <fcntl.h>
#include <sys/utsname.h>
#include <dirent.h>
#include <parse.h>
#include <media/NdkMediaDrm.h>
#include <string>
#include <sstream>
#include <sys/vfs.h>
#include <iostream>
#include <sys/sysinfo.h>
#include <sys/stat.h>
#include <stdio.h>
#include <fcntl.h> // AT_FDCWD
#include <unistd.h>
#include <media/NdkMediaDrm.h>

#include "mylibc.h"
#include "Test.h"
#include "logging.h"
#include "ZhenxiLog.h"
#include "appUtils.h"
#include "fileUtils.h"
#include "HookUtils.h"
#include "common_macros.h"
#include "linkerHandler.h"
#include "pmparser.h"
#include "mylibc.h"
#include "Base64Utils.h"

void ClientTest::testDrm() {
    const uint8_t uuid[] =
            {0xed, 0xef, 0x8b, 0xa9, 0x79, 0xd6, 0x4a, 0xce,
             0xa3, 0xc8, 0x27, 0xdc, 0xd5, 0x1d,
             0x21, 0xed};

    AMediaDrm *mediaDrm = AMediaDrm_createByUUID(uuid);
    // 获取 deviceUniqueId
    AMediaDrmByteArray aMediaDrmByteArray;
    AMediaDrm_getPropertyByteArray(mediaDrm, PROPERTY_DEVICE_UNIQUE_ID, &aMediaDrmByteArray);
    LOGE("ClientTest::testDrm info -> %s",
         Base64Utils::Encode((uint8_t *) aMediaDrmByteArray.ptr, aMediaDrmByteArray.length).c_str())
    AMediaDrm_release(mediaDrm);
}

