package ore.permission.scope

/**
  * Represents anything that is or is in a [[Scope]].
  */
trait ScopeSubject {

  /** Returns the [[Scope]] of this subject. */
  def scope: Scope
}
