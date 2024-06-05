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
import gossamer.*
import vacuous.*
import anticipation.*

trait Columnar:
  def width[TextType: Textual](lines: IArray[TextType], maxWidth: Int, slack: Double): Optional[Int]

  def fit[TextType: Textual](lines: IArray[TextType], width: Int, textAlign: TextAlignment)
          : IndexedSeq[TextType]

package columnar:
  object Prose extends Columnar:
    def width[TextType: Textual](lines: IArray[TextType], maxWidth: Int, slack: Double)
            : Optional[Int] =

      def longestWord(text: TextType, position: Int, lastStart: Int, max: Int): Int =
        if position < text.length then
          if TextType.unsafeChar(text, position) == ' '
          then longestWord(text, position + 1, position + 1, max.max(position - lastStart))
          else longestWord(text, position + 1, lastStart, max)
        else max.max(position - lastStart)

      val longestLine = lines.map(_.length).max
      lines.map(longestWord(_, 0, 0, 0)).max.max((slack*maxWidth).toInt).min(longestLine)

    def fit[TextType: Textual](lines: IArray[TextType], width: Int, textAlign: TextAlignment)
            : IndexedSeq[TextType] =

      def format(text: TextType, position: Int, lineStart: Int, lastSpace: Int, lines: List[TextType])
              : List[TextType] =

        if position < text.length then
          if TextType.unsafeChar(text, position) == ' '
          then format(text, position + 1, lineStart, position, lines)
          else
            if position - lineStart >= width
            then format(text, position + 1, lastSpace + 1, lastSpace, text.slice(lineStart, lastSpace) :: lines)
            else format(text, position + 1, lineStart, lastSpace, lines)
        else if lineStart == position then lines else text.slice(lineStart, position) :: lines

      lines.to(IndexedSeq).flatMap(format(_, 0, 0, 0, Nil).reverse)

  case class Fixed(fixedWidth: Int, ellipsis: Text = t"…") extends Columnar:
    def width[TextType: Textual](lines: IArray[TextType], maxWidth: Int, slack: Double): Optional[Int] =
      fixedWidth

    def fit[TextType: Textual](lines: IArray[TextType], width: Int, textAlign: TextAlignment)
            : IndexedSeq[TextType] =

      lines.to(IndexedSeq).map: line =>
        if line.length > width then line.take(width - ellipsis.length)+TextType(ellipsis) else line

  case class Shortened(fixedWidth: Int, ellipsis: Text = t"…") extends Columnar:
    def width[TextType: Textual](lines: IArray[TextType], maxWidth: Int, slack: Double): Optional[Int] =
      val naturalWidth = lines.map(_.length).max
      (maxWidth*slack).toInt.min(naturalWidth)

    def fit[TextType: Textual](lines: IArray[TextType], width: Int, textAlign: TextAlignment)
            : IndexedSeq[TextType] =

      lines.to(IndexedSeq).map: line =>
        if line.length > width then line.take(width - ellipsis.length)+TextType(ellipsis) else line

  case class Collapsible(threshold: Double) extends Columnar:
    def width[TextType: Textual](lines: IArray[TextType], maxWidth: Int, slack: Double): Optional[Int] =
      if slack > threshold then lines.map(_.length).max else Unset

    def fit[TextType: Textual](lines: IArray[TextType], width: Int, textAlign: TextAlignment)
            : IndexedSeq[TextType] =

      lines.to(IndexedSeq)
