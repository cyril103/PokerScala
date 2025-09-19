package poker.engine

import poker.model.{Player, Street}

object Rules {
  def nextIndex(players: Vector[Player], startIndex: Int): Int = {
    val size = players.size
    Iterator.iterate((startIndex + 1) % size)(i => (i + 1) % size)
      .dropWhile(i => !players(i).inHand)
      .take(1)
      .toList
      .headOption.getOrElse(startIndex)
  }

  def smallBlindIndex(buttonIndex: Int, players: Vector[Player]): Int =
    nextOccupiedIndex(players, buttonIndex)

  def bigBlindIndex(buttonIndex: Int, players: Vector[Player]): Int =
    nextOccupiedIndex(players, smallBlindIndex(buttonIndex, players))

  def firstToAct(street: Street, buttonIndex: Int, players: Vector[Player]): Int = {
    street match {
      case Street.PreFlop =>
        val bbIndex = bigBlindIndex(buttonIndex, players)
        nextOccupiedIndex(players, bbIndex)
      case _ => nextOccupiedIndex(players, buttonIndex)
    }
  }

  private def nextOccupiedIndex(players: Vector[Player], startIndex: Int): Int = {
    val size = players.size
    var idx = (startIndex + 1) % size
    var visited = 0
    while (visited < size && !players(idx).inHand) {
      idx = (idx + 1) % size
      visited += 1
    }
    idx
  }
}
