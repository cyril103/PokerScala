package poker.model

import poker.engine.{Card, HandRank}

sealed trait GameEvent extends Product with Serializable
object GameEvent {
  final case class HandStarted(handId: Long) extends GameEvent
  final case class PlayerActed(playerId: Int, action: Action) extends GameEvent
  final case class StreetAdvanced(street: Street) extends GameEvent
  final case class PotUpdated(pot: PotState) extends GameEvent
  final case class Showdown(results: Vector[ShowdownResult]) extends GameEvent
  final case class Message(text: String) extends GameEvent
}

enum Street {
  case PreFlop, Flop, Turn, River, Showdown
}

final case class PotState(total: Int, contributions: Map[Int, Int], sidePots: Vector[SidePot])
final case class SidePot(amount: Int, eligible: Set[Int])
final case class ShowdownResult(playerId: Int, handRank: HandRank, share: Int, cards: Vector[Card])
