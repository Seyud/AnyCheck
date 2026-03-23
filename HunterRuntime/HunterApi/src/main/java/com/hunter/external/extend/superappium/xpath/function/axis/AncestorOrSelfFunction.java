 package com.hunter.external.extend.superappium.xpath.function.axis;

 import com.hunter.external.extend.superappium.ViewImage;

 import java.util.List;

 public class AncestorOrSelfFunction implements AxisFunction
 {
   public List<ViewImage> call(ViewImage e, List<String> args)
   {
     List<ViewImage> rs = e.parents();
     rs.add(e);
     return rs;
   }
   
   public String getName()
   {
     return "ancestorOrSelf";
   }
 }

