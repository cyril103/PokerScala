package poker.engine

import poker.model.{Action, Player, PlayerStatus}

final case class BettingRound(
  currentBet: Int,
  minRaise: Int,
  lastAggressor: Option[Int],
  pending: Set[Int],
  actionOrder: Vector[Int],
  pointer: Int
) {
  def isComplete(players: Vector[Player]): Boolean =
    activePlayers(players).size <= 1 || pending.isEmpty

  def nextToAct(players: Vector[Player]): Option[Int] = {
    if (pending.isEmpty) None
    else {
      val order = actionOrder
      if (order.isEmpty) None
      else {
        val total = order.length
        var offset = 0
        var found: Option[Int] = None
        while (offset < total && found.isEmpty) {
          val idx = (pointer + offset) % total
          val playerId = order(idx)
          val player = players.find(_.id == playerId)
          if (player.exists(p => pending.contains(p.id) && p.canAct)) found = Some(playerId)
          offset += 1
        }
        found
      }
    }
  }

  def onAction(player: Player, action: Action, updated: Player, players: Vector[Player]): BettingRound = {
    val playerId = player.id
    val orderIndex = actionOrder.indexOf(playerId)
    val nextPointer = if (orderIndex == -1) pointer else (orderIndex + 1) % math.max(actionOrder.size, 1)
    val activeIds = activePlayers(players).filter(_.canAct).map(_.id).toSet
    val sanitizedPending = pending intersect activeIds

    val afterActionPending = action match {
      case Action.Fold => sanitizedPending - playerId
      case Action.Check => sanitizedPending - playerId
      case Action.Call => sanitizedPending - playerId
      case Action.AllIn =>
        if (updated.bet > currentBet) activeIds - playerId
        else sanitizedPending - playerId
      case Action.Bet(_) => activeIds - playerId
      case Action.Raise(_) => activeIds - playerId
    }

    val (newCurrentBet, newMinRaise, newAggressor) = action match {
      case Action.Bet(amount) =>
        val raiseAmount = amount - currentBet
        (amount, math.max(minRaise, raiseAmount), Some(playerId))
      case Action.Raise(amount) =>
        val raiseAmount = amount - currentBet
        (amount, math.max(minRaise, raiseAmount), Some(playerId))
      case Action.AllIn =>
        val committed = updated.bet
        val effectiveRaise = committed - currentBet
        if (committed > currentBet) (committed, math.max(minRaise, effectiveRaise), Some(playerId))
        else (currentBet, minRaise, lastAggressor)
      case _ => (currentBet, minRaise, lastAggressor)
    }

    copy(
      currentBet = newCurrentBet,
      minRaise = math.max(1, newMinRaise),
      lastAggressor = newAggressor,
      pending = afterActionPending,
      pointer = nextPointer
    )
  }

  private def activePlayers(players: Vector[Player]): Vector[Player] =
    players.filter(p => p.status == PlayerStatus.Active || p.status == PlayerStatus.AllIn)
}

object BettingRound {
  def start(players: Vector[Player], firstToActId: Int, currentBet: Int, minRaise: Int): BettingRound = {
    val order = orderingFrom(players, firstToActId)
    val pending = players.filter(_.canAct).map(_.id).toSet
    val pointer = order.indexOf(firstToActId) match {
      case -1 => 0
      case idx => idx
    }
    BettingRound(currentBet, math.max(1, minRaise), None, pending, order, if (order.isEmpty) 0 else pointer)
  }

  private def orderingFrom(players: Vector[Player], firstToActId: Int): Vector[Int] = {
    val ids = players.map(_.id)
    val startIdx = ids.indexOf(firstToActId)
    if (startIdx < 0) ids.toVector
    else (ids.drop(startIdx) ++ ids.take(startIdx)).toVector
  }
}
