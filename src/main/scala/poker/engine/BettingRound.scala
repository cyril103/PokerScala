package poker.engine

import poker.model.{Action, Player, PlayerStatus}

final case class BettingRound(
  currentBet: Int,
  minRaise: Int,
  lastAggressor: Option[Int],
  pending: Set[Int],
  actionOrder: Vector[Int],
  pointer: Int,
  raiseEligible: Set[Int]
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

  def canPlayerRaise(playerId: Int): Boolean = raiseEligible.contains(playerId)

  def onAction(player: Player, action: Action, updated: Player, players: Vector[Player]): BettingRound = {
    val playerId = player.id
    val orderIndex = actionOrder.indexOf(playerId)
    val nextPointer = if (orderIndex == -1) pointer else (orderIndex + 1) % math.max(actionOrder.size, 1)

    val active = activePlayers(players)
    val activeIds = active.map(_.id).toSet
    val canActPlayers = players.filter(_.canAct)
    val canActIds = canActPlayers.map(_.id).toSet

    val sanitizedPending = (pending intersect activeIds) intersect canActIds
    val sanitizedRaiseEligible = (raiseEligible intersect canActIds) - playerId

    val updatedBet = updated.bet
    val increasedBet = updatedBet > currentBet
    val effectiveRaise = updatedBet - currentBet

    val newCurrentBet = action match {
      case Action.Bet(amount)   => amount
      case Action.Raise(amount) => amount
      case Action.AllIn if increasedBet => updatedBet
      case _ => currentBet
    }

    val playersNeedingToAct =
      if (newCurrentBet > currentBet)
        canActPlayers.filter(p => p.id != playerId && p.bet < newCurrentBet).map(_.id).toSet
      else Set.empty[Int]

    val basePending = sanitizedPending - playerId

    val isAllInFullRaise = action match {
      case Action.AllIn if increasedBet && effectiveRaise >= minRaise => true
      case _ => false
    }

    val updatedPending = action match {
      case Action.Bet(_) | Action.Raise(_) => playersNeedingToAct
      case Action.AllIn if isAllInFullRaise => playersNeedingToAct
      case Action.AllIn if increasedBet => basePending ++ playersNeedingToAct
      case _ => basePending
    }

    val updatedMinRaise = action match {
      case Action.Bet(amount)   => math.max(1, amount - currentBet)
      case Action.Raise(amount) => math.max(1, amount - currentBet)
      case Action.AllIn if isAllInFullRaise => math.max(1, effectiveRaise)
      case _ => minRaise
    }

    val updatedAggressor = action match {
      case Action.Bet(_) | Action.Raise(_) => Some(playerId)
      case Action.AllIn if isAllInFullRaise => Some(playerId)
      case _ => lastAggressor
    }

    val updatedRaiseEligible = {
      val base = sanitizedRaiseEligible
      action match {
        case Action.Bet(_) | Action.Raise(_) => (canActIds - playerId)
        case Action.AllIn if isAllInFullRaise => (canActIds - playerId)
        case _ => base
      }
    }

    copy(
      currentBet = newCurrentBet,
      minRaise = math.max(1, updatedMinRaise),
      lastAggressor = updatedAggressor,
      pending = updatedPending,
      pointer = nextPointer,
      raiseEligible = updatedRaiseEligible intersect canActIds
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
    val eligible = pending
    BettingRound(currentBet, math.max(1, minRaise), None, pending, order, if (order.isEmpty) 0 else pointer, eligible)
  }

  private def orderingFrom(players: Vector[Player], firstToActId: Int): Vector[Int] = {
    val ids = players.map(_.id)
    val startIdx = ids.indexOf(firstToActId)
    if (startIdx < 0) ids.toVector
    else (ids.drop(startIdx) ++ ids.take(startIdx)).toVector
  }
}
