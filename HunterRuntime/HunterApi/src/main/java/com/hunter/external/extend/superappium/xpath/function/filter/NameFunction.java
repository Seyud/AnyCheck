 package com.hunter.external.extend.superappium.xpath.function.filter;

 import com.hunter.external.extend.superappium.ViewImage;
 import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

 import java.util.List;

 public class NameFunction implements FilterFunction {
     @Override
     public Object call(ViewImage element, List<SyntaxNode> params) {
         return element.getType();
     }

     @Override
     public String getName() {
         return "name";
     }
 }
