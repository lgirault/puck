/*
 * Puck is a dependency analysis and refactoring tool.
 * Copyright (C) 2016 Loïc Girault loic.girault@gmail.com
 *               2016 Mikal Ziane  mikal.ziane@lip6.fr
 *               2016 Cédric Besse cedric.besse@lip6.fr
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Additional Terms.
 * Author attributions in that material or in the Appropriate Legal
 * Notices displayed by works containing it is required.
 *
 * Author of this file : Loïc Girault
 */

package puck.javaGraph.graphBuilding

import puck.AcceptanceSpec
import puck.graph.{NamedType, ParameterizedType, Uses}
import puck.javaGraph.ScenarioFactory

/**
  * Created by Loïc Girault on 06/04/16.
  */
class TypeConstraintSpec extends AcceptanceSpec {

  scenario("generic - type uses  constraint between type parameter and init"){
    val _ = new ScenarioFactory(
      """package p;
        |
        |class Wrapper<T> { T get(){return null;} }
        |
        |class A{ void m(){} }
        |
        |class B {  Wrapper<A> wa = new Wrapper<A>(); } """
    ){

      val `p.A` = fullName2id("p.A")
      val `p.B.wa` = fullName2id("p.B.wa")
      val `p.B.wa.Definition` = fullName2id("p.B.wa.Definition")
      val `p.Wrapper` = fullName2id("p.Wrapper")
      val `p.Wrapper.Wrapper()` = fullName2id("p.Wrapper.Wrapper()")


      assert(graph uses (`p.B.wa.Definition`, `p.A`))

      graph.styp(`p.B.wa`).value should be (ParameterizedType(`p.Wrapper`, List(NamedType(`p.A`))))

      graph.usesThatShouldUsesSameTypeAs((`p.B.wa`, `p.A`)) should contain (Uses(`p.B.wa.Definition`, `p.A`))

      graph.usesThatShouldUsesASuperTypeOf((`p.Wrapper.Wrapper()`, `p.Wrapper`)) should contain (Uses(`p.B.wa`, `p.Wrapper`))
      graph.usesThatShouldUsesASubtypeOf((`p.B.wa`, `p.Wrapper`)) should contain (Uses(`p.Wrapper.Wrapper()`, `p.Wrapper`))



    }
  }

  scenario("generic - type uses  relationship between type parameter and variable declaration type"){
    val _ = new ScenarioFactory(
      """package p;
        |
        |class Wrapper<T> { T get(){return null;} }
        |
        |class A{ void m(){} }
        |
        |class B {
        |    Wrapper<A> wa = new Wrapper<A>();
        |
        |    void assignA(){
        |        A a = wa.get();
        |        a.m();
        |    }
        |}"""){

      val `p.A` = fullName2id("p.A")
      val `p.A.m()` = fullName2id("p.A.m()")

      val `p.B.wa` = fullName2id("p.B.wa")
      val `p.B.assignA().Definition` = fullName2id("p.B.assignA().Definition")
      val `p.Wrapper` = fullName2id("p.Wrapper")

      assert (Uses(`p.B.wa`, `p.Wrapper`) existsIn graph)
      assert (Uses(`p.B.wa`, `p.A`) existsIn graph)

      assert (Uses(`p.B.assignA().Definition`, `p.A`) existsIn graph)
      assert (Uses(`p.B.assignA().Definition`, `p.A.m()`) existsIn graph)

      graph.styp(`p.B.wa`).value should be (ParameterizedType(`p.Wrapper`, List(NamedType(`p.A`))))

      graph.typeUsesOf(Uses(`p.B.assignA().Definition`, `p.A.m()`)) should contain (
        Uses(`p.B.assignA().Definition`, `p.A`))

      graph.typeMemberUsesOf(Uses(`p.B.assignA().Definition`, `p.A`)) should contain (
        Uses(`p.B.assignA().Definition`, `p.A.m()`))

      graph.typeMemberUsesOf(Uses(`p.B.assignA().Definition`, `p.A`)).size should be (1)

      graph.usesThatShouldUsesASuperTypeOf(Uses(`p.B.wa`, `p.A`)) should contain (
        Uses(`p.B.assignA().Definition`, `p.A`))


    }
  }

  scenario("generic - type uses  relationship between type parameter and variable declaration type - foreach case"){
    val _ = new ScenarioFactory(
      """package p;
        |import java.util.List;
        |
        |interface I{ void m(); }
        |
        |class C {
        |    List<I> is;
        |
        |    void doAllM(){
        |        for(I i : is)
        |            i.m();
        |    }
        |}"""){

      val actualTypeParam = fullName2id("p.I")
      val actualTypeParamMethod = fullName2id("p.I.m()")

      val field = fullName2id("p.C.is")
      val userMethodDef = fullName2id("p.C.doAllM().Definition")
      val genType = fullName2id("java.util.List")

      val fieldGenTypeUse = graph.getUsesEdge(field, genType).value
      val fieldParameterTypeUse = graph.getUsesEdge(field, actualTypeParam).value

      val methodTypeUse = graph.getUsesEdge(userMethodDef, actualTypeParam).value
      val methodTypeMemberUse = graph.getUsesEdge(userMethodDef, actualTypeParamMethod).value

      graph.styp(field).value should be (ParameterizedType(genType, List(NamedType(actualTypeParam))))

      graph.typeUsesOf(methodTypeMemberUse) should contain (methodTypeUse)
      graph.typeMemberUsesOf(methodTypeUse) should contain (methodTypeMemberUse)
      graph.typeMemberUsesOf(methodTypeUse).size should be (1)

      graph.usesThatShouldUsesASuperTypeOf(fieldParameterTypeUse) should contain (methodTypeUse)

    }
  }

  ignore("generic - type parameter as method parameter") {
    val _ = new ScenarioFactory(
      """package p;
        |
        |class Wrapper<T> {  void set(T t){} }
        |
        |class A{ }
        |
        |class B {
        |    Wrapper<A> wa;
        |    void set(){ wa.set(new A()); }
        |}"""
    ) {
      val a = fullName2id("p.A")

      val wa = fullName2id("p.B.wa")

      val set = fullName2id("p.B.set().Definition")

      val genType = fullName2id("p.Wrapper")
      val genericMethod = fullName2id("p.Wrapper.set(T)")


    }
  }

  scenario("2 type variables"){
    val _ = new ScenarioFactory(
    """package p;
      |
      |import java.util.Map;
      |import java.util.jar.Attributes;
      |
      |public class C {
      |
      |    Map<String, Attributes> datas;
      |
      |    public void m() {
      |       for(String s : datas.keySet()){
      |         Attributes i = datas.get(s);
      |       }
      |    }
      |}"""
    ){
//      val mDef = fullName2id("p.C.m().Definition")
//      val get = fullName2id("java.util.Map.get(Object)")

//      val hasElement = fullName2id("p.HasElement")
//      val obj = fullName2id("java.lang.Object")
//      val toString_ = fullName2id("java.lang.Object.toString()")


//      assert(graph.uses(mDef, get))
//      assert(graph.uses(mDef, toString_))
//
//      graph.typeUsesOf(mDef, toString_) should contain (Uses(hasElement, obj))
    }
  }
}