package poker.engine

enum Suit(val code: Char, val symbol: Char) {
  case Clubs extends Suit('C', '\u2663')
  case Diamonds extends Suit('D', '\u2666')
  case Hearts extends Suit('H', '\u2665')
  case Spades extends Suit('S', '\u2660')
}

object Suit {
  private val byCode: Map[Char, Suit] = values.map(s => (s.code, s)).toMap
  private val bySymbol: Map[Char, Suit] = values.map(s => (s.symbol, s)).toMap

  def fromChar(ch: Char): Option[Suit] = {
    val normalized = ch.toUpper
    byCode.get(normalized).orElse(bySymbol.get(ch))
  }
}

enum Rank(val value: Int, val label: String) {
  case Two extends Rank(2, "2")
  case Three extends Rank(3, "3")
  case Four extends Rank(4, "4")
  case Five extends Rank(5, "5")
  case Six extends Rank(6, "6")
  case Seven extends Rank(7, "7")
  case Eight extends Rank(8, "8")
  case Nine extends Rank(9, "9")
  case Ten extends Rank(10, "T")
  case Jack extends Rank(11, "J")
  case Queen extends Rank(12, "Q")
  case King extends Rank(13, "K")
  case Ace extends Rank(14, "A")

  def nextLower: Rank = Rank.fromValue(value - 1).getOrElse(Rank.Ace)
}

object Rank {
  val ordered: List[Rank] = values.toList.sortBy(_.value)
  def fromLabel(label: String): Option[Rank] = values.find(_.label.equalsIgnoreCase(label))
  def fromValue(v: Int): Option[Rank] = values.find(_.value == v)
}

final case class Card(rank: Rank, suit: Suit) {
  override def toString: String = s"${rank.label}${suit.symbol}"
}

object Card {
  private val Pattern = s"([2-9TJQKA])([CDHS${Suit.Clubs.symbol}${Suit.Diamonds.symbol}${Suit.Hearts.symbol}${Suit.Spades.symbol}])".r

  def parse(str: String): Option[Card] = str.trim.toUpperCase match {
    case Pattern(rankLabel, suitLabel) =>
      for {
        rank <- Rank.fromLabel(rankLabel)
        suit <- parseSuit(suitLabel.head)
      } yield Card(rank, suit)
    case _ => None
  }

  private def parseSuit(ch: Char): Option[Suit] = Suit.fromChar(ch)
}
