package puck.graph

import _root_.java.io.BufferedWriter
import scala.None

/**
 * Created by lorilan on 07/05/14.
 */

trait DotHelper{
  def isDotSubgraph(k:NodeKind) : Boolean
  def isDotClass(k:NodeKind) : Boolean
  def fillColor(k:NodeKind) : String
  def namePrefix(k:NodeKind) : String
  def splitDotClassContent(n: AGNode) : (Iterable[AGNode], Iterable[AGNode], Iterable[AGNode] , Iterable[AGNode])
  //with java ((fields, Constructors, Methods), inner classes)
}

object DotPrinter {

  class Style(val line: String, val arrowHead : String)
  val isaStyle = new Style("dashed", "empty")
  val containsStyle = new Style("dashed", "open")
  val usesStyle = new Style("bold", "normal")

  class Status(val color : String, val thickness : String)
  val correctStatus = new Status("black", "1")
  val violationStatus = new Status("red", "5")


  def print(writer: BufferedWriter, graph : AccessGraph,
            helper : DotHelper, printId : Boolean){

    val idPrinter =
      if(printId) (id:Int) => " (" + id + ")"
      else (_:Int) => ""

    def writeln(str:String){
      writer write str
      writer newLine()
    }

    val violations = graph.violations
    /*
     * dot -Tpng give a wrong drawing of the graph when mixing nodes and arcs
     * in the dot file. We stock the arcs and print them separately at the end
     */
    val arcs = scala.collection.mutable.Buffer[String]()
    def printArc(style :Style, source:AGNode, target:AGNode,
                 status:Status){
      //val (lineStyle, headStyle) = style
      //val (color, thickness) = status
      //println("print arc "+ source.nameTypeString + " -> " + target.nameTypeString)
      def dotId(n: AGNode) : String =
        if(helper isDotSubgraph n.kind) n.id.toString
        else{
          val containerId = if(helper isDotClass n.kind) n.id
          else n.container.id
          containerId + ":" + n.id
        }


      def subGraphArc(n: AGNode, pos:String) =
        if(helper isDotSubgraph n.kind) pos + "=cluster" + n.id + ", "
        else ""

      /*writeln*/
      arcs += (dotId(source) + " -> " + dotId(target) + "[ " +
        subGraphArc(source, "ltail") +
        subGraphArc(target, "lhead") +
        "style = " + style.line + ", arrowhead = " + style.arrowHead +
        ", color = " + status.color + ", penwidth = " + status.thickness+ "];")

    }

    def printUse(source : AGNode, target : AGNode) =
      printArc(usesStyle, source, target,
        if(violations.contains(AGEdge.uses(source, target)))
          violationStatus
        else correctStatus )

    def decorate_name(n : AGNode):String =
        if (violations.contains(AGEdge.contains(n.container, n)))
        "<FONT COLOR=\"" + violationStatus.color + "\"><U>" + helper.namePrefix(n.kind)+ n.name + idPrinter(n.id) +"</U></FONT>"
        else helper.namePrefix(n.kind)+ n.name + idPrinter(n.id)

    def printNode(n:AGNode){
      if(helper isDotSubgraph n.kind) printSubGraph(n)
      else if(helper isDotClass n.kind) printClass(n)
    }

    def printSubGraph(n:AGNode){
      List("subgraph cluster" + n.id + " {",
        "label=\"" + decorate_name(n) +"\";",
        "color=black;") foreach writeln

      if(n.isContentEmpty) writeln(n.id + "[label=\"\" shape=none ]")
      else
        n.content.foreach(printNode)

      writeln("}")

      n.users.foreach(printUse(_, n))
    }

    def printClass(n:AGNode){

      def writeTableLine(n:AGNode){
        writeln("<TR><TD PORT=\"" +n.id + "\" ALIGN=\"LEFT\" BORDER=\"0\">"+
          decorate_name(n) +"</TD></TR>")
      }

      val (fields, ctrs, mts, innerClasses) = helper splitDotClassContent n

      writeln(n.id + " [ label = <<TABLE BGCOLOR=\"" + helper.fillColor(n.kind)+
        "\"> <TR> <TD PORT=\""+ n.id+"\" BORDER=\"0\"> <B>" +
        decorate_name(n) +" </B></TD></TR>")

      if(fields.nonEmpty || ctrs.nonEmpty || mts.nonEmpty) writeln("<HR/>")
      fields foreach writeTableLine
      if(fields.nonEmpty && ctrs.nonEmpty && mts.nonEmpty) writeln("<HR/>")
      ctrs foreach writeTableLine
      mts foreach writeTableLine

      writeln("</TABLE>>, shape = \"none\" ];")

      innerClasses foreach printClass

      n.content foreach{ nc =>
          nc.users.foreach(printUse(_, nc))
      }
      n.users.foreach(printUse(_, n))
      n.superTypes.foreach(printArc(isaStyle, n, _, correctStatus))
    }




    writeln("digraph G{")
    writeln("rankdir=LR; ranksep=equally; compound=true")

    graph.root.content.foreach(printNode)

    arcs.foreach(writeln)

    writeln("}")
    writer.close()

  }
}
