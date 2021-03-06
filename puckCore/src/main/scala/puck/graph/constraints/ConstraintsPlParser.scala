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

package puck.graph
package constraints

import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.input.{Reader, StreamReader}
//import RangeBuilder._

sealed trait ParsedId {
  val node : String
  def range : RangeBuilder.Builder
  //def range : NodeId => Range
}
case class SetIdOrScope(node:String) extends ParsedId {
  def range = RangeBuilder.Scope
}
case class PElement(node:String)extends ParsedId {
  def range = RangeBuilder.Element
}

object ConstraintsPlParser{
  def apply(nodesByName : Map[String, NodeId]) =
    new ConstraintsPlParser(nodesByName)

  class ConstraintsMapBuilderImp {

    var constraintsMap = ConstraintsMaps()

    def setDefs(defs : Map[String, NamedRangeSet]): Unit = {
      constraintsMap = constraintsMap.copy(namedSets =  defs)
    }

    def addHideConstraint(owners : RangeSet,
                          facades : RangeSet,
                          interlopers : RangeSet,
                          friends : RangeSet) = {
      val ct = Constraint(owners, facades, interlopers, friends)

      val hideConstraintsMap = owners.foldLeft(constraintsMap.hideConstraintsMap){
        case (map, owner) =>
          val s = map.getOrElse(owner, new ConstraintSet())
          map + (owner -> (s + ct) )
      }
      constraintsMap = constraintsMap.copy(hideConstraintsMap = hideConstraintsMap)
    }

    def addFriendConstraint( friends : RangeSet,
                             befriended : RangeSet) = {
      val ct = Constraint(befriended, RangeSet.empty, RangeSet.empty, friends)

      val friendCtsMap = befriended.foldLeft(constraintsMap.friendConstraintsMap){
        case (map, owner) =>
          val s = map.getOrElse(owner, new ConstraintSet())
          map + (owner -> (s + ct) )
      }

      constraintsMap = constraintsMap.copy(friendConstraintsMap = friendCtsMap)
    }
  }

}
class ConstraintsPlParser private
( nodesByName : Map[String, NodeId]) extends RegexParsers {

  val builder = new ConstraintsPlParser.ConstraintsMapBuilderImp()

  protected override val whiteSpace = """(\s|%.*|#.*)+""".r  //to skip comments


  var defs : Map[String, NamedRangeSet] = Map()


  var imports : Seq[String] = Seq("")

  def findNode(k : ParsedId) : Seq[Range] ={

    def aux(imports : Seq[String], acc : Seq[Range]) : Seq[Range] =
      imports match {
        case Seq() if acc.isEmpty => throw new NoSuchElementException("named element " + k + " not found")
        case Seq() => acc
        case _ =>
          nodesByName get (imports.head + k.node) match {
            case None => aux(imports.tail, acc)
            case Some(n) => aux(imports.tail, k.range(n) +: acc)
          }
      }

    aux(imports, Seq())
  }

  def toDef (request : Either[ParsedId, Seq[ParsedId]]) : RangeSet = {
    request match {
      case Left(key) => defs get key.node match {
        case Some(l) => l
        case None => LiteralRangeSet(findNode(key))
      }
      case Right(l) => LiteralRangeSet(l flatMap findNode)
    }
  }

  def ident : Parser[String] = (
    """[^\[\]\s',().]+""".r
      | "'" ~> """[^\s']+""".r <~ "'"
    )

  def range : Parser[ParsedId] =(
     "r:" ~> ident ^^ (PElement(_))
    | ident ^^ (SetIdOrScope(_))
  )
  def rangeList : Parser[List[ParsedId]] = (
    "[" ~> repsep(range, ",") <~ "]"     //rep1sep ??
      | "r:[" ~> repsep(range, ",") <~ "]" ^^ { _.map(e => PElement(e.node))}
    )
  def rangeListOrRange : Parser[Either[ParsedId, List[ParsedId]]] = (
      rangeList  ^^ (Right(_))
      | range  ^^ (Left(_))
    )

  def list : Parser[List[String]] = "[" ~> repsep(ident, ",") <~ "]"     //rep1sep ??

  /*def listOrIdent : Parser[Either[String, List[String]]] = (
      list  ^^ (Right(_))
      | ident  ^^ (Left(_))
    )
*/
  def java_import : Parser[Unit] =
    "java_import(" ~> list <~ ")." ^^ { l : List[String] =>
      imports = l.foldLeft(imports) {case (acc, str) => (str +".") +: acc}
    }

  def declare_set : Parser[Unit] =
    "declareSet(" ~> ident ~ "," ~ rangeList <~ ")." ^^ {
      case ident ~ _ ~ list => defs get ident match {
        case Some(_) => throw new scala.Error("Set " + ident + " already defined")
        case None => defs += (ident -> new NamedRangeSet(ident,
          LiteralRangeSet(list flatMap findNode)))
      }
    }

  def declare_set_union : Parser[Unit] = {

    def normal(list : Seq[RangeSet]) = {

      val (namedSets, lit) = list.foldLeft(( Seq[RangeSet](),LiteralRangeSet())){
        case ((nsAcc, litAcc), l : LiteralRangeSet) => (nsAcc, litAcc ++ l)
        case ((nsAcc, litAcc), ns) => (ns +: nsAcc, litAcc)
      }

      new RangeSetUnion(namedSets, lit)
    }

    "declareSetUnion(" ~> ident ~ "," ~ list <~ ")." ^^ {
      case ident ~ _ ~ list => defs get ident match {
        case Some(_) => throw new scala.Error("Set " + ident + " already defined")
        case None =>
          defs += (ident -> new NamedRangeSetUnion(ident, normal(list map defs)))
      }
    }
  }



  def hideEnd(rs : RangeSet) : Parser[Unit] = ("," ~>
    rangeListOrRange ~ "," ~
    rangeListOrRange ~ "," ~
    rangeListOrRange <~ ")." ^^ {
    case facades ~ _ ~ interlopers ~ _ ~ friends =>
      builder.addHideConstraint(rs, toDef(facades), toDef(interlopers), toDef(friends))
  }
    | ")." ^^ { case s =>
    builder.addHideConstraint(rs, LiteralRangeSet(),
      LiteralRangeSet(Scope(DependencyGraph.rootId)), LiteralRangeSet())})


  def hide : Parser[Unit] =
    "hide(" ~> range >> { r => hideEnd(LiteralRangeSet(findNode(r)))}

  def hideSet : Parser[Unit] =
    "hideSet(" ~> rangeListOrRange >> { s => hideEnd(toDef(s))}


  type Add2ArgsHideConstraint = (RangeSet, RangeSet) => Unit

  def addHideFromConstraint : Add2ArgsHideConstraint =
    (owner, interlopers) =>
      builder.addHideConstraint(owner,
        LiteralRangeSet(), interlopers, LiteralRangeSet())

  def addHideButFromConstraint : Add2ArgsHideConstraint =
    (owner, friends) =>
      builder.addHideConstraint(owner, LiteralRangeSet(),
        LiteralRangeSet(Scope(DependencyGraph.rootId)), friends)


  def hide2ArgsEnd(add : Add2ArgsHideConstraint)(rs : RangeSet) : Parser[Unit] =
    "," ~> rangeListOrRange <~ ")." ^^ { rl => add(rs, toDef(rl))}

  def hideFrom : Parser[Unit] =
    "hideFrom(" ~> range >> {r =>
      hide2ArgsEnd(addHideFromConstraint)(LiteralRangeSet(findNode(r)))
    }

  def hideSetFrom : Parser[Unit] =
    "hideSetFrom(" ~> rangeListOrRange >> {s =>
      hide2ArgsEnd(addHideFromConstraint)(toDef(s))
    }

  def hideButFrom : Parser[Unit] =
    "hideButFrom(" ~> range >> { r =>
      hide2ArgsEnd(addHideButFromConstraint)(LiteralRangeSet(findNode(r)))
    }

  def hideSetButFrom : Parser[Unit] =
    "hideSetButFrom(" ~> rangeListOrRange >> {s =>
      hide2ArgsEnd(addHideButFromConstraint)(toDef(s))
    }

  def hideFromEachOther : Parser[Unit] = {
    "hideFromEachOther(" ~> rangeListOrRange <~ ")." ^^ {
      case s =>
        val owners = toDef(s)
        builder.addHideConstraint(owners, LiteralRangeSet(),
          owners, LiteralRangeSet())
    }
  }

  def friend : Parser[Unit] =
    "friendOf(" ~> rangeListOrRange ~ "," ~ rangeListOrRange <~ ")." ^^ {
      case friends ~ _ ~ befriended =>
        builder.addFriendConstraint(toDef(friends), toDef(befriended))
    }

  def constraints : Parser[Unit] = {
    ( java_import
      | declare_set
      | declare_set_union
      | hide
      | hideFrom
      | hideButFrom
      | hideSet
      | hideSetFrom
      | hideSetButFrom
      | hideFromEachOther
      | friend
      )}

  /*def apply(input : java.io.Reader) = parseAll(constraints, input) match{
    case Success(result, _ ) => result
    case failure : NoSuccess => throw new scala.Error(failure.msg)
  }*/

  def apply(input : java.io.Reader) = {
    def aux(input : Reader[Char]) : Unit = {
      parse(constraints, input) match {
        case Success(_, i) => aux(i)
        case Error(msg, next) =>
          throw new scala.Error("!!! Error !!! at position " + next.pos + " " + msg)
        case Failure(msg, next) =>
          if (next.atEnd) ()
          else throw new scala.Error("Failure at position " + next.pos + " " + msg)
      }
    }
    aux(StreamReader(input))
    builder.setDefs(defs)
  }
}
