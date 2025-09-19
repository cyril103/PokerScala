package poker.model

sealed trait Action extends Product with Serializable
object Action {
  case object Fold extends Action
  case object Check extends Action
  case object Call extends Action
  final case class Bet(amount: Int) extends Action
  final case class Raise(amount: Int) extends Action
  case object AllIn extends Action
}
