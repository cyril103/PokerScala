package poker.engine

import scala.util.Random

final case class Deck(cards: Vector[Card], rng: Random) {
  def draw(): (Card, Deck) = cards match {
    case head +: tail => head -> copy(cards = tail)
    case _            => throw new IllegalStateException("Deck is empty")
  }

  def draw(n: Int): (Vector[Card], Deck) =
    if (n <= 0) Vector.empty -> this
    else {
      val (picked, rest) = cards.splitAt(n)
      if (picked.length != n) throw new IllegalStateException("Not enough cards to draw")
      picked -> copy(cards = rest)
    }

  def burn(): Deck = copy(cards = cards.drop(1))

  def reshuffle(): Deck = copy(cards = Deck.fullDeckVector(rng))
}

object Deck {
  val allCards: Vector[Card] = {
    for {
      suit <- Suit.values.toVector
      rank <- Rank.values.toVector
    } yield Card(rank, suit)
  }

  def apply(seed: Option[Long]): Deck = {
    val random = seed.map(new Random(_)).getOrElse(new Random())
    Deck(fullDeckVector(random), random)
  }

  private[poker] def fullDeckVector(rng: Random): Vector[Card] = {
    rng.shuffle(allCards)
  }
}
