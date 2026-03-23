//
// Created by zhenxi on 2022/1/21.
//

#ifndef QCONTAINER_PRO_STRINGHANDLER_H
#define QCONTAINER_PRO_STRINGHANDLER_H

#include <list>

#include "stringUtils.h"
#include "libpath.h"


class stringHandler2 {
public:

    static void hookStrHandler2(const std::list<std::string> &filter_list, std::ofstream *os);

    [[maybe_unused]] static void stopJnitrace();

private:
    static void init();
};


#endif //QCONTAINER_PRO_STRINGHANDLER_H
