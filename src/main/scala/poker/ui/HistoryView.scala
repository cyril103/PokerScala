package poker.ui

import javafx.scene.control.{Label, ScrollPane}
import javafx.scene.layout.{HBox, VBox}
import poker.model.HandHistory

class HistoryView extends ScrollPane {
  private val historyContainer = new VBox(10)
  historyContainer.getStyleClass.add("history-view")
  setContent(historyContainer)
  setFitToWidth(true)

  def addHandHistory(history: HandHistory): Unit = {
    val handBox = new VBox(5)
    handBox.getStyleClass.add("history-hand")

    val idLabel = new Label(s"Hand #${history.handId}")
    idLabel.getStyleClass.add("history-hand-id")

    val boardLabel = new Label(s"Board: ${history.board.mkString(" ")}")
    boardLabel.getStyleClass.add("history-board")

    val playersBox = new VBox(2)
    history.players.foreach { playerHand =>
      val playerLabel = new Label(s"${playerHand.name}: ${playerHand.holeCards.mkString(" ")}")
      playerLabel.getStyleClass.add("history-player-hand")
      playersBox.getChildren.add(playerLabel)
    }

    handBox.getChildren.addAll(idLabel, boardLabel, playersBox)
    historyContainer.getChildren.add(0, handBox) // Add to top
  }
}
