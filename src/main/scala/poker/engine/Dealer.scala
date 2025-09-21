package poker.engine

import poker.model._

final class Dealer(initialState: GameState) {
  private var state: GameState = initialState

  def gameState: GameState = state

  def nextToAct: Option[Int] = state.bettingRound.flatMap(_.nextToAct(state.players))

  def startHand(): Vector[GameEvent] = {
    val handNumber = state.handId
    val resetAll = resetPlayers(state.players)
    val aliveIndices = resetAll.indices.filter(idx => resetAll(idx).stack > 0)
    val size = resetAll.size

    if (aliveIndices.size < 2) {
      val safeButton = if (size == 0) 0 else math.min(state.buttonIndex, size - 1)
      state = state.copy(
        handId = handNumber,
        players = resetAll,
        buttonIndex = safeButton,
        bettingRound = None,
        street = Street.Showdown,
        board = Vector.empty,
        potManager = PotManager()
      )
      Vector(GameEvent.Message("La partie est terminee : plus assez de joueurs."))
    } else {
      val safeButton = if (size == 0) 0 else math.min(state.buttonIndex, size - 1)
      val buttonIdx = nextButtonIndex(resetAll, safeButton)

      var players = resetAll
      var pot = PotManager()
      var deck = Deck(state.config.rngSeed.map(seed => seed + handNumber - 1))

      if (state.config.ante > 0) {
        players = players.map { player =>
          if (player.status == PlayerStatus.Active) {
            val ante = math.min(state.config.ante, player.stack)
            if (ante > 0) {
              val after = player.withBet(player.bet + ante)
              val delta = after.totalInvested - player.totalInvested
              pot = pot.addContribution(after.id, delta)
              after
            } else player
          } else player
        }
      }

      val sbIdx = Rules.smallBlindIndex(buttonIdx, players)
      val bbIdx = Rules.bigBlindIndex(buttonIdx, players)

      val (afterSBPlayers, potAfterSB) = postBlind(players, pot, sbIdx, state.config.smallBlind)
      val (afterBBPlayers, potAfterBB) = postBlind(afterSBPlayers, potAfterSB, bbIdx, state.config.bigBlind)

      players = afterBBPlayers
      pot = potAfterBB

      var workingDeck = deck
      val order = dealOrder(players, buttonIdx)
      for (_ <- 0 until 2) {
        order.foreach { idx =>
          val player = players(idx)
          if (player.status != PlayerStatus.Eliminated) {
            val (card, rest) = workingDeck.draw()
            workingDeck = rest
            players = players.updated(idx, player.addCard(card))
          }
        }
      }

      val currentBet = players.map(_.bet).maxOption.getOrElse(0)
      val firstToActIdx = Rules.firstToAct(Street.PreFlop, buttonIdx, players)
      val bettingRound = BettingRound.start(players, players(firstToActIdx).id, currentBet, state.config.bigBlind)

      state = state.copy(
        handId = handNumber + 1,
        players = players,
        buttonIndex = buttonIdx,
        deck = workingDeck,
        board = Vector.empty,
        street = Street.PreFlop,
        potManager = pot,
        bettingRound = Some(bettingRound)
      )

      val potEvent = GameEvent.PotUpdated(pot.buildPotState(activePlayerIds(players)))
      val baseEvents = Vector(GameEvent.HandStarted(handNumber), GameEvent.StreetAdvanced(Street.PreFlop), potEvent)
      autoAdvanceIfNeeded(baseEvents)
    }
  }
  def act(playerId: Int, action: Action): Vector[GameEvent] = {
    state.bettingRound match {
      case None => Vector(GameEvent.Message("No active hand"))
      case Some(round) =>
        round.nextToAct(state.players) match {
          case None => Vector(GameEvent.Message("No player to act"))
          case Some(expectedPlayerId) if expectedPlayerId != playerId =>
            Vector(GameEvent.Message("It is not this player's turn"))
          case Some(_) =>
            val playerIdx = state.players.indexWhere(_.id == playerId)
            if (playerIdx < 0) Vector(GameEvent.Message("Unknown player"))
            else proceedAction(playerIdx, playerId, round, action)
        }
    }
  }

  private def proceedAction(playerIdx: Int, playerId: Int, round: BettingRound, action: Action): Vector[GameEvent] = {
    val player = state.players(playerIdx)
    if (requiresRaisePrivilege(round, player, action) && !round.canPlayerRaise(playerId)) {
      return Vector(GameEvent.Message("Illegal raise"))
    }
    val (updatedPlayer, contribution) = applyAction(player, action, round.currentBet, round.minRaise)
    var players = state.players.updated(playerIdx, updatedPlayer)
    val pot = if (contribution > 0) state.potManager.addContribution(playerId, contribution) else state.potManager
    val updatedRound = round.onAction(player, action, updatedPlayer, players)

    players = players.map(p => if (p.stack <= 0 && p.status == PlayerStatus.Active) p.withStatus(PlayerStatus.AllIn) else p)

    var events = Vector[GameEvent](GameEvent.PlayerActed(playerId, action))

    val activeCount = players.count(_.inHand)
    val baseEvents =
      if (activeCount <= 1) {
        val winner = players.find(_.inHand)
        val winnings = pot.total
        val playersAfter = winner match {
          case Some(w) => updateStack(players, w.id, winnings)
          case None    => players
        }
        val result = winner.map(w => ShowdownResult(w.id, HandRank.lowest, winnings, w.holeCards)).toVector
        state = state.copy(players = playersAfter, potManager = PotManager(), bettingRound = None, street = Street.Showdown)
        events :+= GameEvent.Showdown(result)
        events :+= GameEvent.PotUpdated(PotState(0, Map.empty, Vector.empty))
        events
      } else if (updatedRound.isComplete(players)) {
        val streetEvents = advanceStreet(players, pot)
        events ++ streetEvents
      } else {
        state = state.copy(players = players, potManager = pot, bettingRound = Some(updatedRound))
        events :+ GameEvent.PotUpdated(pot.buildPotState(activePlayerIds(players)))
      }

    autoAdvanceIfNeeded(baseEvents)
  }

  private def requiresRaisePrivilege(round: BettingRound, player: Player, action: Action): Boolean = {
    action match {
      case Action.Bet(_)   => true
      case Action.Raise(_) => true
      case Action.AllIn    => player.bet + player.stack > round.currentBet
      case _               => false
    }
  }

  private def applyAction(player: Player, action: Action, currentBet: Int, minRaise: Int): (Player, Int) = {
    action match {
      case Action.Fold =>
        player.withStatus(PlayerStatus.Folded) -> 0
      case Action.Check =>
        require(player.bet == currentBet, "Cannot check facing a bet")
        player -> 0
      case Action.Call =>
        val target = math.max(currentBet, player.bet)
        val next = player.withBet(target)
        next -> (next.totalInvested - player.totalInvested)
      case Action.Bet(amount) =>
        require(currentBet == 0, "Cannot bet when a bet exists; use raise")
        require(amount >= minRaise, s"Bet must be at least $minRaise")
        val next = player.withBet(amount)
        next -> (next.totalInvested - player.totalInvested)
      case Action.Raise(amount) =>
        val bump = amount - currentBet
        require(bump >= minRaise, s"Raise must be at least $minRaise")
        val next = player.withBet(amount)
        next -> (next.totalInvested - player.totalInvested)
      case Action.AllIn =>
        val next = player.withBet(player.bet + player.stack)
        next -> (next.totalInvested - player.totalInvested)
    }
  }
  private def advanceStreet(players: Vector[Player], pot: PotManager): Vector[GameEvent] = {
    val nextStreet = state.street match {
      case Street.PreFlop  => Street.Flop
      case Street.Flop     => Street.Turn
      case Street.Turn     => Street.River
      case Street.River    => Street.Showdown
      case Street.Showdown => Street.Showdown
    }

    nextStreet match {
      case Street.Showdown =>
        val results = resolveShowdown(players, pot)
        val playersAfter = applyShowdownResults(players, results)
        state = state.copy(players = playersAfter, potManager = PotManager(), bettingRound = None, street = Street.Showdown)
        Vector(GameEvent.StreetAdvanced(nextStreet), GameEvent.Showdown(results))
      case _ =>
        val (nextDeck, nextBoard) = dealBoard(nextStreet, state.deck, state.board)
        val resetPlayers = players.map(_.resetForStreet)
        val firstIdx = Rules.firstToAct(nextStreet, state.buttonIndex, resetPlayers)
        val nextRound = BettingRound.start(resetPlayers, resetPlayers(firstIdx).id, currentBet = 0, minRaise = state.config.bigBlind)
        state = state.copy(players = resetPlayers, deck = nextDeck, board = nextBoard, street = nextStreet, bettingRound = Some(nextRound), potManager = pot)
        Vector(
          GameEvent.StreetAdvanced(nextStreet),
          GameEvent.PotUpdated(pot.buildPotState(activePlayerIds(resetPlayers)))
        )
    }
  }

  private def autoAdvanceIfNeeded(events: Vector[GameEvent]): Vector[GameEvent] = {
    var acc = events
    var continue = true
    while (continue) {
      state.bettingRound match {
        case Some(round) if round.pending.isEmpty =>
          val nextEvents = advanceStreet(state.players, state.potManager)
          acc = acc ++ nextEvents
        case _ =>
          continue = false
      }
    }
    acc
  }

  private def resolveShowdown(players: Vector[Player], pot: PotManager): Vector[ShowdownResult] = {
    val contenders = players.filter(_.inHand)
    val ranks = contenders.map { p =>
      val rank = HandEvaluator.evaluate7(p.holeCards ++ state.board)
      p.id -> rank
    }.toMap
    pot.resolveShowdown(players, ranks)
  }

  private def applyShowdownResults(players: Vector[Player], results: Vector[ShowdownResult]): Vector[Player] = {
    players.map { p =>
      val share = results.find(_.playerId == p.id).map(_.share).getOrElse(0)
      p.copy(stack = p.stack + share)
    }
  }

  private def dealBoard(nextStreet: Street, deck: Deck, board: Vector[Card]): (Deck, Vector[Card]) = {
    nextStreet match {
      case Street.Flop =>
        val deckAfterBurn = deck.burn()
        val (cards, rest) = deckAfterBurn.draw(3)
        rest -> (board ++ cards)
      case Street.Turn =>
        val deckAfterBurn = deck.burn()
        val (card, rest) = deckAfterBurn.draw()
        rest -> (board :+ card)
      case Street.River =>
        val deckAfterBurn = deck.burn()
        val (card, rest) = deckAfterBurn.draw()
        rest -> (board :+ card)
      case _ => deck -> board
    }
  }

  private def postBlind(players: Vector[Player], pot: PotManager, index: Int, amount: Int): (Vector[Player], PotManager) = {
    val player = players(index)
    if (player.status != PlayerStatus.Active) players -> pot
    else {
      val target = player.bet + math.min(amount, player.stack)
      val updated = player.withBet(target)
      val delta = updated.totalInvested - player.totalInvested
      players.updated(index, updated) -> pot.addContribution(player.id, delta)
    }
  }

  private def updateStack(players: Vector[Player], playerId: Int, amount: Int): Vector[Player] = {
    players.map { p => if (p.id == playerId) p.copy(stack = p.stack + amount) else p }
  }

  private def nextButtonIndex(players: Vector[Player], currentButton: Int): Int = {
    val alive = players.indices.filter(idx => players(idx).stack > 0)
    if (alive.isEmpty) currentButton
    else alive.sorted.find(_ > currentButton).getOrElse(alive.sorted.head)
  }

  private def resetPlayers(players: Vector[Player]): Vector[Player] = {
    players.map { p =>
      if (p.stack > 0) p.copy(bet = 0, totalInvested = 0, holeCards = Vector.empty, status = PlayerStatus.Active)
      else p.copy(bet = 0, totalInvested = 0, holeCards = Vector.empty, status = PlayerStatus.Eliminated)
    }
  }

  private def dealOrder(players: Vector[Player], buttonIndex: Int): Vector[Int] = {
    val size = players.size
    (1 to size).map { offset =>
      (buttonIndex + offset) % size
    }.filter { idx =>
      players(idx).status match {
        case PlayerStatus.Active | PlayerStatus.AllIn => true
        case _                                        => false
      }
    }.toVector
  }

  private def activePlayerIds(players: Vector[Player]): Set[Int] = players.filter(_.inHand).map(_.id).toSet
}

