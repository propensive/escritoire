/*
    Escritoire, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package escritoire

import rudiments.*
import vacuous.*
import gossamer.*
import hieroglyph.*
import spectacular.*
import anticipation.*

import language.experimental.pureFunctions

enum BoxLine:
  case None, Thin, Thick, Double

case class BoxDrawingCharacter(vertical: BoxLine, horizontal: BoxLine)

object BoxDrawing:
  val simpleChars: IArray[Char] = IArray(
      ' ', '─', '━', '═',
      '│', '┼', '┿', '╪',
      '┃', '╂', '╋', '═',
      '║', '╫', '║', '╬')
  
  def simple(vertical: BoxLine, horizontal: BoxLine): Char =
    simpleChars(vertical.ordinal*4 + horizontal.ordinal)
  
  private val box: IArray[Char] =
    List
      (t" ╴╸ ╷┐┑╕╻┒┓  ╖ ╗╶─╾ ┌┬┭ ┎┰┱ ╓╥╓ ╺╼━ ┍┮┯╕┏┲┳  ╖ ╗   ═╒ ╒╤   ═╔ ╔╦╵┘┙╛│┤┥╡╽┧┪╛    └┴┵ ├┼┽ ┟╁╅     ┕┶┷╛",
       t"┝┾┿╡┢╆╈╛    ╘ ╘╧╞ ╞╪╘ ╘╧    ╹┚┛ ╿┦┩╕┃┨┫  ╖ ╗┖┸┹ ┞╀╃ ┠╂╊ ╓╥╓ ┗┺┻ ┡╄╇╕┣ ╋  ╖ ╗   ═╒ ╒╤   ═╔ ╔╦ ╜ ╝    ",
       t" ╜ ╝║╢║╣╙╨╙     ╙╨╙ ╟╫╟  ╜ ╝     ╜ ╝║╢║╣╚ ╚╩    ╚ ╚╩╠ ╠╬").join.chars

  def apply(top: BoxLine, right: BoxLine, bottom: BoxLine, left: BoxLine): Char =
    box(top.ordinal + right.ordinal*4 + bottom.ordinal*16 + left.ordinal*64)

enum Breaks:
  case Never, Space, Zwsp, Character

enum Alignment:
  case Left, Right, Center

enum DelimitRows:
  case None, Rule, Space, SpaceIfMultiline, RuleIfMultiline

object ColumnAlignment:
  val left: ColumnAlignment[Any] = () => Alignment.Left

  given byte: ColumnAlignment[Byte] = () => Alignment.Right
  given short: ColumnAlignment[Short] = () => Alignment.Right
  given int: ColumnAlignment[Int] = () => Alignment.Right
  given long: ColumnAlignment[Long] = () => Alignment.Right
  given text: ColumnAlignment[Text] = () => Alignment.Left

trait ColumnAlignment[-ColumnType]:
  def alignment(): Alignment

object Column:

  def apply[RowType, CellType, TextType]
      (title:  TextType,
       width:  Optional[Int]       = Unset,
       align:  Optional[Alignment] = Unset,
       breaks: Breaks              = Breaks.Space,
       hide:   Boolean             = false,
       sizing: ColumnSizing        = columnSizing.Prose)
      (get: RowType -> CellType)
      (using textual: Textual[TextType], columnAlignment: ColumnAlignment[CellType] = ColumnAlignment.left)
      (using textual.ShowType[CellType])
          : Column[RowType, TextType] =

    def contents(row: RowType): TextType = textual.show(get(row))
    
    Column(title, contents, breaks, align.or(columnAlignment.alignment()), width, hide, sizing)

case class Column[RowType, TextType: Textual]
    (title:  TextType,
     get:    RowType => TextType,
     breaks: Breaks,
     align:  Alignment,
     width:  Optional[Int],
     hide:   Boolean,
     sizing: ColumnSizing)

object Table:
  @targetName("make")
  def apply[RowType](using classTag: ClassTag[RowType])[TextType: ClassTag: Textual]
      (initColumns: Column[RowType, TextType]*)
          : Table[RowType, TextType] =

    new Table(initColumns*)

abstract class Tabulation[TextType: ClassTag]():
  type Row
  
  def columns: IArray[Column[Row, TextType]]
  def titles: Seq[IArray[IArray[TextType]]]
  def rows: Seq[IArray[IArray[TextType]]]
  def dataLength: Int

  def layout(width: Int)(using style: TableStyle, metrics: TextMetrics, textual: Textual[TextType])
          : TableLayout[TextType] =
    
    case class Layout(slack: Double, indices: IArray[Int], widths: IArray[Int], totalWidth: Int):
      lazy val columnWidths: IArray[(Int, Column[Row, TextType], Int)] = IArray.from:
        indices.indices.map: index =>
          val columnIndex = indices(index)
          (columnIndex, columns(columnIndex), widths(index))

    def layout(slack: Double): Layout =
      val widths: IndexedSeq[Optional[Int]] = columns.indices.map: index =>
        val dataMax = rows.map: cells =>
          columns(index).sizing.width[TextType](cells(index), width, slack).or(0)
        .max

        val titleMax = titles.map: cells =>
          columns(index).sizing.width[TextType](cells(index), width, slack).or(0)
        .max
      
        dataMax.max(titleMax)
      
      val indices: IndexedSeq[Int] = widths.indices.map { index => widths(index).let(index.waive) }.compact
      val totalWidth = widths.sumBy(_.or(0)) + style.cost(indices.length)
    
      Layout(slack, IArray.from(indices), IArray.from(widths.compact), totalWidth)

    def bisect(min: Layout, max: Layout, countdown: Int): (Layout, Layout) =
      if countdown == 0 then (min, max)
      else if max.totalWidth - min.totalWidth <= 1 then (min, max) else
        val point = layout((min.slack + max.slack)/2)
        
        if point.totalWidth == width then (point, point)
        else if point.totalWidth > width then bisect(min, point, countdown - 1)
        else bisect(point, max, countdown - 1)
      
    val rowLayout = bisect(layout(0), layout(1), 10)(0)

    // We may be able to increase the slack in some of the remaining columns

    def lines(data: Seq[IArray[IArray[TextType]]]): LazyList[TableRow[TextType]] =
      data.to(LazyList).map: cells =>
        val tableCells = rowLayout.columnWidths.map: (index, column, width) =>
          val lines = column.sizing.fit(cells(index), width)
          TableCell(width, 1, lines, lines.length)
        
        val height = tableCells.maxBy(_.minHeight).minHeight

        TableRow(tableCells, false, height)
    
    val widths = rowLayout.columnWidths.map(_(2))

    TableLayout(List(TableSection(widths, lines(titles)), TableSection(widths, lines(rows))))

case class TableCell[TextType](width: Int, span: Int, lines: IndexedSeq[TextType], minHeight: Int):
  def apply(line: Int): TextType = lines(line)

case class TableRow[TextType](cells: IArray[TableCell[TextType]], title: Boolean, height: Int):
  def apply(column: Int): TableCell[TextType] = cells(column)

case class TableSection[TextType](widths: IArray[Int], rows: LazyList[TableRow[TextType]])
case class TableLayout[TextType](sections: List[TableSection[TextType]]):

  def render(using metrics: TextMetrics, textual: Textual[TextType], style: TableStyle): LazyList[TextType] =
    val leftEdge = textual.make(t"${style.left} ".s)
    val rightEdge = textual.make(t" ${style.right}".s)
    val midEdge = textual.make(t" ${style.separator} ".s)
    
    def recur(widths: IArray[Int], rows: LazyList[TableRow[TextType]]): LazyList[TextType] =
      rows match
        case row #:: tail =>
          val lines = (0 until row.height).map: lineNumber =>
            widths.indices.map: index =>
              val cell = row(index)
              if cell.minHeight > lineNumber then cell(lineNumber).pad(widths(index))
              else textual.make((t" "*widths(index)).s)
            .join(leftEdge, midEdge, rightEdge)
          
          lines.to(LazyList) #::: recur(widths, tail)
        
        case _ =>
          LazyList()
    
    val line1 = sections.head.widths.to(List).map: width =>
      textual.make(style.topBar.s*(width + style.padding.length*2))
    .join
      (textual.make(style.topLeft.s),
       textual.make(style.topSeparator.s),
       textual.make(style.topRight.s))
      
    val rule = sections.head.widths.to(List).map: width =>
      textual.make(style.midBar.s*(width + style.padding.length*2))
    .join
      (textual.make(style.midLeft.s),
        textual.make(style.midSeparator.s),
        textual.make(style.midRight.s))

    val lastLine = sections.head.widths.to(List).map: width =>
      textual.make(style.bottomBar.s*(width + style.padding.length*2))
    .join
      (textual.make(style.bottomLeft.s),
       textual.make(style.bottomSeparator.s),
       textual.make(style.bottomRight.s))
      
    val body = sections.to(LazyList).flatMap: section =>
      rule #:: recur(section.widths, section.rows)

    line1 #:: body.tail #::: LazyList(lastLine)
      

case class Table[RowType: ClassTag, TextType: ClassTag](initColumns: Column[RowType, TextType]*)
    (using textual: Textual[TextType]):
  table =>
  
  val columns: IArray[Column[RowType, TextType]] = IArray.from(initColumns.filterNot(_.hide))
  val titles: Seq[IArray[IArray[TextType]]] = Seq(IArray.from(columns.map(_.title.cut(t"\n"))))

  def tabulate(data: Seq[RowType]): Tabulation[TextType] { type Row = RowType } = new Tabulation[TextType]:
    type Row = RowType

    val columns: IArray[Column[Row, TextType]] = table.columns
    val titles: Seq[IArray[IArray[TextType]]] = table.titles
    val dataLength: Int = data.length
    val rows: Seq[IArray[IArray[TextType]]] = data.map { row => columns.map(_.get(row).cut(t"\n")) }
