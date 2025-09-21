package poker.ui

import javafx.animation.PauseTransition
import javafx.application.{Application, Platform}
import javafx.scene.Scene
import javafx.scene.control.{Alert, ButtonType}
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import javafx.util.Duration
import poker.ai.{AIProfile, Bot}
import poker.engine.{Dealer, GameState}
import poker.model.{Action, Config, GameEvent, Player, PlayerStatus, Street}

final class MainApp extends Application {
  private var config: Config = Config()
  private var dealer: Dealer = _
  private var bots: Map[Int, Bot] = Map.empty
  private var humanId: Int = 1
  private var botTimer: Option[PauseTransition] = None
  private var primaryStage: Stage = _
  private var humanEliminatedNotified: Boolean = false

  private val tableView = new TableView
  private val controlsView = new ControlsView

  override def start(stage: Stage): Unit = {
    primaryStage = stage
    humanEliminatedNotified = false

    val settingsDialog = new SettingsDialog(config)
    settingsDialog.showAndWait().ifPresent(cfg => config = cfg)

    val players = createPlayers(config)
    humanId = players.find(_.isHuman).map(_.id).getOrElse(1)
    dealer = new Dealer(GameState.initial(config, players))
    syncBotsWithState()

    val root = new BorderPane()
    root.setCenter(tableView)
    root.setRight(controlsView)

    val scene = new Scene(root, 1024, 640)
    Assets(scene)

    stage.setTitle("ScalaHoldemFX")
    stage.setScene(scene)
    stage.show()

    wireControls()

    val events = dealer.startHand()
    syncBotsWithState()
    handleEvents(events, () => {
      updateUI()
      if (dealer.gameState.bettingRound.isDefined) runBots()
    })
  }

  private def wireControls(): Unit = {
    controlsView.onFold(() => handleHumanAction(Action.Fold))
    controlsView.onCheckOrCall(() => {
      for {
        round <- dealer.gameState.bettingRound
        player <- dealer.gameState.playerById(humanId)
      } {
        val callAmount = math.max(0, round.currentBet - player.bet)
        val action = if (callAmount == 0) Action.Check else Action.Call
        handleHumanAction(action)
      }
    })
    controlsView.onBetOrRaise(amount => {
      for (round <- dealer.gameState.bettingRound) {
        val action = if (round.currentBet == 0) Action.Bet(amount) else Action.Raise(amount)
        handleHumanAction(action)
      }
    })
  }

  private def handleHumanAction(action: Action): Unit = {
    stopBotTimer()
    val events = dealer.act(humanId, action)
    handleEvents(events, () => {
      updateUI()
      if (dealer.gameState.bettingRound.isDefined) runBots()
    })
  }

  private def boardSnapshotForStreet(street: Street): Vector[String] = {
    val board = dealer.gameState.board.map(_.toString)
    val count = street match {
      case Street.PreFlop  => 0
      case Street.Flop     => math.min(3, board.length)
      case Street.Turn     => math.min(4, board.length)
      case Street.River    => math.min(5, board.length)
      case Street.Showdown => board.length
    }
    board.take(count)
  }

  private def shouldPauseAfterStreet(street: Street): Boolean = {
    if (config.allInStreetPauseMs <= 0) false
    else {
      val isTurnOrRiver = street == Street.Turn || street == Street.River
      val players = dealer.gameState.players.filter(_.inHand)
      isTurnOrRiver && players.nonEmpty && players.forall(_.status == PlayerStatus.AllIn)
    }
  }

  private def handleEvents(events: Vector[GameEvent], onComplete: () => Unit = () => ()): Unit = {
    def process(queue: List[GameEvent]): Unit = queue match {
      case Nil =>
        onComplete()
      case event :: tail =>
        tableView.appendLog(event)
        val boardOverride = event match {
          case GameEvent.HandStarted(_) =>
            syncBotsWithState()
            None
          case GameEvent.StreetAdvanced(street) =>
            tableView.updateStreet(street)
            Some(boardSnapshotForStreet(street))
          case GameEvent.PotUpdated(pot) =>
            tableView.updatePot(pot)
            None
          case GameEvent.Showdown(_) =>
            scheduleNextHand(Some(config.showdownPauseMs))
            Some(boardSnapshotForStreet(Street.Showdown))
          case _ => None
        }

        updateUI()
        boardOverride.foreach(cards => tableView.updateBoard(cards))

        val delayMs = event match {
          case GameEvent.StreetAdvanced(street) if shouldPauseAfterStreet(street) => config.allInStreetPauseMs
          case _                                                                 => 0
        }

        if (delayMs > 0) {
          val pause = new PauseTransition(Duration.millis(delayMs.toDouble))
          pause.setOnFinished(_ => process(tail))
          pause.play()
        } else {
          process(tail)
        }
    }

    process(events.toList)
  }

  private def updateUI(): Unit = {
    val state = dealer.gameState
    tableView.updatePlayers(state.players, state.buttonIndex, dealer.nextToAct)
    tableView.updateBoard(state.board.map(_.toString))
    tableView.updateStreet(state.street)
    val potState = state.potManager.buildPotState(state.activePlayers.map(_.id).toSet)
    tableView.updatePot(potState)

    maybeHandleHumanElimination(state)

    val isHumanTurn = dealer.nextToAct.contains(humanId) && state.bettingRound.isDefined
    if (isHumanTurn) {
      for {
        round <- state.bettingRound
        player <- state.playerById(humanId)
      } {
        controlsView.setEnabled(true)
        val callAmount = math.max(0, round.currentBet - player.bet)
        val maxBet = player.bet + player.stack
        controlsView.updateControls(
          callAmount = callAmount,
          minRaise = round.minRaise,
          currentBet = round.currentBet,
          maxBet = maxBet,
          canCheck = round.currentBet == player.bet,
          hasChips = player.stack > 0
        )
      }
    } else {
      controlsView.setEnabled(false)
    }
  }

  private def maybeHandleHumanElimination(state: GameState): Unit = {
    state.playerById(humanId).foreach { player =>
      val busted = player.stack <= 0 && !player.inHand
      if (busted && !humanEliminatedNotified) {
        humanEliminatedNotified = true
        triggerHumanGameOver()
      }
    }
  }

  private def triggerHumanGameOver(): Unit = {
    stopBotTimer()
    if (primaryStage == null) {
      Platform.exit()
    } else {
      Platform.runLater(() => {
        val alert = new Alert(Alert.AlertType.INFORMATION)
        alert.initOwner(primaryStage)
        alert.setTitle("Game Over")
        alert.setHeaderText("Game Over")
        alert.setContentText("Vous êtes éliminé. La partie est terminée.")
        alert.getButtonTypes.setAll(ButtonType.OK)
        alert.showAndWait()
        Platform.exit()
      })
    }
  }

  private def botDelayMs: Double = math.max(config.minBotDelayMs.toDouble, config.decisionTimeMs.toDouble * 5)

  private def stopBotTimer(): Unit = {
    botTimer.foreach(_.stop())
    botTimer = None
  }

  private def runBots(): Unit = {
    stopBotTimer()
    continueBots()
  }

  private def continueBots(delayMs: Double = botDelayMs): Unit = {
    val state = dealer.gameState
    val nextOpt = dealer.nextToAct
    if (state.bettingRound.isEmpty || nextOpt.isEmpty) {
      controlsView.setEnabled(false)
      stopBotTimer()
      return
    }

    val playerId = nextOpt.get
    if (playerId == humanId) {
      controlsView.setEnabled(true)
      stopBotTimer()
    } else {
      controlsView.setEnabled(false)
      val pause = new PauseTransition(Duration.millis(delayMs))
      botTimer = Some(pause)
      pause.setOnFinished(_ => {
        val currentState = dealer.gameState
        val currentNext = dealer.nextToAct
        if (currentNext.contains(playerId) && currentState.bettingRound.isDefined) {
          bots.get(playerId) match {
            case Some(bot) =>
              val action = bot.decide(currentState)
              val events = dealer.act(playerId, action)
              handleEvents(events, () => {
                updateUI()
                continueBots()
              })
            case None =>
              continueBots()
          }
        } else {
          continueBots()
        }
      })
      pause.play()
    }
  }

  private def scheduleNextHand(overrideDelayMs: Option[Int] = None): Unit = {
    stopBotTimer()
    val baseDelay = math.max(500, config.uiAnimationMs).toDouble
    val targetDelay = overrideDelayMs.map(_.toDouble).getOrElse(baseDelay)
    val delay = math.max(baseDelay, targetDelay)
    val pause = new PauseTransition(Duration.millis(delay))
    pause.setOnFinished(_ => {
      val events = dealer.startHand()
      syncBotsWithState()
      handleEvents(events, () => {
        updateUI()
        if (dealer.gameState.bettingRound.isDefined) runBots()
      })
    })
    pause.play()
  }

  private def syncBotsWithState(): Unit = {
    val nonHumanPlayers = dealer.gameState.players.filter(p => !p.isHuman && p.stack > 0)
    val aliveIds = nonHumanPlayers.map(_.id).toSet
    val retained = bots.filter { case (id, _) => aliveIds.contains(id) }
    val missing = nonHumanPlayers.collect {
      case p if !retained.contains(p.id) => p.id -> new Bot(p.id, AIProfile.default, config)
    }
    bots = retained ++ missing
  }

  private def createPlayers(config: Config): Vector[Player] = {
    val total = config.numBots + 1
    (0 until total).toVector.map { seat =>
      val id = seat + 1
      val isHuman = seat == 0
      val name = if (isHuman) "You" else s"Bot $seat"
      Player(
        id = id,
        seat = seat,
        name = name,
        isHuman = isHuman,
        stack = config.startingStack,
        status = if (config.startingStack > 0) PlayerStatus.Active else PlayerStatus.Eliminated
      )
    }
  }
}

object MainApp {
  def main(args: Array[String]): Unit = {
    Application.launch(classOf[MainApp], args: _*)
  }
}

