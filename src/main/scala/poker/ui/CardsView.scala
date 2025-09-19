package poker.ui

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.Label
import javafx.scene.layout.HBox

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
      getChildren.add(label)
    }
  }
}
