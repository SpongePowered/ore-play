package ore

import play.api.cache.SyncCacheApi

/**
  * Represents something that can be added to the Cache.
  */
trait Cacheable {

  def cacheApi: SyncCacheApi

  /**
    * Returns the key to this in the Cache.
    *
    * @return Key
    */
  def key: String

  /**
    * Caches this.
    */
  def cache(): Unit = this.cacheApi.set(this.key, this)

  /**
    * Removes this from the Cache.
    */
  def free(): Unit = this.cacheApi.remove(this.key)

}
