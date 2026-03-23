package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.function.NameAware;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public abstract interface FilterFunction extends NameAware
{
  public abstract Object call(ViewImage paramViewImage, List<SyntaxNode> paramList);
}