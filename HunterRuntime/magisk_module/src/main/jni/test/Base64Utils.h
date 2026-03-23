//
// Created by Zhenxi on 2021/4/8.
//

#ifndef INC_01_BASE64UTILS_H
#define INC_01_BASE64UTILS_H

#include <stdlib.h>
#include <math.h>
#include <memory.h>
#include <string>




using namespace std;

//原始码表
//全局唯一变量 iniArry里面
static const string base64Char = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";



//算法变种Key
static const int key[] = {1,2,3,4,5,6,7};


class Base64Utils {

public:
    static string Encode(const string& origSigned);
    static string Encode(uint8_t* origSigned,size_t len);

    static string Decode(string origSigned);

    //变种Base64加密
    static string VTEncode(string origSigned);
    static string VTDecode(string origSigned);


    static bool is_base64(unsigned char c) {
        return (isalnum(c) || (c == '+') || (c == '/'));
    }

};


#endif //INC_01_BASE64UTILS_H
