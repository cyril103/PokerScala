package poker.ui

import javafx.geometry.Insets
import javafx.scene.control.{Button, Label, Slider}
import javafx.scene.layout.{HBox, VBox}

final class ControlsView extends VBox {
  private val foldButton = new Button("Fold")
  private val checkCallButton = new Button("Check")
  private val betRaiseButton = new Button("Bet")
  private val allInButton = new Button("All-in")
  private val amountSlider = new Slider()
  private val amountLabel = new Label("Bet: 0")

  private var foldHandler: () => Unit = () => ()
  private var checkHandler: () => Unit = () => ()
  private var betHandler: Int => Unit = _ => ()
  private var allInHandler: () => Unit = () => ()

  amountSlider.setShowTickLabels(true)
  amountSlider.setShowTickMarks(true)
  amountSlider.setMajorTickUnit(1)
  amountSlider.valueProperty().addListener((_, _, newValue) => updateSliderLabel(newValue.doubleValue()))

  foldButton.setOnAction(_ => foldHandler())
  checkCallButton.setOnAction(_ => checkHandler())
  betRaiseButton.setOnAction(_ => betHandler(amountSlider.getValue.toInt))
  allInButton.setOnAction(_ => allInHandler())

  setSpacing(8)
  setPadding(new Insets(10))

  private val buttonRow = new HBox(8, foldButton, checkCallButton, betRaiseButton, allInButton)
  getChildren.addAll(buttonRow, amountLabel, amountSlider)

  def onFold(handler: () => Unit): Unit = foldHandler = handler
  def onCheckOrCall(handler: () => Unit): Unit = checkHandler = handler
  def onBetOrRaise(handler: Int => Unit): Unit = betHandler = handler
  def onAllIn(handler: () => Unit): Unit = allInHandler = handler

  def updateControls(callAmount: Int, minRaise: Int, currentBet: Int, maxBet: Int, canCheck: Boolean, hasChips: Boolean): Unit = {
    val baseMin = if (currentBet == 0) math.max(minRaise, 1) else currentBet + math.max(minRaise, 1)
    val sliderMin = math.min(baseMin, maxBet)
    val sliderMax = math.max(sliderMin, maxBet)
    amountSlider.setMin(sliderMin.toDouble)
    amountSlider.setMax(sliderMax.toDouble)
    amountSlider.setValue(sliderMin.toDouble)
    updateSliderLabel(amountSlider.getValue)

    val canCall = if (callAmount == 0) true else hasChips
    val canBet = hasChips && maxBet > currentBet

    foldButton.setDisable(!hasChips)
    val disableCheck = if (callAmount == 0) !canCheck else !canCall
    checkCallButton.setDisable(disableCheck)
    betRaiseButton.setDisable(!canBet)
    allInButton.setDisable(!hasChips)
    amountSlider.setDisable(!canBet)

    checkCallButton.setText(if (callAmount == 0) "Check" else s"Call $callAmount")
    betRaiseButton.setText(if (currentBet == 0) "Bet" else "Raise")
  }

  def setEnabled(enabled: Boolean): Unit = {
    foldButton.setDisable(!enabled)
    checkCallButton.setDisable(!enabled)
    betRaiseButton.setDisable(!enabled)
    allInButton.setDisable(!enabled)
    amountSlider.setDisable(!enabled)
  }

  private def updateSliderLabel(value: Double): Unit = {
    amountLabel.setText(s"Bet: ${value.toInt}")
  }
}
