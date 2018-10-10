package ore.permission.scope

/**
  * Represents a "scope" for testing permissions within the application.
  */
trait Scope extends ScopeSubject {

  override val scope: Scope = this

}
