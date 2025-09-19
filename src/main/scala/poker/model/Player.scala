package poker.model

import poker.engine.Card

enum PlayerStatus {
  case Active, Folded, AllIn, Eliminated
}

final case class Player(
  id: Int,
  seat: Int,
  name: String,
  isHuman: Boolean,
  stack: Int,
  bet: Int = 0,
  holeCards: Vector[Card] = Vector.empty,
  status: PlayerStatus = PlayerStatus.Active,
  totalInvested: Int = 0
) {
  def active: Boolean = status == PlayerStatus.Active
  def inHand: Boolean = status == PlayerStatus.Active || status == PlayerStatus.AllIn
  def canAct: Boolean = status == PlayerStatus.Active

  def withBet(targetBet: Int): Player = {
    val desiredIncrement = math.max(0, targetBet - bet)
    val actualIncrement = math.min(desiredIncrement, stack)
    val finalBet = bet + actualIncrement
    val finalStack = stack - actualIncrement
    val nextStatus =
      if (finalStack == 0 && status == PlayerStatus.Active) PlayerStatus.AllIn
      else status
    copy(bet = finalBet, stack = finalStack, totalInvested = totalInvested + actualIncrement, status = nextStatus)
  }

  def withStatus(newStatus: PlayerStatus): Player = copy(status = newStatus)
  def withStack(amount: Int): Player = copy(stack = amount)
  def withCards(cards: Vector[Card]): Player = copy(holeCards = cards)
  def addCard(card: Card): Player = copy(holeCards = holeCards :+ card)
  def resetForStreet: Player = copy(bet = 0)
  def eliminateIfBroke: Player = if (stack > 0) this else copy(status = PlayerStatus.Eliminated)
}

