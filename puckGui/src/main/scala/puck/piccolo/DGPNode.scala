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

package puck.piccolo

import org.piccolo2d.PNode
import org.piccolo2d.util.PBounds
import puck.graph.NodeId


/**
  * Created by Loïc Girault on 23/05/16.
  */
object DGPNode {
  def unapply(arg: DGPNode): Some[NodeId] = Some(arg.id)
  type T = PNode with DGPNode

  implicit def toPNode(n : T) : PNode = n.asInstanceOf[PNode]

  val ATTRIBUTE_CONTAINER : String = "container"

}
trait DGPNode {
  this : PNode =>
  def id : NodeId

  def addContent(child : DGPNode) : Unit
  def rmContent(child : DGPNode) : Unit
  def content : Iterable[DGPNode]
  def contentSize : Int
  def clearContent() : Unit

  private def fullContent(acc : List[DGPNode]) : List[DGPNode] =
    this.content.foldLeft(acc){
      (acc, n) => n :: n.fullContent(acc)
    }

  def fullContent : List[DGPNode] = fullContent(List[DGPNode]())
  def toPNode : PNode with DGPNode = this

  //global bounds used by edges as referential for source and target coordinates
  //e.g : title node global bounds in the case of the expandable node
  def arrowGlobalBounds : PBounds
}
