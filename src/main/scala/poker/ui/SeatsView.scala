package poker.ui

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import poker.model.Player

final class SeatsView extends VBox {
  setSpacing(6)
  setPadding(new Insets(10))

  def update(players: Vector[Player], buttonIndex: Int, currentPlayerId: Option[Int]): Unit = {
    getChildren.clear()
    players.zipWithIndex.foreach { case (player, idx) =>
      val label = new Label(formatPlayer(player, idx == buttonIndex, currentPlayerId.contains(player.id)))
      label.getStyleClass.add("seat-label")
      val baseStyle = "-fx-padding: 4px; -fx-background-color: #333; -fx-text-fill: white;"
      val highlight = if (currentPlayerId.contains(player.id)) "-fx-border-color: #ffd700; -fx-border-width: 2px;" else ""
      label.setStyle(baseStyle + highlight)
      getChildren.add(label)
    }
  }

  private def formatPlayer(player: Player, isButton: Boolean, isActive: Boolean): String = {
    val role = if (isButton) "(BTN)" else ""
    val status = player.status.toString
    val cards = if (player.holeCards.nonEmpty) player.holeCards.map(_.toString).mkString(" ") else "[?? ??]"
    val actionMark = if (isActive) "*" else ""
    s"${player.name} $role $actionMark - Stack: ${player.stack} - Bet: ${player.bet} - ${status} - ${cards}"
  }
}
