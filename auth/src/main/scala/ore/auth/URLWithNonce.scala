package ore.auth

/**
  * Represents an auth url that contains a nonce.
  * @param url The auth url
  * @param nonce The nonce that was created together with the URL
  */
case class URLWithNonce(url: String, nonce: String)
