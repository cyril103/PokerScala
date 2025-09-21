package poker.model

import poker.engine.Card

case class PlayerHand(
  name: String,
  holeCards: Vector[Card]
)

case class HandHistory(
  handId: Long,
  board: Vector[Card],
  players: Vector[PlayerHand]
)
