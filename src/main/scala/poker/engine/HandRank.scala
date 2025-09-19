package poker.engine

enum HandCategory(val strength: Int, val label: String) {
  case HighCard     extends HandCategory(1, "High Card")
  case Pair         extends HandCategory(2, "Pair")
  case TwoPairs     extends HandCategory(3, "Two Pair")
  case ThreeOfAKind extends HandCategory(4, "Three of a Kind")
  case Straight     extends HandCategory(5, "Straight")
  case Flush        extends HandCategory(6, "Flush")
  case FullHouse    extends HandCategory(7, "Full House")
  case FourOfAKind  extends HandCategory(8, "Four of a Kind")
  case StraightFlush extends HandCategory(9, "Straight Flush")
}

final case class HandRank(category: HandCategory, ranks: Vector[Int]) extends Ordered[HandRank] {
  override def compare(that: HandRank): Int = {
    val catComp = category.strength.compare(that.category.strength)
    if (catComp != 0) catComp
    else ranks.zipAll(that.ranks, 0, 0).foldLeft(0) { case (acc, (l, r)) =>
      if (acc != 0) acc else l.compare(r)
    }
  }

  override def toString: String = s"${category.label} (${ranks.mkString(",")})"
}

object HandRank {
  val lowest: HandRank = HandRank(HandCategory.HighCard, Vector(2, 3, 4, 5, 7))
}
