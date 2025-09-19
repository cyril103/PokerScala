package poker.ui

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, TextArea}
import javafx.scene.layout.{BorderPane, Pane, StackPane, VBox}
import javafx.scene.shape.Circle
import poker.model.{GameEvent, Player, PlayerStatus, PotState, Street}

import scala.collection.mutable

final class TableView extends BorderPane {
  private val tableRoot = new StackPane()
  private val tableCircle = new Circle()
  tableCircle.getStyleClass.add("table-circle")
  tableCircle.centerXProperty().bind(tableRoot.widthProperty().divide(2))
  tableCircle.centerYProperty().bind(tableRoot.heightProperty().divide(2))

  private val seatLayer = new Pane()
  seatLayer.setPickOnBounds(false)
  seatLayer.prefWidthProperty().bind(tableRoot.widthProperty())
  seatLayer.prefHeightProperty().bind(tableRoot.heightProperty())

  private val cardsView = new CardsView
  StackPane.setAlignment(cardsView, Pos.CENTER)

  private val streetLabel = new Label("Street: PreFlop")
  private val potLabel = new Label("Pot: 0")
  private val overlay = new VBox(4, streetLabel, potLabel)
  overlay.getStyleClass.add("table-overlay")
  StackPane.setAlignment(overlay, Pos.TOP_CENTER)

  private val logArea = new TextArea()
  logArea.setEditable(false)
  logArea.setWrapText(true)
  logArea.setPrefRowCount(6)
  logArea.getStyleClass.add("table-log")
  setBottom(logArea)

  private case class SeatNode(container: VBox, nameLabel: Label, stackLabel: Label, statusLabel: Label)

  private val seatNodes = mutable.ArrayBuffer.empty[SeatNode]
  private var playersSnapshot: Vector[Player] = Vector.empty
  private var buttonIndex: Int = 0
  private var currentPlayerId: Option[Int] = None

  tableRoot.getChildren.addAll(tableCircle, cardsView, seatLayer, overlay)
  setCenter(tableRoot)
  BorderPane.setAlignment(tableRoot, Pos.CENTER)

  tableRoot.widthProperty().addListener((_, _, _) => layoutSeats())
  tableRoot.heightProperty().addListener((_, _, _) => layoutSeats())

  def updatePlayers(players: Vector[Player], buttonIndex: Int, currentPlayerId: Option[Int]): Unit = {
    playersSnapshot = players
    this.buttonIndex = buttonIndex
    this.currentPlayerId = currentPlayerId

    ensureSeatCount(players.size)

    seatNodes.zipWithIndex.foreach { case (node, idx) =>
      if (idx < players.size) {
        val player = players(idx)
        val cards = if (player.holeCards.nonEmpty) player.holeCards.map(_.toString).mkString(" ") else "··"
        val statusText = player.status match {
          case PlayerStatus.Active   => if (player.bet > 0) s"Mise: ${player.bet}" else "Prêt"
          case PlayerStatus.AllIn    => "All-in"
          case PlayerStatus.Folded   => "Fold"
          case PlayerStatus.Eliminated => "Eliminé"
        }

        node.nameLabel.setText(formatName(player, idx == buttonIndex))
        node.stackLabel.setText(s"Stack: ${player.stack}")
        node.statusLabel.setText(s"${statusText}  ${cards}")

        val classes = node.container.getStyleClass
        classes.setAll("seat-node")
        if (idx == buttonIndex) classes.add("button-seat")
        if (currentPlayerId.contains(player.id)) classes.add("active-seat")
        if (player.status == PlayerStatus.Folded || player.status == PlayerStatus.Eliminated) classes.add("folded-seat")

        node.container.setVisible(true)
      } else {
        node.container.setVisible(false)
      }
    }

    layoutSeats()
  }

  def updateBoard(cards: Vector[String]): Unit = cardsView.update(cards)

  def updatePot(pot: PotState): Unit = potLabel.setText(s"Pot: ${pot.total}")

  def updateStreet(street: Street): Unit = streetLabel.setText(s"Street: ${street.toString}")

  def appendLog(event: GameEvent): Unit = {
    val line = event match {
      case GameEvent.PlayerActed(playerId, action) => s"Joueur $playerId ➔ $action"
      case GameEvent.HandStarted(handId)           => s"Main $handId démarrée"
      case GameEvent.StreetAdvanced(next)          => s"Nouvelle street : ${next.toString}"
      case GameEvent.Message(text)                 => text
      case GameEvent.Showdown(results)             => s"Showdown : ${results.map(r => s"${r.playerId} gagne ${r.share}").mkString(", ")}"
      case GameEvent.PotUpdated(potState)          => s"Pot total : ${potState.total}"
    }
    logArea.appendText(line + System.lineSeparator())
  }

  private def ensureSeatCount(required: Int): Unit = {
    if (seatNodes.size < required) {
      val missing = required - seatNodes.size
      (0 until missing).foreach { _ =>
        val name = new Label()
        name.getStyleClass.add("seat-name")
        val stack = new Label()
        stack.getStyleClass.add("seat-stack")
        val status = new Label()
        status.getStyleClass.add("seat-status")

        val container = new VBox(4, name, stack, status)
        container.getStyleClass.add("seat-node")
        container.setManaged(false)
        seatLayer.getChildren.add(container)
        seatNodes += SeatNode(container, name, stack, status)
      }
    }
  }

  private def layoutSeats(): Unit = {
    if (playersSnapshot.isEmpty) return

    val width = tableRoot.getWidth
    val height = tableRoot.getHeight
    if (width <= 0 || height <= 0) return

    val circleRadius = math.min(width, height) * 0.32
    tableCircle.setRadius(circleRadius)

    val centerX = width / 2
    val centerY = height / 2
    val seatingRadius = circleRadius + 110
    val totalSeats = playersSnapshot.size

    seatNodes.zipWithIndex.foreach { case (seat, idx) =>
      if (idx < totalSeats && seat.container.isVisible) {
        val angle = (math.Pi * 1.5) + (idx.toDouble / totalSeats) * math.Pi * 2
        seat.container.applyCss()
        seat.container.autosize()
        val nodeWidth = seat.container.getWidth
        val nodeHeight = seat.container.getHeight
        val x = centerX + seatingRadius * math.cos(angle) - nodeWidth / 2
        val y = centerY + seatingRadius * math.sin(angle) - nodeHeight / 2
        seat.container.relocate(x, y)
      }
    }
  }

  private def formatName(player: Player, isButton: Boolean): String = {
    val badge = if (isButton) " (BTN)" else ""
    s"${player.name}$badge"
  }
}


