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

package puck.graph.comparison

import puck.graph.DGEdge
import puck.graph.transformations._
import puck.util.{PuckLog, PuckLogger}

object FilterNoise {

  implicit val defaultVerbosity = (PuckLog.NoSpecialContext, PuckLog.Debug)

/*  private def select[T](l : List[T], pred : T => Boolean) : (Option[T], List[T]) = {
    def aux(l1 : List[T], acc : List[T]) : (Option[T], List[T]) =
      if(l1.isEmpty) (None, l)
      else if(pred(l1.head)) (Some(l1.head), acc reverse_::: l1.tail)
      else aux(l1.tail, l1.head :: acc)

    aux(l, List[T]())
  }*/

  def writeRule(logger : PuckLogger)(name : => String, op1 : => String, op2 : => String, res : => String ) : Unit = {
    logger.writeln(name +" : ")
    logger.writeln(op1)
    logger.writeln("+ " + op2)
    logger.writeln("= " + res)
    logger.writeln("----------------------------------------------")
  }

  def mapUntil( stoppingEdge : DGEdge,
                transfos : List[Transformation],
                rule : (Transformation => Option[Transformation])): Seq[Transformation] = {

    def aux(acc : Seq[Transformation], l : Seq[Transformation]) : Seq[Transformation] ={
      if(l.isEmpty) acc.reverse
      else
        l.head match {
          case Transformation(_, RedirectionOp(`stoppingEdge`, _)) =>
            RecordingComparator.revAppend(acc, l)
          case _ => rule(l.head) match {
            case None => aux(acc, l.tail)
            case Some(t) => aux(t +: acc, l.tail)
          }
        }
    }
    aux(List(), transfos)
  }

  def xRedRule(logger : PuckLogger, red: RedirectionOp) : Transformation => Option[Transformation] = {
    val kind = red.edge.kind
    val n1 = red.edge.source
    val n2 = red.edge.target
    red.extremity match {
      case Target(n3) => {
        case op2@Transformation(Regular, RedirectionOp(e @ DGEdge(`kind`, `n1`, n0), Target(`n2`))) =>
          val res = Transformation(Regular, RedirectionOp(kind(n1, n0), Target(n3)))
          writeRule(logger)("RedRed_tgt", op2.toString, redTransfo(red).toString, res.toString)
          Some(res)
        case op2@Transformation(Regular, Edge(e @ DGEdge(`kind`, `n1`, `n2`))) =>
          val res = if(red.withMerge) None
          else Some(Transformation(Regular, Edge(kind(n1, n3))))
          writeRule(logger)("AddRed_tgt", op2.toString, redTransfo(red).toString, res.toString)
          res
        case t => Some(t)
      }
      case Source(n3) => {
        case op2@Transformation(Regular, RedirectionOp(e @ DGEdge(`kind`, n0, `n2`), Source(`n1`))) =>
          val res = Transformation(Regular, RedirectionOp(kind(n0, n2), Source(n3)))
          writeRule(logger)("RedRed_src", op2.toString, redTransfo(red).toString, res.toString)
          Some(res)
        case op2@Transformation(Regular, Edge(e @ DGEdge(`kind`, `n1`, `n2`))) =>
          val res = Transformation(Regular, Edge(kind(n3, n2)))
          writeRule(logger)("AddRed_src", op2.toString, redTransfo(red).toString, res.toString)
          Some(res)
        case t => Some(t)
      }
    }
  }

  def redTransfo : RedirectionOp => Transformation = Transformation(Regular, _)

/*
  def apply(transfos : Seq[Transformation], logger : PuckLogger): Seq[Transformation] = {
    val ts = filter(transfos, logger)
    if(ts.length == transfos.length) ts
    else apply(ts, logger)
  }
*/

  def apply(transfos : Seq[Transformation], logger : PuckLogger): Seq[Transformation] ={

    // We are going backwards !
    def aux(filteredTransfos : Seq[Transformation],
            l : Seq[Transformation],
            removedEdges : Seq[DGEdge]): (Seq[Transformation], Seq[DGEdge]) = {
      l match {
        case List() => (filteredTransfos, removedEdges)
/*        case (op2 @ Transformation(Remove, TTEdge(DGEdge(Contains, n1, n2)))) :: tl =>
          select[Transformation](tl,
          { case Transformation(Add, TTEdge(DGEdge(Contains, `n1`, `n2`))) => true
          case _ => false
          }) match {
            case (Some( op1 ), newTl) =>
              writeRule(logger)("AddDel", op1.toString, op2.toString, "None")
              aux(filteredTransfos, newTl, removedEdges)
            case (None, _) => aux(l.head +: filteredTransfos, l.tail, removedEdges)
          }*/

        case (op1 @ Transformation(Regular, red @ RedirectionOp(stopingEdge, _))) :: tl =>
          val transfos = mapUntil(stopingEdge, tl, xRedRule(logger, red))
          aux(filteredTransfos, transfos, removedEdges)


        case (t @ Transformation(Regular, Edge(_))):: tl => aux(t +: filteredTransfos, tl, removedEdges)
        case (Transformation(Reverse, Edge(e))):: tl => aux(filteredTransfos, tl, e +: removedEdges)

        case hd :: _ => sys.error(hd  + " should not be in list")
      }
    }

    val (normalTransfos, removedEdges) = aux(Seq(), transfos, Seq())

    removedEdges.foldLeft(normalTransfos){(normalTransfos0, e) =>
      logger.writeln(s"$e was removed filtering:")
      normalTransfos0 filter {
        case Transformation(Regular, Edge(`e`)) =>
          logger.writeln(s"add found")
          false
        case _ => true
      }
    }

  }
}

