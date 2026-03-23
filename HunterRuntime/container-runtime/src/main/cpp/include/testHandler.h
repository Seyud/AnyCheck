//
// Created by ww on 2024/9/25.
//

#ifndef HUNTERRUNTIME_TESTHANDLER_H
#define HUNTERRUNTIME_TESTHANDLER_H

#endif //HUNTERRUNTIME_TESTHANDLER_H

#include <string>
#include <list>
#include <fstream>

class testHandler {
public:
    static void hookTestStrHandler(const std::list<std::string>& strList, std::ofstream* outStream);
};
