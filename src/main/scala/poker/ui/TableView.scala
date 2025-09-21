package poker.ui

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, TextArea}
import javafx.scene.layout.{BorderPane, HBox, Pane, StackPane, VBox}
import javafx.scene.shape.Ellipse
import javafx.scene.paint.{Color, RadialGradient, CycleMethod, Stop}
import poker.engine.Card
import poker.model.{GameEvent, Player, PlayerStatus, PotState, ShowdownResult, Street}

import scala.collection.mutable
import scala.collection.immutable.Set

final class TableView extends BorderPane {
  private val tableRoot = new StackPane()
  private val tableEllipse = new Ellipse()
  tableEllipse.getStyleClass.add("table-ellipse")
  tableEllipse.centerXProperty().bind(tableRoot.widthProperty().divide(2))
  tableEllipse.centerYProperty().bind(tableRoot.heightProperty().divide(2))

  private val chipsLayer = new Pane()
  chipsLayer.setPickOnBounds(false)
  chipsLayer.setMouseTransparent(true)
  chipsLayer.prefWidthProperty().bind(tableRoot.widthProperty())
  chipsLayer.prefHeightProperty().bind(tableRoot.heightProperty())

  private val seatLayer = new Pane()
  seatLayer.setPickOnBounds(false)
  seatLayer.prefWidthProperty().bind(tableRoot.widthProperty())
  seatLayer.prefHeightProperty().bind(tableRoot.heightProperty())

  private val cardsView = new CardsView
  StackPane.setAlignment(cardsView, Pos.CENTER)

  private val potChipsBox = new HBox(4)
  potChipsBox.getStyleClass.add("pot-chips")
  potChipsBox.setManaged(false)
  chipsLayer.getChildren.add(potChipsBox)

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

  private case class SeatNode(container: VBox, dealerBadge: Label, nameLabel: Label, stackLabel: Label, cardsBox: HBox, statusLabel: Label, chipsBox: HBox)

  private val seatNodes = mutable.ArrayBuffer.empty[SeatNode]
  private var revealedPlayerIds: Set[Int] = Set.empty
  private val redSuits: Set[Char] = Set('\u2665', '\u2666', 'H', 'D')
  private val blackSuits: Set[Char] = Set('\u2663', '\u2660', 'C', 'S')
  private val placeholderCardText: String = "--"
  private val hiddenCardText: String = "??"
  private var boardCards: Vector[Card] = Vector.empty
  private val chipDenominations: Vector[Int] = Vector(100, 25, 5, 1)

  private var playersSnapshot: Vector[Player] = Vector.empty
  private var buttonIndex: Int = 0
  private var currentPlayerId: Option[Int] = None

  tableRoot.getChildren.addAll(tableEllipse, chipsLayer, cardsView, seatLayer, overlay)
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

        val isButton = idx == buttonIndex

        node.dealerBadge.setVisible(isButton)
        node.dealerBadge.setManaged(isButton)
        node.nameLabel.setText(formatName(player, isButton))
        node.stackLabel.setText(s"Stack: ${player.stack}")
        node.statusLabel.setText(statusText)
        renderSeatCards(node.cardsBox, player)
        renderSeatChips(node.chipsBox, player.bet)

        val classes = node.container.getStyleClass
        classes.setAll("seat-node")
        if (isButton) classes.add("button-seat")
        if (currentPlayerId.contains(player.id)) classes.add("active-seat")
        if (player.status == PlayerStatus.Folded || player.status == PlayerStatus.Eliminated) classes.add("folded-seat")

        node.container.setVisible(true)
      } else {
        node.container.setVisible(false)
        node.cardsBox.getChildren.clear()
        node.dealerBadge.setVisible(false)
        node.dealerBadge.setManaged(false)
        node.chipsBox.setVisible(false)
        node.chipsBox.setManaged(false)
        node.chipsBox.getChildren.clear()
      }
    }

    layoutSeats()
  }

  def updateBoard(cards: Vector[String]): Unit = {
    cardsView.update(cards)
    boardCards = cards.flatMap(Card.parse)
  }

  def updatePot(pot: PotState): Unit = {
    potLabel.setText(s"Pot: ${pot.total}")
    renderPotChips(pot.total)
  }

  def updateStreet(street: Street): Unit = streetLabel.setText(s"Street: ${street.toString}")

  def appendLog(event: GameEvent): Unit = {
    event match {
      case GameEvent.HandStarted(_)    => revealedPlayerIds = Set.empty
      case GameEvent.Showdown(results) =>
        val nonZeroWinners = results.filter(_.share > 0).map(_.playerId)
        revealedPlayerIds = nonZeroWinners.toSet
      case _ => ()
    }

    val line = event match {
      case GameEvent.PlayerActed(playerId, action) => s"Joueur $playerId ➔ $action"
      case GameEvent.HandStarted(handId)           => s"Main $handId démarrée"
      case GameEvent.StreetAdvanced(next)          => s"Nouvelle street : ${next.toString}"
      case GameEvent.Message(text)                 => text
      case GameEvent.Showdown(results)             => describeShowdown(results)
      case GameEvent.PotUpdated(potState)          => s"Pot total : ${potState.total}"
    }
    logArea.appendText(line + System.lineSeparator())
  }

  private def describeShowdown(results: Vector[ShowdownResult]): String = {
    val parts = results.map { r =>
      val desc = HandDescription.describe(r, boardCards)
      val gain = if (r.share > 0) s"remporte ${r.share}" else "ne remporte rien"
      s"${r.playerId} $gain — $desc"
    }
    s"Showdown : ${parts.mkString(", ")}"
  }

  private def ensureSeatCount(required: Int): Unit = {
    if (seatNodes.size < required) {
      val missing = required - seatNodes.size
      (0 until missing).foreach { _ =>
        val dealer = new Label("BTN")
        dealer.getStyleClass.add("dealer-button")
        dealer.setVisible(false)
        dealer.setManaged(false)
        dealer.setMouseTransparent(true)

        val name = new Label()
        name.getStyleClass.add("seat-name")

        val nameRow = new HBox(6, dealer, name)
        nameRow.setAlignment(Pos.CENTER)
        nameRow.getStyleClass.add("seat-header")

        val stack = new Label()
        stack.getStyleClass.add("seat-stack")
        val cards = new HBox(6)
        cards.setAlignment(Pos.CENTER)
        cards.getStyleClass.add("seat-cards")

        val status = new Label()
        status.getStyleClass.add("seat-status")

        val container = new VBox(4, nameRow, stack, cards, status)
        container.getStyleClass.add("seat-node")
        container.setManaged(false)
        seatLayer.getChildren.add(container)

        val chips = new HBox(2)
        chips.getStyleClass.add("seat-chips")
        chips.setManaged(false)
        chipsLayer.getChildren.add(chips)

        seatNodes += SeatNode(container, dealer, name, stack, cards, status, chips)
      }
    }
  }

  private def shouldReveal(player: Player): Boolean = {
    if (player.isHuman) true
    else if (revealedPlayerIds.contains(player.id)) true
    else if (player.status == PlayerStatus.AllIn) everyoneInHandAllIn
    else false
  }

  private def everyoneInHandAllIn: Boolean = {
    val inHand = playersSnapshot.filter(_.inHand)
    inHand.nonEmpty && inHand.forall(_.status == PlayerStatus.AllIn)
  }

  private def renderSeatChips(box: HBox, amount: Int): Unit =
    renderChips(box, amount, large = false)

  private def renderPotChips(amount: Int): Unit =
    renderChips(potChipsBox, amount, large = true)

  private def renderChips(container: HBox, amount: Int, large: Boolean): Unit = {
    container.getChildren.clear()
    if (amount <= 0) {
      container.setVisible(false)
      container.setManaged(false)
    } else {
      val chips = computeChips(amount)
      chips.foreach { value =>
        container.getChildren.add(createChipNode(value, large))
      }
      container.setVisible(true)
      container.setManaged(true)
    }
  }

  private def computeChips(amount: Int): Seq[Int] = {
    var remaining = amount
    val acc = scala.collection.mutable.ArrayBuffer.empty[Int]
    chipDenominations.foreach { denom =>
      val count = remaining / denom
      if (count > 0) {
        acc ++= Seq.fill(count)(denom)
        remaining -= count * denom
      }
    }
    if (acc.isEmpty && amount > 0) Seq(1) else acc.toSeq
  }

  private def createChipNode(value: Int, large: Boolean): StackPane = {
    val radius = if (large) 13.0 else 9.0
    val baseColor = chipColor(value)
    val circle = new javafx.scene.shape.Circle(radius)
    circle.setFill(chipGradient(baseColor))
    circle.setStroke(darken(baseColor, 0.55))
    circle.setStrokeWidth(if (large) 2.0 else 1.3)

    val shine = new javafx.scene.shape.Circle(radius * 0.58)
    shine.setFill(new RadialGradient(0, 0, 0.35, 0.3, 1.0, true, CycleMethod.NO_CYCLE,
      new Stop(0.0, Color.rgb(255, 255, 255, 0.75)),
      new Stop(0.7, Color.rgb(255, 255, 255, 0.0))))
    shine.setTranslateX(-radius * 0.25)
    shine.setTranslateY(-radius * 0.3)
    shine.setMouseTransparent(true)

    val label = new Label(chipLabel(value))
    label.getStyleClass.add("chip-label")
    if (large) label.getStyleClass.add("chip-label-large")
    label.setMouseTransparent(true)
    val textColor = if (baseColor.getBrightness < 0.55) Color.web("#ffffff") else Color.web("#111217")
    label.setTextFill(textColor)

    val chip = new StackPane(circle, shine, label)
    chip.getStyleClass.add("chip")
    if (large) chip.getStyleClass.add("chip-large") else chip.getStyleClass.add("chip-small")
    chip
  }

  private def chipGradient(base: Color): RadialGradient = {
    new RadialGradient(0, 0, 0.38, 0.35, 1.0, true, CycleMethod.NO_CYCLE,
      new Stop(0.0, lighten(base, 0.45)),
      new Stop(0.55, base),
      new Stop(1.0, darken(base, 0.4)))
  }

  private def lighten(color: Color, factor: Double): Color =
    color.interpolate(Color.WHITE, clamp01(factor))

  private def darken(color: Color, factor: Double): Color =
    color.interpolate(Color.BLACK, clamp01(factor))

  private def clamp01(value: Double): Double =
    math.max(0.0, math.min(1.0, value))

  private def chipColor(value: Int): Color = value match {
    case v if v >= 100 => Color.web("#0000ffff")
    case v if v >= 25  => Color.web("#00ff59ff")
    case v if v >= 5   => Color.web("#ff0000ff")
    case _             => Color.web("#ffffffff")
  }

  private def chipLabel(value: Int): String = value match {
    case v if v >= 1000 => s"${v / 1000}K"
    case v => v.toString
  }

  private def renderSeatCards(box: HBox, player: Player): Unit = {
    box.getChildren.clear()
    val reveal = shouldReveal(player)
    val cardStrings = if (reveal) player.holeCards.map(_.toString) else Vector.empty[String]
    val expected = 2

    (0 until expected).foreach { idx =>
      val node =
        if (reveal) {
          if (idx < cardStrings.size) createSeatCard(cardStrings(idx))
          else placeholderCard()
        } else if (player.holeCards.nonEmpty) hiddenCard()
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

  private def hiddenCard(): Label = {
    val label = new Label(hiddenCardText)
    label.getStyleClass.add("seat-card")
    label.getStyleClass.add("seat-card-hidden")
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

    val radiusX = math.max(200.0, width * 0.38)
    val radiusY = math.max(160.0, height * 0.28)
    tableEllipse.setRadiusX(radiusX)
    tableEllipse.setRadiusY(radiusY)

    val centerX = width / 2
    val centerY = height / 2
    val totalSeats = playersSnapshot.size
    val margin = 36.0

    val visibleSeats = seatNodes.zipWithIndex.flatMap { case (seat, idx) =>
      if (idx < totalSeats && seat.container.isVisible) {
        seat.container.applyCss()
        seat.container.autosize()
        Some((seat, idx, seat.container.getWidth, seat.container.getHeight))
      } else None
    }

    val maxSeatWidth = if (visibleSeats.nonEmpty) visibleSeats.map(_._3).max else 0.0
    val maxSeatHeight = if (visibleSeats.nonEmpty) visibleSeats.map(_._4).max else 0.0
    val limitRadiusX = math.max(0.0, centerX - margin - maxSeatWidth / 2)
    val limitRadiusY = math.max(0.0, centerY - margin - maxSeatHeight / 2)
    val seatRadiusX = math.max(radiusX + 120.0, math.min(limitRadiusX, radiusX + 160.0))
    val seatRadiusY = math.max(radiusY + 90.0, math.min(limitRadiusY, radiusY + 140.0))

    def clamp(value: Double, min: Double, max: Double): Double =
      if (max <= min) min else math.max(min, math.min(max, value))

    visibleSeats.foreach { case (seat, idx, nodeWidth, nodeHeight) =>
      val angle = (math.Pi * 1.5) + (idx.toDouble / totalSeats) * math.Pi * 2
      val seatX = centerX + seatRadiusX * math.cos(angle) - nodeWidth / 2
      val seatY = centerY + seatRadiusY * math.sin(angle) - nodeHeight / 2
      val clampedSeatX = clamp(seatX, margin, width - nodeWidth - margin)
      val clampedSeatY = clamp(seatY, margin, height - nodeHeight - margin)
      seat.container.relocate(clampedSeatX, clampedSeatY)

      seat.chipsBox.applyCss()
      seat.chipsBox.autosize()
      val chipWidth = math.max(seat.chipsBox.getWidth, 40.0)
      val chipHeight = math.max(seat.chipsBox.getHeight, 20.0)
      val seatCenterX = clampedSeatX + nodeWidth / 2
      val seatCenterY = clampedSeatY + nodeHeight / 2
      val dx = seatCenterX - centerX
      val dy = seatCenterY - centerY
      val distance = math.hypot(dx, dy)
      val targetDistance = math.max(0.0, distance - 120.0)
      val ratio = if (distance == 0) 0.0 else targetDistance / distance
      val chipCenterX = centerX + dx * ratio
      val chipCenterY = centerY + dy * ratio
      val chipX = clamp(chipCenterX - chipWidth / 2, margin, width - chipWidth - margin)
      val chipY = clamp(chipCenterY - chipHeight / 2, margin, height - chipHeight - margin)
      seat.chipsBox.relocate(chipX, chipY)
    }

    potChipsBox.applyCss()
    potChipsBox.autosize()
    val potWidth = potChipsBox.getWidth
    val potHeight = potChipsBox.getHeight
    potChipsBox.relocate(centerX - potWidth / 2, centerY + 20 - potHeight / 2)
  }

  private def formatName(player: Player, isButton: Boolean): String = {
    val badge = if (isButton) " (BTN)" else ""
    s"${player.name}$badge"
  }
}

