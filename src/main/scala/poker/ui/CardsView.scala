package poker.ui

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.Label
import javafx.scene.layout.HBox

import scala.collection.immutable.Set

final class CardsView extends HBox {
  setSpacing(10)
  setPadding(new Insets(12))
  setAlignment(Pos.CENTER)
  getStyleClass.add("board-container")

  def update(cards: Vector[String]): Unit = {
    getChildren.clear()
    cards.foreach { card =>
      val label = new Label(card)
      label.getStyleClass.add("board-card")
      suitClass(card).foreach(label.getStyleClass.add)
      getChildren.add(label)
    }
  }

  private val redSuits: Set[Char] = Set('\u2665', '\u2666', 'H', 'D')
  private val blackSuits: Set[Char] = Set('\u2663', '\u2660', 'C', 'S')

  private def suitClass(card: String): Option[String] = {
    card.reverseIterator.collectFirst {
      case ch if redSuits.contains(ch)   => "card-red"
      case ch if blackSuits.contains(ch) => "card-black"
    }
  }
}
