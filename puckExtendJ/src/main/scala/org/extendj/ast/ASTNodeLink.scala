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

package org.extendj.ast

import puck.graph.{TypeDecl => PTypeDecl, _}
import org.extendj.concretize.RedirectSource
import puck.util.PuckLogger

object ASTNodeLink{

  def setName(name : String,
              reenactor : DependencyGraph, renamed : NodeId)
             ( implicit dg2ast : NodeId => ASTNodeLink,
               logger : PuckLogger) : Unit = dg2ast(renamed) match {
    case FieldDeclHolder(decl, idx) => decl.getDeclarator(idx).setID(name)
    case MethodDeclHolder(mdecl) => mdecl.setID(name)
    case TypedKindDeclHolder(tdecl) =>
      val oldName = tdecl.getID
      tdecl.setID(name)
      val cu = tdecl.compilationUnit()
      //TODO if printed in place oldName.java should be deleted
      if(cu.pathName().endsWith(s"$oldName.java"))
        cu.setPathName(cu.pathName().replaceAllLiterally(s"$oldName.java", s"$name.java"))
      import scala.collection.JavaConversions._
      tdecl.constructors().foreach{cdecl =>
        cdecl.flushAttrCache()
        cdecl.setID(name)
      }
      val oldFullName = reenactor.fullName(renamed)
      val containerFullName = reenactor.fullName(reenactor.container_!(renamed))

      val newFullName =
        if(containerFullName.isEmpty) name
        else  s"$containerFullName.$name"
      tdecl.program().changeTypeMap(oldFullName, newFullName, tdecl)

    case ConstructorDeclHolder(cdecl) => cdecl.setID(name)
    case h => setPackageName(name, reenactor, renamed)
  }

  def setPackageName
  ( name : String,
    reenactor : DependencyGraph, renamed : NodeId)
  ( implicit dg2ast : NodeId => ASTNodeLink, logger : PuckLogger) : Unit = {

    val oldName = reenactor fullName renamed
    val newName = {
      val containerName = reenactor fullName (reenactor container_! renamed)
      if(containerName.isEmpty) name
      else s"$containerName.$name"
    }

    reenactor.content(renamed) map (id => (id, dg2ast(id))) foreach {
      case (tid, TypedKindDeclHolder(td)) =>
        td.compilationUnit().setPackageDecl(newName) //both setter and affectation needed
        td.compilationUnit().packageName_value = newName //this value is the cached one
        RedirectSource.fixImportForUsersOfMovedTypeDecl(reenactor, td, tid, oldName, newName)
        RedirectSource.fixImportOfMovedTypeDecl(reenactor, td, tid, oldName, newName, newlyCreatedCu = false)
    }
  }


  def getPath(graph: DependencyGraph, packagedId : NodeId)
             ( implicit program : Program ) : String = {
    val cpath = graph.containerPath(packagedId)
    val names = cpath.tail.map(graph.getConcreteNode(_).name)
    program.getRootPath + names.mkString(java.io.File.separator)
  }
  def getPath(graph: DependencyGraph, packagedId : NodeId, typeDeclId : NodeId)
             ( implicit program : Program ) : String  =
    getPath(graph, packagedId) + java.io.File.separator +
      graph.getConcreteNode(typeDeclId).name + ".java"


  def enlargeVisibilityOfNidIfNeeded
  ( g : DependencyGraph,
    astNode : Visible,
    nid : NodeId)
  ( implicit id2declMap: NodeId => ASTNodeLink) : Unit = {
    val n = g.getConcreteNode(nid)

    import ASTNode.VIS_PUBLIC
    n.kind.kindType match {
      case PTypeDecl  =>

        val hostNameSpace = g.hostNameSpace(nid)
        val needMoreVisibility : NodeId => Boolean =
          g.hostNameSpace(_) != hostNameSpace
        val ctors = g.content(nid)

        val contentThatNeedMoreVisibility = ctors.filter {
          ctor =>
            g.usersOfExcludingTypeUse(ctor) exists needMoreVisibility
        }

        contentThatNeedMoreVisibility foreach { n =>
          val v  : Visible = id2declMap(n) match {
            case hn : HasNode =>
              hn.node match {
                case v : Visible => v
                case nv => puck.error("Visible expected but got " + nv)
              }
            case _ => puck.error()
          }
          v.setVisibility(VIS_PUBLIC)
          v.getModifiers.flushAttrCache()
        }

        if (contentThatNeedMoreVisibility.nonEmpty ||
          (g.usersOf(nid) exists needMoreVisibility)) {
          astNode.setVisibility(VIS_PUBLIC)
          astNode.asInstanceOf[TypeDecl].flushVisibilityCache()
          astNode.getModifiers.flushAttrCache()
        }

      case InstanceValue | StableValue
        if astNode.getVisibility != VIS_PUBLIC =>

        val hostTypeDecl = g.hostTypeDecl(nid)

        val needMoreVisibility : NodeId => Boolean =
          g.hostTypeDecl(_) != hostTypeDecl

        if (g.usersOfExcludingTypeUse(nid) exists needMoreVisibility) {
          astNode.setVisibility(VIS_PUBLIC)
          astNode.getModifiers.flushAttrCache()
        }


      case InstanceValue | StableValue => ()
      case kt => error(s"$kt not expected")
    }

  }
}

sealed trait ASTNodeLink
sealed trait CanAccessHostType extends ASTNodeLink {
  def hostType : TypeDecl
}

case object NoDecl extends ASTNodeLink
object HasNode {
  def unapply(arg: HasNode): Some[ASTNode[_]] = Some[ASTNode[_]](arg.node)
}
sealed abstract class HasNode extends ASTNodeLink {
  def node : ASTNode[_]
}
//object ASTNodeHolder {
//  def unapply(nl : ASTNodeLink) : Option[ASTNode[_]] = nl match {
//    case hn : HasNode => Some(hn.node)
//    case _ => None
//  }
//}
case object PackageDeclHolder extends ASTNodeLink


sealed abstract class DefHolder extends HasNode
case class ExprHolder(expr : Expr)
  extends DefHolder with CanAccessHostType {
  def node = expr.asInstanceOf[ASTNode[_]]
  def hostType : TypeDecl = expr.hostType()
}
case class BlockHolder(block : Block)
  extends DefHolder with CanAccessHostType {
  def node = block.asInstanceOf[ASTNode[_]]
  def hostType : TypeDecl = block.hostType()
}

case class ParameterDeclHolder(decl : ParameterDeclaration)
  extends HasNode with CanAccessHostType {
  def node = decl.asInstanceOf[ASTNode[_]]
  def hostType : TypeDecl = decl.hostType()
}

sealed trait HasBodyDecl
  extends HasNode with CanAccessHostType {
  val decl : BodyDecl
  def node = decl.asInstanceOf[ASTNode[_]]
  def hostType : TypeDecl = decl.hostType()
}



sealed trait HasMemberDecl extends HasBodyDecl {
  override val decl : MemberDecl
}

object VariableDeclHolder {
  def unapply(nl : ASTNodeLink) : Option[Variable] = nl match {
    case FieldDeclHolder(decl, idx) => Some(decl.getDeclarator(idx))
    case ParameterDeclHolder(decl) => Some(decl)
    case LocalVarDeclHolder(decl) => Some(decl)
    case _ => None
  }

}

class DeclarationCreationError(msg : String) extends DGError(msg)

case class ConstructorDeclHolder(decl : ConstructorDecl) extends HasBodyDecl
case class MethodDeclHolder(decl : MethodDecl) extends HasMemberDecl

object CallableDeclHolder {
  def unapply(nl : ASTNodeLink) : Option[Callable] = nl match {
    case ConstructorDeclHolder(cdecl) => Some(cdecl)
    case MethodDeclHolder(mdecl) => Some(mdecl)
    case _ => None
  }
}

case class FieldDeclHolder(decl : FieldDecl, declaratorIndex : Int) extends HasMemberDecl {
  def declarator = decl.getDeclarator(declaratorIndex)
}

case class LocalVarDeclHolder(decl : VariableDeclarator)
  extends HasNode with CanAccessHostType {
  def node = decl.asInstanceOf[ASTNode[_]]
  def hostType : TypeDecl = decl.hostType()
}

case class EnumConstantHolder(decl : EnumConstant) extends HasBodyDecl

trait TypedKindDeclHolder extends HasNode {
  def decl : TypeDecl
  def node = decl.asInstanceOf[ASTNode[_]]
}

object TypedKindDeclHolder {
  def unapply(l : ASTNodeLink) : Option[TypeDecl] =
    l match {
      case th : TypedKindDeclHolder => Some(th.decl)
      case _ => None
    }
}

case class InterfaceDeclHolder(decl : InterfaceDecl) extends TypedKindDeclHolder
case class EnumDeclHolder(decl : EnumDecl) extends TypedKindDeclHolder
case class ClassDeclHolder(decl : ClassDecl) extends TypedKindDeclHolder
case class WildCardTypeHolder(decl : AbstractWildcardType) extends TypedKindDeclHolder
case class TypeVariableHolder(decl : TypeVariable) extends TypedKindDeclHolder
case class PrimitiveDeclHolder(decl : TypeDecl) extends TypedKindDeclHolder