package com.hunter.external.extend.superappium.xpath.function.select;

import com.hunter.external.extend.superappium.ViewImages;
import com.hunter.external.extend.superappium.xpath.function.NameAware;
import com.hunter.external.extend.superappium.xpath.model.XNodes;
import com.hunter.external.extend.superappium.xpath.model.XpathNode;

import java.util.List;

public interface SelectFunction extends NameAware {
  XNodes call(XpathNode.ScopeEm scopeEm, ViewImages elements, List<String> args);
}
