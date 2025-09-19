package poker.ui

import javafx.geometry.Insets
import javafx.scene.control.{ButtonType, Dialog, Label, Spinner, TextField}
import javafx.scene.layout.GridPane
import poker.model.Config

final class SettingsDialog(initial: Config) extends Dialog[Config] {
  setTitle("Game Settings")
  getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

  private val botsSpinner = new Spinner[Integer](1, 8, initial.numBots)
  private val stackField = new TextField(initial.startingStack.toString)
  private val smallBlindField = new TextField(initial.smallBlind.toString)
  private val bigBlindField = new TextField(initial.bigBlind.toString)
  private val anteField = new TextField(initial.ante.toString)
  private val mcField = new TextField(initial.mcIterations.toString)
  private val decisionField = new TextField(initial.decisionTimeMs.toString)
  private val botDelayField = new TextField(initial.minBotDelayMs.toString)
  private val seedField = new TextField(initial.rngSeed.map(_.toString).getOrElse(""))

  private val grid = new GridPane()
  grid.setHgap(10)
  grid.setVgap(10)
  grid.setPadding(new Insets(20, 150, 10, 10))

  private var nextRow = 0

  addRow("Bots", botsSpinner)
  addRow("Stack", stackField)
  addRow("Small Blind", smallBlindField)
  addRow("Big Blind", bigBlindField)
  addRow("Ante", anteField)
  addRow("MC Iterations", mcField)
  addRow("Decision Time ms", decisionField)
  addRow("Min Bot Delay ms", botDelayField)
  addRow("Seed", seedField)

  getDialogPane.setContent(grid)

  setResultConverter(dialogButton =>
    if (dialogButton == ButtonType.OK) readConfig() else null
  )

  private def addRow(label: String, field: javafx.scene.Node): Unit = {
    grid.add(new Label(label), 0, nextRow)
    grid.add(field, 1, nextRow)
    nextRow += 1
  }

  private def readConfig(): Config = {
    Config(
      startingStack = parseInt(stackField.getText, initial.startingStack),
      smallBlind = parseInt(smallBlindField.getText, initial.smallBlind),
      bigBlind = parseInt(bigBlindField.getText, initial.bigBlind),
      ante = parseInt(anteField.getText, initial.ante),
      numBots = botsSpinner.getValue.intValue(),
      mcIterations = parseInt(mcField.getText, initial.mcIterations),
      decisionTimeMs = parseInt(decisionField.getText, initial.decisionTimeMs),
      minBotDelayMs = parseInt(botDelayField.getText, initial.minBotDelayMs),
      rngSeed = parseLong(seedField.getText).orElse(initial.rngSeed),
      uiAnimationMs = initial.uiAnimationMs
    )
  }

  private def parseInt(text: String, defaultValue: Int): Int = {
    try text.trim.toInt
    catch case _: NumberFormatException => defaultValue
  }

  private def parseLong(text: String): Option[Long] = {
    val trimmed = Option(text).map(_.trim).getOrElse("")
    if (trimmed.isEmpty) None
    else try Some(trimmed.toLong) catch case _: NumberFormatException => None
  }
}

