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

package puck.jastadd
package concretize

import org.extendj.ast._
import puck.graph._
import puck.javaGraph._
import puck.javaGraph.nodeKind._
import puck.util.PuckLogger
import org.extendj.{ast => AST}

object CreateEdge {

  def createTypeUse
  (id2declMap: NodeId => ASTNodeLink,
    typed : NodeId,
    typ : NodeId) : Unit = {
    id2declMap(typed)  match {
      //explicit upcast shouldn't be needed, why the compiling error ?
      case dh @ (FieldDeclHolder(_,_)
        | ParameterDeclHolder(_)
        | MethodDeclHolder(_)) =>

        val taccess : AST.Access = id2declMap(typ) match {
          case tdh : TypedKindDeclHolder => tdh.decl.createLockedAccess()
          case dh => throw new JavaAGError(s"CreateEdge.createTypeUse: $dh where expected a typeDecl")
        }

        dh.asInstanceOf[HasNode].node.setTypeAccess(taccess)

      case ConstructorDeclHolder(_) => ()

      case k => throw new JavaAGError(s"CreateEdge.createTypeUse: $k as user of TypeKind, set type unhandled !")
    }


  }


  def apply
  ( graph: DependencyGraph,
    reenactor : DependencyGraph,
    id2declMap: NodeId => ASTNodeLink,
    e: DGEdge)
  ( implicit program : AST.Program, logger : PuckLogger) = {
        e.kind match {
          case Contains =>
            createContains(graph, reenactor, id2declMap, e)
          case ContainsParam =>
            (id2declMap(e.container), id2declMap(e.content)) match {
              case (MethodDeclHolder(mdecl), ParameterDeclHolder(pdecl)) =>
                mdecl.prependParameter(pdecl)
              case (ConstructorDeclHolder(cdecl), ParameterDeclHolder(pdecl)) =>
                cdecl.prependParameter(pdecl)
              case _ =>
                error(s"ContainsParam(${reenactor.getNode(e.container)}, ${reenactor.getNode(e.content)}) " +
                  "should be between a decl and a param")
            }

          case Isa =>
            createIsa(id2declMap(e.subType), id2declMap(e.superType))

          case Uses =>
            val u = e.asInstanceOf[Uses]
            val (source, target ) = (graph.getNode(e.source), graph.getNode(e.target))
            (source.kind, target.kind) match {
              case (Class, Interface) =>
                logger.writeln("do not create %s : assuming its an isa edge (TOCHECK)".format(e)) // class imple
              case (Definition, Constructor) =>
                createUsesOfConstructor(graph, reenactor, id2declMap, u)


//              case (Definition, Field) => ()
//                createUsesofField(graph, reenactor, id2declMap, u)
              case (Definition, Method) if ensureIsInitalizerUseByCtor(reenactor, u)=>
                createInitializerCall(reenactor, id2declMap, u)

              case _ => logger.writeln(" =========> need to create " + e)
            }
          case _  =>
            logger.writeln(s"Creation of ${e.kind} ignored")

        }

  }

  def ensureIsInitalizerUseByCtor(graph: DependencyGraph, u : Uses) : Boolean =
    graph.kindType(graph.container_!(u.user)) == TypeConstructor &&
      (graph.getRole(u.used) contains Initializer(graph.hostTypeDecl(u.user)))

  def createInitializerCall
    ( reenactor : DependencyGraph,
      id2declMap : NodeId => ASTNodeLink,
      e : Uses)
    ( implicit program : AST.Program, logger : PuckLogger) : Unit = {
    val sourceDecl = reenactor.container_!(e.user)
    (id2declMap(sourceDecl), id2declMap(e.used)) match {
      case (ConstructorDeclHolder(cdecl), MethodDeclHolder(mdecl)) =>
        cdecl.unsetImplicitConstructor()
        cdecl.addInitializerCall(mdecl)
      case hs => error("createInitializerCall : expected constructor using method got " + hs)
    }
  }


  def createContains
  ( graph: DependencyGraph,
    reenactor : DependencyGraph,
    id2declMap : NodeId => ASTNodeLink,
    e : DGEdge)
  ( implicit program : AST.Program, logger : PuckLogger) : Unit =
    (id2declMap(e.container), id2declMap(e.content)) match {
      case (PackageDeclHolder, i: TypedKindDeclHolder) =>
        setPackageDecl(reenactor, e.container, e.content, i.decl)
        program.registerType(graph.fullName(e.content), i.decl)
      case (th: TypedKindDeclHolder, MethodDeclHolder(mdecl)) =>
        th.decl.addBodyDecl(mdecl)

      case (_, PackageDeclHolder) => () // can be ignored

      case (ClassDeclHolder(clsdecl), bdHolder : HasBodyDecl) =>
        clsdecl.addBodyDecl(bdHolder.decl)

      case _ => logger.writeln(" =========> %s not created".format(e))

    }

  def createIsa
  (sub : ASTNodeLink, sup : ASTNodeLink)
  ( implicit logger : PuckLogger) : Unit = (sub, sup) match {
    case (ClassDeclHolder(sDecl), InterfaceDeclHolder(idecl)) =>
      sDecl.addImplements(idecl.createLockedAccess())

    case (InterfaceDeclHolder(ideclSub), InterfaceDeclHolder(ideclSup)) =>
      ideclSub.addSuperInterface(ideclSup.createLockedAccess())

    case (ClassDeclHolder(subDecl), ClassDeclHolder(superDecl)) =>
      subDecl.setSuperClass(superDecl.createLockedAccess())

    case e => logger.writeln(s"isa($e) not created")
  }

  def createUsesOfConstructor
  ( graph: DependencyGraph,
    reenactor : DependencyGraph,
    id2declMap : NodeId => ASTNodeLink,
    e : Uses)
  ( implicit logger : PuckLogger) : Unit = {
    val sourceDecl = reenactor declarationOf e.user
    val ConstructorDeclHolder(cdecl) = id2declMap(e.used)
    id2declMap(sourceDecl) match {
      case FieldDeclHolder(fdecl, idx)
        if fdecl.getDeclarator(idx).getInitOpt.isEmpty =>
        CreateNode.createNewInstanceExpr(fdecl.getDeclarator(idx), cdecl)
      case MethodDeclHolder(mdecl)
        if reenactor getRole sourceDecl contains Factory(e.used) =>
          mdecl.makeFactoryOf(cdecl)
      case dh => error(s"createUsesOfConstructor ${dh.getClass} " +
        s"with role ${reenactor getRole sourceDecl} as user unhandled")

    }
  }

//  def createUsesofField
//  ( graph: DependencyGraph,
//    reenactor : DependencyGraph,
//    id2declMap : NodeId => ASTNodeLink,
//    e : Uses)
//  ( implicit logger : PuckLogger) : Unit = {
//
//    val typesUsed = reenactor.usedBy(e.used).filter{
//      id => reenactor.kindType(id) == TypeDecl
//    }
//
//    if (typesUsed.size != 1)
//      throw new puck.graph.Error(s"require ONE type use got ${typesUsed.size}")
//
//    val typeUse = Uses(e.used, typesUsed.head)
//    val tmUses = reenactor.typeMemberUsesOf(typeUse).filter{_.user == e.user}
//
//    (id2declMap(e.user), id2declMap(e.used)) match {
//      case (dh: DefHolder, FieldDeclHolder(newReceiverDecl)) =>
//        val receiver = newReceiverDecl.createLockedAccess()
//        tmUses.map { u =>
//          id2declMap(u.used)}.foreach {
//          case MethodDeclHolder(methUsedDecl) =>
//            dh.node.addNewReceiver(methUsedDecl, receiver)
//          case FieldDeclHolder(fieldUsedDecl) =>
//            dh.node.addNewReceiver(fieldUsedDecl, receiver)
//          case used =>
//            logger.writeln(s"create receiver for $used ignored")
//        }
//
//      case h => throw new puck.graph.Error(s"method decl and field decl expected, got $h")
//    }
//  }


  def setPackageDecl
  ( graph: DependencyGraph,
    packageId : NodeId,
    typeDeclNodeId : NodeId,
    td : AST.TypeDecl)
  ( implicit program : AST.Program, logger : PuckLogger) = {

    val cu = td.compilationUnit()
    val pkgDecl = graph.fullName(packageId)
    val path = ASTNodeLink.getPath(graph, packageId, typeDeclNodeId)
    cu.setPackageDecl(pkgDecl)
    cu.setPathName(path)
    //!\ very important !!
    cu.flushTreeCache()
  }

}
