package poker.ui

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, TextArea}
import javafx.scene.layout.{BorderPane, HBox, Pane, StackPane, VBox}
import javafx.scene.shape.Circle
import poker.model.{GameEvent, Player, PlayerStatus, PotState, Street}

import scala.collection.mutable
import scala.collection.immutable.Set

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

  private case class SeatNode(container: VBox, nameLabel: Label, stackLabel: Label, cardsBox: HBox, statusLabel: Label)

  private val seatNodes = mutable.ArrayBuffer.empty[SeatNode]
  private val redSuits: Set[Char] = Set('\u2665', '\u2666', 'H', 'D')
  private val blackSuits: Set[Char] = Set('\u2663', '\u2660', 'C', 'S')
  private val placeholderCardText: String = "--"
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
        val statusText = player.status match {
          case PlayerStatus.Active   => if (player.bet > 0) s"Mise: ${player.bet}" else "Pret"
          case PlayerStatus.AllIn    => "All-in"
          case PlayerStatus.Folded   => "Fold"
          case PlayerStatus.Eliminated => "Elimine"
        }

        node.nameLabel.setText(formatName(player, idx == buttonIndex))
        node.stackLabel.setText(s"Stack: ${player.stack}")
        node.statusLabel.setText(statusText)
        renderSeatCards(node.cardsBox, player)

        val classes = node.container.getStyleClass
        classes.setAll("seat-node")
        if (idx == buttonIndex) classes.add("button-seat")
        if (currentPlayerId.contains(player.id)) classes.add("active-seat")
        if (player.status == PlayerStatus.Folded || player.status == PlayerStatus.Eliminated) classes.add("folded-seat")

        node.container.setVisible(true)
      } else {
        node.container.setVisible(false)
        node.cardsBox.getChildren.clear()
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
        val cards = new HBox(6)
        cards.setAlignment(Pos.CENTER)
        cards.getStyleClass.add("seat-cards")

        val status = new Label()
        status.getStyleClass.add("seat-status")

        val container = new VBox(4, name, stack, cards, status)
        container.getStyleClass.add("seat-node")
        container.setManaged(false)
        seatLayer.getChildren.add(container)
        seatNodes += SeatNode(container, name, stack, cards, status)
      }
    }
  }

  private def renderSeatCards(box: HBox, player: Player): Unit = {
    box.getChildren.clear()
    val cardStrings = player.holeCards.map(_.toString)
    val expected = math.max(cardStrings.size, 2)
    (0 until expected).foreach { idx =>
      val node =
        if (idx < cardStrings.size) createSeatCard(cardStrings(idx))
        else placeholderCard()
      box.getChildren.add(node)
    }
    box.setVisible(true)
    box.setManaged(true)
  }

  private def createSeatCard(card: String): Label = {
    val label = new Label(card)
    label.getStyleClass.add("seat-card")
    suitClass(card).foreach(label.getStyleClass.add)
    label
  }

  private def placeholderCard(): Label = {
    val label = new Label(placeholderCardText)
    label.getStyleClass.add("seat-card")
    label.getStyleClass.add("seat-card-placeholder")
    label
  }

  private def suitClass(card: String): Option[String] = {
    card.reverseIterator.collectFirst {
      case ch if redSuits.contains(ch)   => "card-red"
      case ch if blackSuits.contains(ch) => "card-black"
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

